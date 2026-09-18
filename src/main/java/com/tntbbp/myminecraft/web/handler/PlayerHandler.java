package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.tntbbp.myminecraft.web.BridgeException;
import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.WebBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** 플레이어 조회 (api-bridge.md §2.3~2.6). 오프라인 대상도 Mojang 네트워크 조회 없이 서버에 있는 정보만 쓴다. */
public class PlayerHandler {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;
    /** 오프라인 목록은 플레이어 파일을 읽으므로 한 번에 이만큼씩 나눠 메인 스레드에서 처리한다. */
    private static final int OFFLINE_CHUNK = 50;

    private final WebBridge bridge;

    public PlayerHandler(WebBridge bridge) {
        this.bridge = bridge;
    }

    /** {@code GET /players/online}. */
    public BridgeResponse online(BridgeExchange exchange) throws Exception {
        JsonObject body = bridge.callSync(() -> {
            JsonArray players = new JsonArray();
            for (Player player : Bukkit.getOnlinePlayers()) {
                Location location = player.getLocation();
                JsonObject entry = new JsonObject();
                entry.addProperty("uuid", player.getUniqueId().toString());
                entry.addProperty("name", player.getName());
                entry.addProperty("world", location.getWorld() == null ? "" : location.getWorld().getName());
                entry.addProperty("x", location.getX());
                entry.addProperty("y", location.getY());
                entry.addProperty("z", location.getZ());
                entry.addProperty("ping_ms", player.getPing());
                entry.addProperty("gamemode", player.getGameMode().name());
                entry.addProperty("health", player.getHealth());
                entry.addProperty("food", player.getFoodLevel());
                entry.addProperty("level", player.getLevel());
                entry.addProperty("is_op", player.isOp());
                players.add(entry);
            }
            JsonObject result = new JsonObject();
            result.addProperty("count", players.size());
            result.add("players", players);
            return result;
        });
        return BridgeResponse.ok(body);
    }

    /** {@code GET /players/offline?limit=&cursor=} — UUID 순 정렬, 커서는 마지막 UUID를 감싼 불투명 문자열. */
    public BridgeResponse offline(BridgeExchange exchange) throws Exception {
        int limit = parseLimit(exchange.query("limit"));
        String after = decodeCursor(exchange.query("cursor"));

        List<UUID> page = bridge.callSync(() -> {
            List<String> ids = new ArrayList<>();
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                ids.add(player.getUniqueId().toString());
            }
            ids.sort(null);
            List<UUID> selected = new ArrayList<>();
            for (String id : ids) {
                if (after != null && id.compareTo(after) <= 0) {
                    continue;
                }
                selected.add(UUID.fromString(id));
                if (selected.size() > limit) {
                    break;
                }
            }
            return selected;
        });
        boolean hasMore = page.size() > limit;
        List<UUID> visible = hasMore ? page.subList(0, limit) : page;

        JsonArray players = new JsonArray();
        for (int start = 0; start < visible.size(); start += OFFLINE_CHUNK) {
            List<UUID> chunk = visible.subList(start, Math.min(visible.size(), start + OFFLINE_CHUNK));
            JsonArray part = bridge.callSync(() -> {
                JsonArray entries = new JsonArray();
                for (UUID uuid : chunk) {
                    entries.add(offlineEntry(Bukkit.getOfflinePlayer(uuid)));
                }
                return entries;
            });
            players.addAll(part);
        }

