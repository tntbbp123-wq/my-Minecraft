package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.mail.MailManager;
import com.tntbbp.myminecraft.manager.mail.MailSpec;
import com.tntbbp.myminecraft.model.Mail;
import com.tntbbp.myminecraft.model.MailAttachment;
import com.tntbbp.myminecraft.model.MailSenderType;
import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import com.tntbbp.myminecraft.web.BridgeException;
import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.WebBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 우편 API (api-bridge.md §3) — 관리자(웹) 우편 발송·우편함 조회·미수령 우편 회수.
 * 멱등(X-Request-Id)·인증·본문 크기는 통로({@link WebBridge})가 앞에서 처리한다. 우편 데이터는 전부
 * {@link MailManager}를 메인 스레드({@code bridge.callSync})에서 만진다.
 */
public class MailHandler {

    static final int MAX_TITLE = 64;
    static final int MAX_BODY = 512;
    static final int MAX_SENDER_NAME = 32;
    static final int MAX_ATTACHMENTS = 27;
    static final int MIN_EXPIRES_DAYS = 1;
    static final int MAX_EXPIRES_DAYS = 90;
    static final String DEFAULT_SENDER_NAME = "운영자";

    /**
     * 형식 검사를 마친 {@code POST /mail} 요청.
     *
     * @param mode        uuids | all | online
     * @param uuids       mode=uuids일 때 받는 사람(중복 제거 전), 아니면 빈 목록
     * @param expiresDays 보관 일수(없으면 null → 관리자 우편 기본값)
     */
    record SendRequest(String mode, List<UUID> uuids, String title, String body, String senderName,
                       List<AttachmentRequest> attachments, long attachedG, Integer expiresDays) {
    }

    /** 첨부 요청 하나. {@code itemName}(특수 아이템)과 {@code material}(바닐라) 중 하나만 있다. */
    record AttachmentRequest(int index, String itemName, String material, int count) {
    }

    private final WebBridge bridge;

    public MailHandler(WebBridge bridge) {
        this.bridge = bridge;
    }

    /** {@code POST /mail} (쓰기) — 관리자 우편 발송. 상한 제외·기본 보관 30일. */
    public BridgeResponse send(BridgeExchange exchange) throws Exception {
        SendRequest request = parseSendRequest(exchange.json());
        String actor = exchange.actor();
        JsonObject body = bridge.callSync(() -> {
            MyMinecraftPlugin plugin = bridge.plugin();
            List<MailSpec.Item> items = resolveAttachments(plugin, request.attachments());
            List<UUID> recipients = resolveRecipients(request);
            if (recipients.isEmpty()) {
                throw BridgeException.invalidField("recipients: 받을 플레이어가 없습니다.");
            }
            MailSpec spec = new MailSpec(MailSenderType.ADMIN, request.senderName(), request.title(), request.body(),
                    items, request.attachedG(), request.expiresDays() == null ? 0 : request.expiresDays(), actor);
            MailManager.SendResult result = plugin.getMailManager().send(spec, recipients);
            bridge.adminLog().mailSend(actor, request.mode(), result.sent(), result.heldCount(), result.mailIds(),
                    request.title());
            return sendResponse(result);
        });
        return BridgeResponse.ok(body);
    }

    /** {@code GET /players/{uuid}/mail} — 우편함 조회(오프라인 OK). 보류·수령 완료 우편 포함, 최신순. */
    public BridgeResponse list(BridgeExchange exchange) throws Exception {
        UUID uuid = PlayerHandler.parseUuid(exchange.pathParam("uuid"));
        JsonObject body = bridge.callSync(() -> {
            JsonArray mailbox = new JsonArray();
            for (Mail mail : bridge.plugin().getMailManager().list(uuid)) {
                mailbox.add(mailJson(mail));
            }
            JsonObject result = new JsonObject();
            result.add("mailbox", mailbox);
            return result;
        });
        return BridgeResponse.ok(body);
    }

    /** {@code POST /players/{uuid}/mail/{id}/recall} (쓰기) — 미수령 우편 회수. */
    public BridgeResponse recall(BridgeExchange exchange) throws Exception {
        UUID uuid = PlayerHandler.parseUuid(exchange.pathParam("uuid"));
        long mailId = parseMailId(exchange.pathParam("id"));
        String actor = exchange.actor();
        bridge.callSync(() -> {
            switch (bridge.plugin().getMailManager().recall(uuid, mailId)) {
                case NOT_FOUND -> throw BridgeException.notFound("우편을 찾을 수 없습니다: " + mailId);
                case ALREADY_CLAIMED -> throw BridgeException.conflict("이미 수령한 우편이라 회수할 수 없습니다.");
                case RECALLED -> bridge.adminLog().mailRecall(actor, uuid, mailId);
            }
            return null;
        });
        JsonObject body = new JsonObject();
        body.addProperty("ok", true);
        body.addProperty("recalled", true);
        return BridgeResponse.ok(body);
    }

