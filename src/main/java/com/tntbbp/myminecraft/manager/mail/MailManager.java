package com.tntbbp.myminecraft.manager.mail;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.log.EventLog;
import com.tntbbp.myminecraft.log.TradeLogger;
import com.tntbbp.myminecraft.model.Mail;
import com.tntbbp.myminecraft.model.MailAttachment;
import com.tntbbp.myminecraft.model.MailLimits;
import com.tntbbp.myminecraft.model.Mailbox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 우편 시스템 (기획서 06 §4.6 + 관리자 우편 보완, 계약 gn-admin {@code docs/api-bridge.md} §3).
 *
 * <ul>
 *   <li>우편함은 플레이어별 {@code plugins/MyMinecraft/mail/<uuid>.yml}, 우편 id는 서버 전체에서 하나씩 증가
 *       ({@code mail/counter.yml}). UUID 기준이라 오프라인 플레이어에게도 보낼 수 있다.</li>
 *   <li>일반(시스템·퀘스트) 우편: 보관 7일, 우편함 50통. 가득 차면 새 우편은 보류하고 알린다.
 *       관리자 우편: 상한 제외, 보관 30일(요청으로 1~90일 지정 가능). 값은 config {@code mail.*}.</li>
 *   <li>수령은 개별·일괄. 인벤토리가 부족하면 들어가는 만큼만 받고 나머지는 우편에 남는다(부분 수령).</li>
 *   <li>{@link #send}·{@link #enqueue}는 나중에 퀘스트 보상(P-11) 등이 그대로 쓰는 <b>공개 API</b>다.</li>
 * </ul>
 *
 * <p><b>모든 메서드는 메인 스레드에서 호출한다</b>(웹 통로는 {@code callSync}로 부른다). 파일 쓰기만
 * {@link MailStore}의 쓰기 스레드가 한다.
 */
public class MailManager {

    /** 최근에 쓴 우편함만 메모리에 둔다(바뀌면 곧바로 저장하므로 빠져도 안전). */
    private static final int CACHE_LIMIT = 256;
    /** 만료 우편 점검 간격(틱): 30분. */
    private static final long SWEEP_PERIOD_TICKS = 20L * 60 * 30;
    private static final String COLOR_CODES = "(?i)§[0-9A-FK-ORX]";

    public enum ClaimStatus {
        /** 첨부를 모두 받음(첨부 없는 우편은 읽음 처리). */
        CLAIMED,
        /** 인벤토리가 부족해 일부만 받음. 나머지는 우편에 남음. */
        PARTIAL,
        /** 인벤토리에 빈 공간이 없어 아무것도 받지 못함. */
        NO_SPACE,
        /** 없는 우편(만료·회수됨) 또는 보류 중인 우편. */
        NOT_FOUND,
        ALREADY_CLAIMED
    }

    public enum RecallStatus {
        RECALLED, NOT_FOUND, ALREADY_CLAIMED
    }

    /**
     * 이번에 받은 첨부 한 종류.
     *
     * @param name        기록용 이름(특수 아이템명, 없으면 재질 이름)
     * @param displayName 표시 이름
     * @param material    재질 이름
     */
    public record Received(String name, String displayName, String material, int count) {
    }

    public record ClaimResult(ClaimStatus status, Mail mail, List<Received> received, long receivedG) {
    }

    /**
     * @param claimed   다 받은 우편 수
     * @param partial   일부만 받은 우편 수
     * @param remaining 아직 받을 것이 남은 우편 수
     */
    public record ClaimAllResult(int claimed, int partial, List<Received> received, long receivedG, int remaining) {
    }

    /**
     * @param mailIds   새로 만든 우편 id(보류된 우편 포함)
     * @param delivered 우편함에 바로 들어간 받는 사람
     * @param held      우편함이 가득 차 보류된 받는 사람
     */
    public record SendResult(List<Long> mailIds, List<UUID> delivered, List<UUID> held) {

        public int sent() {
            return delivered.size();
        }

        public int heldCount() {
            return held.size();
        }
    }

    private record QueuedSend(MailSpec spec, List<UUID> recipients) {
    }

    private record Delivery(List<Received> received, long g, boolean changed) {
    }

    private final MyMinecraftPlugin plugin;
    private final MailStore store;
    private final Map<UUID, Mailbox> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Mailbox> eldest) {
            return size() > CACHE_LIMIT;
        }
    };
    private final Deque<QueuedSend> queue = new ArrayDeque<>();
    private long nextId;
    private BukkitTask queueTask;
    private BukkitTask sweepTask;
    private volatile boolean sweeping;

    public MailManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.store = new MailStore(new File(plugin.getDataFolder(), "mail").toPath(), plugin.getLogger());
        this.nextId = store.loadNextId();
    }

    /** 만료 우편 주기 점검 시작(onEnable). */
    public void start() {
        if (sweepTask == null) {
            sweepTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::scanExpired,
                    20L * 30, SWEEP_PERIOD_TICKS);
        }
    }

    /** onDisable: 지연 발송 대기열을 바로 보내고, 남은 파일 쓰기를 모두 끝낸다. */
    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        if (queueTask != null) {
            queueTask.cancel();
            queueTask = null;
        }
        QueuedSend queued;
        while ((queued = queue.poll()) != null) {
            try {
                send(queued.spec(), queued.recipients());
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "종료 중 대기 우편 발송 실패", e);
            }
        }
        store.flush();
    }

    /** 현재 설정(config {@code mail.*})의 상한·보관 규칙. */
    public MailLimits limits() {
        FileConfiguration config = plugin.getConfig();
        return new MailLimits(
                config.getInt("mail.regular-max-count", MailLimits.DEFAULT.regularMaxCount()),
                config.getInt("mail.admin-max-count", MailLimits.DEFAULT.adminMaxCount()),
                config.getInt("mail.regular-retention-days", MailLimits.DEFAULT.regularRetentionDays()),
                config.getInt("mail.admin-retention-days", MailLimits.DEFAULT.adminRetentionDays()));
    }

    // ---------------------------------------------------------------- 발송 (공개 API)

    /** 한 명에게 보낸다. {@link #send(MailSpec, Collection)} 참고. */
    public SendResult send(MailSpec spec, UUID recipient) {
        return send(spec, List.of(recipient));
    }

    /**
     * 받는 사람마다 우편을 한 통씩 만든다(전역 id 부여). 우편함이 가득 차면 보류하고, 접속 중이면 알린다.
     * 관리자 우편이면 거래 기록 {@code admin_mail_send}를 <b>받는 사람마다 1건</b> 남긴다(웹 유저 상세 거래 탭).
     * 같은 받는 사람이 여러 번 있으면 한 번만 보낸다.
     */
    public SendResult send(MailSpec spec, Collection<UUID> recipients) {
        Set<UUID> targets = new LinkedHashSet<>();
        if (recipients != null) {
            for (UUID recipient : recipients) {
                if (recipient != null) {
                    targets.add(recipient);
                }
            }
        }
        if (targets.isEmpty()) {
            return new SendResult(List.of(), List.of(), List.of());
        }
        List<MailAttachment> attachments = toAttachments(spec.items());
        String actor = spec.actor() == null ? EventLog.SYSTEM_ACTOR : spec.actor();
        MailLimits limits = limits();
        int days = spec.retentionDays() > 0 ? spec.retentionDays() : limits.retentionDaysFor(spec.senderType());
        long now = System.currentTimeMillis();

        // id를 먼저 예약·저장한다(쓰기 스레드는 순서대로 쓰므로 우편 파일보다 카운터가 먼저 디스크에 남는다).
        long id = nextId;
        nextId += targets.size();
        store.saveNextId(nextId);

        List<Long> mailIds = new ArrayList<>();
        List<UUID> delivered = new ArrayList<>();
        List<UUID> held = new ArrayList<>();
        for (UUID owner : targets) {
            Mailbox mailbox = mailbox(owner);
            boolean hold = !mailbox.hasRoom(spec.senderType(), limits);
            List<MailAttachment> copies = new ArrayList<>();
            for (MailAttachment attachment : attachments) {
                copies.add(attachment.fresh());
            }
            Mail mail = new Mail(id++, spec.senderType(), spec.senderName(), spec.title(), spec.body(), copies,
                    spec.attachedG(), false, now, MailRules.expiresAt(now, days), false, 0L, hold);
            mailbox.add(mail);
            store.saveMailbox(mailbox);
            mailIds.add(mail.id());
            (hold ? held : delivered).add(owner);
            if (spec.senderType().isAdmin()) {
                plugin.getTradeLogger().record("admin_mail_send", actor,
                        adminMailSendData(actor, owner, Bukkit.getOfflinePlayer(owner).getName(), mail));
            }

            Player online = Bukkit.getPlayer(owner);
            if (online != null) {
                notifyArrival(online, mail, limits);
            }
        }
        return new SendResult(List.copyOf(mailIds), List.copyOf(delivered), List.copyOf(held));
    }

    /**
     * 지연 발송 대기열에 넣는다(기획서 06: 퀘스트를 한꺼번에 달성해도 우편이 몰리지 않게 {@code mail.send-batch-delay-ticks}
     * 간격으로 한 건씩 보냄, 기본 3초). 서버가 꺼질 때 남은 것은 바로 보낸다.
     */
    public void enqueue(MailSpec spec, Collection<UUID> recipients) {
        List<UUID> targets = new ArrayList<>();
        if (recipients != null) {
            for (UUID recipient : recipients) {
                if (recipient != null) {
                    targets.add(recipient);
                }
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        queue.add(new QueuedSend(spec, List.copyOf(targets)));
        scheduleQueue();
    }

    private void scheduleQueue() {
        if (queueTask != null || queue.isEmpty() || !plugin.isEnabled()) {
            return;
        }
        long delay = Math.max(1L, plugin.getConfig().getLong("mail.send-batch-delay-ticks", 60L));
        queueTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            queueTask = null;
            QueuedSend next = queue.poll();
            if (next != null) {
                try {
                    send(next.spec(), next.recipients());
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "대기 우편 발송 실패: " + next.spec().title(), e);
                }
            }
            scheduleQueue();
        }, delay);
    }

    // ---------------------------------------------------------------- 조회

    /** 전체 우편(보류·수령 완료 포함), 최신순. 오프라인 플레이어도 된다. */
    public List<Mail> list(UUID owner) {
        List<Mail> mails = new ArrayList<>(mailbox(owner).mails());
        Collections.reverse(mails);
        return mails;
    }

    /** 우편함에서 받을 수 있는 우편(보류·수령 완료 제외), 최신순. */
    public List<Mail> claimable(UUID owner) {
        List<Mail> mails = mailbox(owner).active();
        Collections.reverse(mails);
        return mails;
    }

    /** 받지 않은 우편 수(보류 제외). */
    public int unclaimedCount(UUID owner) {
        return mailbox(owner).activeCount();
    }

    /** 우편함이 가득 차 보류된 우편 수. */
    public int heldCount(UUID owner) {
        return mailbox(owner).heldCount();
    }

    // ---------------------------------------------------------------- 수령

    /** 우편 한 통을 받는다(부분 수령 가능). 받은 것이 있으면 거래 기록 {@code mail_claim}. */
    public ClaimResult claim(Player player, long mailId) {
        Mailbox mailbox = mailbox(player.getUniqueId());
        Mail mail = mailbox.find(mailId);
        if (mail == null || mail.isHeld()) {
            return new ClaimResult(ClaimStatus.NOT_FOUND, mail, List.of(), 0L);
        }
        if (mail.isClaimed()) {
            return new ClaimResult(ClaimStatus.ALREADY_CLAIMED, mail, List.of(), 0L);
        }
        Delivery delivery = deliver(player, mail);
        if (!delivery.changed()) {
            return new ClaimResult(ClaimStatus.NO_SPACE, mail, List.of(), 0L);
        }
        logClaim(player, mail, delivery);
        saveAfterChange(mailbox);
        return new ClaimResult(mail.isClaimed() ? ClaimStatus.CLAIMED : ClaimStatus.PARTIAL, mail,
                delivery.received(), delivery.g());
    }

    /** 받을 수 있는 우편을 오래된 순으로 모두 받는다(인벤토리가 차면 들어가는 만큼만). */
    public ClaimAllResult claimAll(Player player) {
        Mailbox mailbox = mailbox(player.getUniqueId());
        int claimed = 0;
        int partial = 0;
        long totalG = 0L;
        Map<String, Received> merged = new LinkedHashMap<>();
        boolean changed = false;
        for (Mail mail : mailbox.active()) {
            Delivery delivery = deliver(player, mail);
            if (!delivery.changed()) {
                continue;
            }
            changed = true;
            logClaim(player, mail, delivery);
            if (mail.isClaimed()) {
                claimed++;
            } else {
                partial++;
            }
            totalG += delivery.g();
            for (Received received : delivery.received()) {
                merged.merge(received.name(), received, (a, b) ->
                        new Received(a.name(), a.displayName(), a.material(), a.count() + b.count()));
            }
        }
        if (changed) {
            saveAfterChange(mailbox);
        }
        return new ClaimAllResult(claimed, partial, List.copyOf(merged.values()), totalG, mailbox.activeCount());
    }

    private Delivery deliver(Player player, Mail mail) {
        long now = System.currentTimeMillis();
        PlayerInventory inventory = player.getInventory();
        int slots = inventory.getStorageContents().length;
        List<MailAttachment> attachments = mail.attachments();
        int[] delivered = new int[attachments.size()];
        List<Received> received = new ArrayList<>();
        for (int i = 0; i < attachments.size(); i++) {
            MailAttachment attachment = attachments.get(i);
            if (attachment.remaining() <= 0) {
                continue;
            }
            ItemStack template = decode(attachment);
            if (template == null || template.getType().isAir()) {
                plugin.getLogger().warning("우편 " + mail.id() + "의 첨부 아이템(" + attachment.logName()
                        + ")을 읽지 못해 우편에 그대로 둡니다.");
                continue;
            }
            int maxStack = Math.max(1, template.getMaxStackSize());
            int attempt = MailRules.attemptCount(attachment.remaining(), maxStack, slots);
            int[] sizes = MailRules.splitStacks(attempt, maxStack);
            ItemStack[] stacks = new ItemStack[sizes.length];
            for (int s = 0; s < sizes.length; s++) {
                stacks[s] = template.clone();
                stacks[s].setAmount(sizes[s]);
            }
            int left = 0;
            for (ItemStack leftover : inventory.addItem(stacks).values()) {
                left += leftover.getAmount();
            }
            int got = Math.max(0, attempt - left);
            delivered[i] = got;
            if (got > 0) {
                received.add(new Received(attachment.logName(), attachment.displayName(), attachment.material(), got));
            }
        }
        long g = mail.pendingG();
        if (g > 0) {
            plugin.getEconomyManager().add(player.getUniqueId(), g);
        }
        boolean wasClaimed = mail.isClaimed();
        mail.applyDelivery(delivered, g > 0, now);
        boolean changed = !received.isEmpty() || g > 0 || (mail.isClaimed() && !wasClaimed);
        return new Delivery(List.copyOf(received), g, changed);
    }

    private void logClaim(Player player, Mail mail, Delivery delivery) {
        if (delivery.received().isEmpty() && delivery.g() == 0) {
            return; // 첨부 없는 우편을 읽기만 함
        }
        List<TradeLogger.ItemCount> items = new ArrayList<>();
        for (Received received : delivery.received()) {
            items.add(new TradeLogger.ItemCount(received.name(), received.count()));
        }
        plugin.getTradeLogger().mailClaim(player.getUniqueId(), player.getName(), mail.id(), items, delivery.g(),
                !mail.isClaimed());
    }

    // ---------------------------------------------------------------- 회수·만료

    /** 받지 않은 우편을 회수(삭제)한다. 일부만 받은 우편은 남은 첨부째 회수된다. */
    public RecallStatus recall(UUID owner, long mailId) {
        Mailbox mailbox = mailbox(owner);
        Mail mail = mailbox.find(mailId);
        if (mail == null) {
            return RecallStatus.NOT_FOUND;
        }
        if (mail.isClaimed()) {
            return RecallStatus.ALREADY_CLAIMED;
        }
        mailbox.remove(mailId);
        saveAfterChange(mailbox);
        return RecallStatus.RECALLED;
    }

    /** 모든 우편함에서 만료된 우편을 지운다(보류·수령 완료 포함). @return 지운 우편 수 */
    public int purgeExpired() {
        int total = 0;
        for (UUID owner : store.owners()) {
            total += purgeOwner(owner);
        }
        return total;
    }

    private int purgeOwner(UUID owner) {
        Mailbox mailbox = cache.get(owner);
        if (mailbox == null) {
            mailbox = store.loadMailbox(owner);
        }
        long now = System.currentTimeMillis();
        int purged = mailbox.purgeExpired(now);
        if (purged > 0) {
            List<Mail> released = mailbox.releaseHeld(now, limits());
            store.saveMailbox(mailbox);
            notifyReleased(owner, released);
        }
        return purged;
    }

    /** 비동기: 우편 파일을 읽기만 해서 만료 우편이 있는 사람을 찾고, 정리는 메인 스레드에서 한다. */
    private void scanExpired() {
        if (sweeping) {
            return;
        }
        sweeping = true;
        try {
            long now = System.currentTimeMillis();
            List<UUID> due = new ArrayList<>();
            for (UUID owner : store.owners()) {
                if (store.loadMailbox(owner, false).hasExpired(now)) {
                    due.add(owner);
                }
            }
            if (!due.isEmpty() && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int purged = 0;
                    for (UUID owner : due) {
                        purged += purgeOwner(owner);
                    }
                    if (purged > 0) {
                        plugin.getLogger().info("보관 기간이 지난 우편 " + purged + "통을 정리했습니다.");
                    }
                });
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "만료 우편 점검 중 오류", e);
        } finally {
            sweeping = false;
        }
    }

    // ---------------------------------------------------------------- 우편함 캐시

    /** 우편함(캐시 → 파일). 읽을 때마다 만료 정리와 보류 해제를 한다. */
    private Mailbox mailbox(UUID owner) {
        Mailbox mailbox = cache.get(owner);
        if (mailbox == null) {
            mailbox = store.loadMailbox(owner);
            cache.put(owner, mailbox);
        }
        long now = System.currentTimeMillis();
        int purged = mailbox.purgeExpired(now);
        List<Mail> released = mailbox.releaseHeld(now, limits());
        if (purged > 0 || !released.isEmpty()) {
            store.saveMailbox(mailbox);
            notifyReleased(owner, released);
        }
        return mailbox;
    }

    /** 우편을 받거나 회수해 공간이 났으면 보류 우편을 넣고 저장한다. */
    private void saveAfterChange(Mailbox mailbox) {
        List<Mail> released = mailbox.releaseHeld(System.currentTimeMillis(), limits());
        store.saveMailbox(mailbox);
        notifyReleased(mailbox.owner(), released);
    }

    // ---------------------------------------------------------------- 알림

    /** 접속 알림: "새 우편 N통" + 보류 우편 안내. */
    public void notifyOnJoin(Player player) {
        Mailbox mailbox = mailbox(player.getUniqueId());
        int unclaimed = mailbox.activeCount();
        int held = mailbox.heldCount();
        if (unclaimed > 0) {
            player.sendMessage(prefix()
                    .append(Component.text("새 우편 ", NamedTextColor.WHITE))
                    .append(Component.text(unclaimed + "통", NamedTextColor.YELLOW))
                    .append(Component.text("이 있습니다. ", NamedTextColor.WHITE))
                    .append(openLink()));
        }
        if (held > 0) {
            player.sendMessage(prefix().append(Component.text("우편함이 가득 차 보류된 우편이 " + held
                    + "통 있습니다. 우편을 받아 공간을 비우면 자동으로 들어옵니다.", NamedTextColor.RED)));
        }
    }

    private void notifyArrival(Player player, Mail mail, MailLimits limits) {
        if (mail.isHeld()) {
            player.sendMessage(prefix().append(Component.text("우편함이 가득 차("
                    + limits.maxCountFor(mail.senderType()) + "통) 새 우편 '" + mail.title()
                    + "'이(가) 보류되었습니다. 우편을 받아 공간을 비우면 자동으로 들어옵니다.", NamedTextColor.RED)));
            return;
        }
        player.sendMessage(prefix()
                .append(Component.text("새 우편이 도착했습니다: ", NamedTextColor.WHITE))
                .append(Component.text(mail.title(), NamedTextColor.YELLOW))
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(openLink()));
    }

    private void notifyReleased(UUID owner, List<Mail> released) {
        if (released.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(owner);
        if (player != null) {
            player.sendMessage(prefix()
                    .append(Component.text("보류됐던 우편 " + released.size() + "통이 우편함에 들어왔습니다. ",
                            NamedTextColor.WHITE))
                    .append(openLink()));
        }
    }

    private static Component prefix() {
        return Component.text("[우편] ", NamedTextColor.GOLD);
    }

    private static Component openLink() {
        return Component.text("[우편함 열기]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/우편함"))
                .hoverEvent(HoverEvent.showText(Component.text("클릭하면 우편함을 엽니다 (/우편함)")));
    }

    // ---------------------------------------------------------------- 아이템 직렬화

    /** 첨부 견본 아이템(1개)을 되살린다. 읽지 못하면 null. */
    public static ItemStack decode(MailAttachment attachment) {
        if (attachment == null || attachment.data() == null) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(attachment.data()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<MailAttachment> toAttachments(List<MailSpec.Item> items) {
        List<MailAttachment> result = new ArrayList<>();
        for (MailSpec.Item item : items) {
            ItemStack template = item.template().clone();
            template.setAmount(1);
            String data = Base64.getEncoder().encodeToString(template.serializeAsBytes());
            String displayName = item.displayName() != null && !item.displayName().isBlank()
                    ? item.displayName().replaceAll(COLOR_CODES, "")
                    : displayNameOf(template);
            result.add(new MailAttachment(data, template.getType().name(), displayName, item.itemName(),
                    item.count(), 0));
        }
        return result;
    }

    /** 표시 이름: 아이템에 붙은 이름(색 제거), 없으면 재질 이름. */
    static String displayNameOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(name).replaceAll(COLOR_CODES, "");
                if (!plain.isBlank()) {
                    return plain;
                }
            }
        }
        return item.getType().name();
    }

    /**
     * 관리자 우편 발송 거래 기록 한 건(받는 사람 1명)의 data (api-bridge.md §5.4, P2 변경 메모):
     * {@code {"actor","uuid","name","recipients_count":1,"title","attachments":[{"name","count"}],"attached_g",
     * "mail_id","mail_ids":[mail_id],"held"}}.
     */
    static JsonObject adminMailSendData(String actor, UUID owner, String name, Mail mail) {
        JsonObject data = new JsonObject();
        data.addProperty("actor", actor);
        data.addProperty("uuid", owner == null ? null : owner.toString());
        data.addProperty("name", name);
        data.addProperty("recipients_count", 1);
        data.addProperty("title", mail.title());
        JsonArray items = new JsonArray();
        for (MailAttachment attachment : mail.attachments()) {
            JsonObject item = new JsonObject();
            item.addProperty("name", attachment.logName());
            item.addProperty("count", attachment.count());
            items.add(item);
        }
        data.add("attachments", items);
        data.addProperty("attached_g", mail.attachedG());
        data.addProperty("mail_id", mail.id());
        JsonArray ids = new JsonArray();
        ids.add(mail.id());
        data.add("mail_ids", ids);
        data.addProperty("held", mail.isHeld());
        return data;
    }
}