        JsonObject body = new JsonObject();
        body.add("players", players);
        body.addProperty("next_cursor", hasMore ? encodeCursor(visible.get(visible.size() - 1).toString()) : null);
        return BridgeResponse.ok(body);
    }

    /** {@code GET /players/resolve?name=} — 서버에 캐시된 이름만(네트워크 조회 금지). */
    public BridgeResponse resolve(BridgeExchange exchange) throws Exception {
        String name = exchange.query("name");
        if (name == null || name.isBlank()) {
            throw BridgeException.badRequest("'name' 쿼리가 필요합니다.");
        }
        String trimmed = name.strip();
        JsonObject body = bridge.callSync(() -> {
            Player online = Bukkit.getPlayerExact(trimmed);
            OfflinePlayer player = online != null ? online : Bukkit.getOfflinePlayerIfCached(trimmed);
            if (player == null) {
                throw BridgeException.notFound("서버에 기록된 플레이어가 아닙니다: " + trimmed);
            }
            JsonObject result = new JsonObject();
            result.addProperty("uuid", player.getUniqueId().toString());
            result.addProperty("name", player.getName() == null ? trimmed : player.getName());
            result.addProperty("online", player.isOnline());
            return result;
        });
        return BridgeResponse.ok(body);
    }

    /** {@code GET /players/{uuid}/profile} — 등록된 제공자별 섹션. 오프라인이어도 동작한다. */
    public BridgeResponse profile(BridgeExchange exchange) throws Exception {
        UUID uuid = parseUuid(exchange.pathParam("uuid"));
        JsonObject body = bridge.callSync(() -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            if (!player.isOnline() && !player.hasPlayedBefore()) {
                throw BridgeException.notFound("플레이어를 찾을 수 없습니다: " + uuid);
            }
            JsonObject result = new JsonObject();
            result.addProperty("uuid", uuid.toString());
            result.addProperty("name", player.getName());
            result.addProperty("online", player.isOnline());
            result.add("sections", bridge.playerDataRegistry().buildSections(player));
            return result;
        });
        return BridgeResponse.ok(body);
    }

    private static JsonObject offlineEntry(OfflinePlayer player) {
        JsonObject entry = new JsonObject();
        entry.addProperty("uuid", player.getUniqueId().toString());
        entry.addProperty("name", player.getName());
        entry.add("first_played", epochOrNull(player.getFirstPlayed()));
        entry.add("last_seen", epochOrNull(player.getLastSeen()));
        entry.addProperty("is_op", player.isOp());
        entry.addProperty("is_banned", player.isBanned());
        entry.addProperty("is_whitelisted", player.isWhitelisted());
        return entry;
    }

    /** 0(기록 없음)이면 JSON null. */
    public static JsonElement epochOrNull(long millis) {
        return millis <= 0 ? JsonNull.INSTANCE : new JsonPrimitive(millis);
    }

    public static UUID parseUuid(String raw) throws BridgeException {
        if (raw == null) {
            throw BridgeException.badRequest("UUID가 필요합니다.");
        }
        try {
            return UUID.fromString(raw.strip());
        } catch (IllegalArgumentException e) {
            throw BridgeException.badRequest("UUID 형식이 올바르지 않습니다: " + raw);
        }
    }

    static int parseLimit(String raw) throws BridgeException {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LIMIT;
        }
        int value;
        try {
            value = Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw BridgeException.badRequest("limit 은 정수여야 합니다.");
        }
        if (value < 1) {
            throw BridgeException.badRequest("limit 은 1 이상이어야 합니다.");
        }
        return Math.min(value, MAX_LIMIT);
    }

    /** 커서 = base64url({"a": "<마지막 UUID>"}). */
    static String encodeCursor(String lastUuid) {
        JsonObject cursor = new JsonObject();
        cursor.addProperty("a", lastUuid);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(cursor.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 커서를 풀어 마지막 UUID 문자열을 돌려준다. 없으면 null, 형식이 틀리면 400 bad_request. */
    static String decodeCursor(String cursor) throws BridgeException {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor.strip()), StandardCharsets.UTF_8);
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String last = object.get("a").getAsString();
            return UUID.fromString(last).toString();
        } catch (IllegalArgumentException | IllegalStateException | JsonParseException
                 | NullPointerException | UnsupportedOperationException e) {
            throw BridgeException.badRequest("cursor 형식이 올바르지 않습니다.");
        }
    }
}