    // ---------------------------------------------------------------- 메인 스레드 전용

    private static List<MailSpec.Item> resolveAttachments(MyMinecraftPlugin plugin, List<AttachmentRequest> requests)
            throws BridgeException {
        List<MailSpec.Item> items = new ArrayList<>();
        for (AttachmentRequest request : requests) {
            String field = "attachments[" + request.index() + "]";
            if (request.itemName() != null) {
                SpecialItemCatalog.Resolved resolved = SpecialItemCatalog.resolve(plugin, request.itemName(), 1);
                if (resolved == null || resolved.item() == null || resolved.item().getType().isAir()) {
                    throw BridgeException.invalidField(field + ".item_name: 알 수 없는 특수 아이템입니다: "
                            + request.itemName());
                }
                items.add(MailSpec.Item.special(request.itemName(), resolved.item(), request.count(),
                        resolved.displayName()));
            } else {
                Material material = Material.matchMaterial(request.material());
                if (material == null || material.isLegacy() || material.isAir() || !material.isItem()) {
                    throw BridgeException.invalidField(field + ".material: 지급할 수 없는 재질입니다: "
                            + request.material());
                }
                items.add(MailSpec.Item.of(new ItemStack(material), request.count()));
            }
        }
        return items;
    }

    private static List<UUID> resolveRecipients(SendRequest request) {
        Set<UUID> recipients = new LinkedHashSet<>();
        switch (request.mode()) {
            case "uuids" -> recipients.addAll(request.uuids());
            case "online" -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    recipients.add(player.getUniqueId());
                }
            }
            case "all" -> {
                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    recipients.add(player.getUniqueId());
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    recipients.add(player.getUniqueId());
                }
            }
            default -> {
                // parseSendRequest에서 이미 걸러짐
            }
        }
        return new ArrayList<>(recipients);
    }

    // ---------------------------------------------------------------- 순수 헬퍼 (단위 테스트 대상)

    /** {@code POST /mail} 본문 형식 검사. 필드 누락·타입 오류는 400, 값 규칙 위반은 422. */
    static SendRequest parseSendRequest(JsonObject json) throws BridgeException {
        JsonElement recipientsElement = json.get("recipients");
        if (recipientsElement == null || recipientsElement.isJsonNull()) {
            throw BridgeException.badRequest("'recipients' 가 필요합니다.");
        }
        if (!recipientsElement.isJsonObject()) {
            throw BridgeException.badRequest("'recipients' 는 객체여야 합니다.");
        }
        JsonObject recipients = recipientsElement.getAsJsonObject();
        String mode = BridgeExchange.reqString(recipients, "mode").strip();
        List<UUID> uuids = switch (mode) {
            case "uuids" -> parseUuids(recipients);
            case "all", "online" -> List.of();
            default -> throw BridgeException.invalidField("recipients.mode 는 uuids, all, online 중 하나여야 합니다.");
        };

        String title = BridgeExchange.reqString(json, "title").strip();
        checkText("title", title, 1, MAX_TITLE, false);

        String body = BridgeExchange.optString(json, "body");
        body = body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n').strip();
        checkText("body", body, 0, MAX_BODY, true);

        String senderName = BridgeExchange.optString(json, "sender_name");
        senderName = senderName == null ? DEFAULT_SENDER_NAME : senderName.strip();
        checkText("sender_name", senderName, 1, MAX_SENDER_NAME, false);

        List<AttachmentRequest> attachments = parseAttachments(json);

        Long attachedG = BridgeExchange.optLong(json, "attached_g");
        if (attachedG != null && attachedG < 0) {
            throw BridgeException.invalidField("attached_g 는 0 이상이어야 합니다.");
        }
        Integer expiresDays = BridgeExchange.optInt(json, "expires_days");
        if (expiresDays != null && (expiresDays < MIN_EXPIRES_DAYS || expiresDays > MAX_EXPIRES_DAYS)) {
            throw BridgeException.invalidField("expires_days 는 " + MIN_EXPIRES_DAYS + "~" + MAX_EXPIRES_DAYS
                    + " 사이여야 합니다.");
        }
        return new SendRequest(mode, uuids, title, body, senderName, attachments,
                attachedG == null ? 0L : attachedG, expiresDays);
    }

    private static List<UUID> parseUuids(JsonObject recipients) throws BridgeException {
        JsonElement element = recipients.get("uuids");
        if (element == null || element.isJsonNull()) {
            throw BridgeException.badRequest("recipients.mode 가 uuids 이면 'uuids' 가 필요합니다.");
        }
        if (!element.isJsonArray()) {
            throw BridgeException.badRequest("'uuids' 는 배열이어야 합니다.");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            throw BridgeException.invalidField("recipients.uuids 가 비어 있습니다.");
        }
        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw BridgeException.badRequest("recipients.uuids[" + i + "] 는 문자열이어야 합니다.");
            }
            try {
                uuids.add(UUID.fromString(item.getAsString().strip()));
            } catch (IllegalArgumentException e) {
                throw BridgeException.invalidField("recipients.uuids[" + i + "] UUID 형식이 올바르지 않습니다: "
                        + item.getAsString());
            }
        }
        return uuids;
    }

    private static List<AttachmentRequest> parseAttachments(JsonObject json) throws BridgeException {
        JsonElement element = json.get("attachments");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw BridgeException.badRequest("'attachments' 는 배열이어야 합니다.");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > MAX_ATTACHMENTS) {
            throw BridgeException.invalidField("attachments 는 최대 " + MAX_ATTACHMENTS + "개입니다.");
        }
        List<AttachmentRequest> attachments = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String field = "attachments[" + i + "]";
            JsonElement item = array.get(i);
            if (!item.isJsonObject()) {
                throw BridgeException.badRequest(field + " 는 객체여야 합니다.");
            }
            JsonObject object = item.getAsJsonObject();
            String itemName = BridgeExchange.optString(object, "item_name");
            String material = BridgeExchange.optString(object, "material");
            if ((itemName == null) == (material == null)) {
                throw BridgeException.invalidField(field + ": item_name 과 material 중 하나만 지정해야 합니다.");
            }
            if (itemName != null) {
                itemName = itemName.strip();
                if (itemName.isEmpty()) {
                    throw BridgeException.invalidField(field + ".item_name 이 비어 있습니다.");
                }
            } else {
                material = material.strip();
                if (material.isEmpty()) {
                    throw BridgeException.invalidField(field + ".material 이 비어 있습니다.");
                }
            }
            Integer count = BridgeExchange.optInt(object, "count");
            if (count == null) {
                throw BridgeException.badRequest(field + ".count 가 필요합니다.");
            }
            if (count < 1) {
                throw BridgeException.invalidField(field + ".count 는 1 이상이어야 합니다.");
            }
            attachments.add(new AttachmentRequest(i, itemName, material, count));
        }
        return attachments;
    }

    /** 글자 수(코드포인트) 범위와 제어문자 검사. {@code allowNewline}이면 줄바꿈만 허용. */
    static void checkText(String field, String value, int min, int max, boolean allowNewline) throws BridgeException {
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) {
            throw BridgeException.invalidField(field + " 는 " + min + "~" + max + "자여야 합니다 (현재 " + length + "자).");
        }
        boolean control = value.codePoints()
                .anyMatch(cp -> Character.isISOControl(cp) && !(allowNewline && cp == '\n'));
        if (control) {
            throw BridgeException.invalidField(field + " 에 제어문자를 쓸 수 없습니다.");
        }
    }

    static long parseMailId(String raw) throws BridgeException {
        if (raw == null) {
            throw BridgeException.badRequest("우편 id가 필요합니다.");
        }
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            throw BridgeException.badRequest("우편 id 형식이 올바르지 않습니다: " + raw);
        }
    }

    static JsonObject sendResponse(MailManager.SendResult result) {
        JsonObject body = new JsonObject();
        body.addProperty("ok", true);
        body.addProperty("sent", result.sent());
        body.addProperty("held", result.heldCount());
        JsonArray heldUuids = new JsonArray();
        for (UUID uuid : result.held()) {
            heldUuids.add(uuid.toString());
        }
        body.add("held_uuids", heldUuids);
        JsonArray mailIds = new JsonArray();
        for (Long id : result.mailIds()) {
            mailIds.add(id);
        }
        body.add("mail_ids", mailIds);
        return body;
    }

    /** 우편 한 통 JSON (api-bridge.md §3.2). */
    static JsonObject mailJson(Mail mail) {
        JsonObject object = new JsonObject();
        object.addProperty("id", mail.id());
        object.addProperty("sender_type", mail.senderType().id());
        object.addProperty("sender_name", mail.senderName());
        object.addProperty("title", mail.title());
        object.addProperty("body", mail.body());
        JsonArray attachments = new JsonArray();
        for (MailAttachment attachment : mail.attachments()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("material", attachment.material());
            entry.addProperty("display_name", attachment.displayName());
            entry.addProperty("count", attachment.count());
            entry.addProperty("item_name", attachment.itemName());
            entry.addProperty("remaining", attachment.remaining());
            attachments.add(entry);
        }
        object.add("attachments", attachments);
        object.addProperty("attached_g", mail.attachedG());
        object.addProperty("g_claimed", mail.isGClaimed());
        object.addProperty("created_at", mail.createdAt());
        object.addProperty("expires_at", mail.expiresAt());
        object.addProperty("claimed", mail.isClaimed());
        object.add("claimed_at", mail.isClaimed() && mail.claimedAt() > 0
                ? new JsonPrimitive(mail.claimedAt()) : JsonNull.INSTANCE);
        object.addProperty("held", mail.isHeld());
        return object;
    }
}
