package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.web.BridgeException;
import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.WebBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;

/**
 * 서버 상태·게임 안 공지·카운트다운·저장 (api-bridge.md §2.2, §2.12).
 * 실제 서버 끄기/재시작은 웹의 전원 도우미가 하고, 통로는 공지와 저장만 맡는다.
 */
public class ServerHandler {

    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int MAX_COUNTDOWN_SECONDS = 3600;
    private static final long AI_STATUS_CACHE_MILLIS = 60_000L;
    /** 채팅 공지를 하는 남은 시간(초). 시작 시점에는 항상 한 번 공지한다. */
    private static final Set<Integer> ANNOUNCE_POINTS =
            Set.of(1800, 1200, 600, 300, 180, 120, 60, 30, 10, 5, 4, 3, 2, 1);

    /** 진행 중인 카운트다운(메인 스레드에서만 접근). */
    private static final class Countdown {
        private final String id;
        private final int seconds;
        private final String action;
        private final String message;
        private final long endAtMillis;
        private BukkitTask task;
        private int lastRemaining = -1;

        private Countdown(String id, int seconds, String action, String message, long endAtMillis) {
            this.id = id;
            this.seconds = seconds;
            this.action = action;
            this.message = message;
            this.endAtMillis = endAtMillis;
        }
    }

    private final WebBridge bridge;
    private Countdown countdown;
    private volatile boolean cachedAiEnabled;
    private volatile long aiCheckedAtMillis;

    public ServerHandler(WebBridge bridge) {
        this.bridge = bridge;
    }

    /** {@code GET /server/status}. */
    public BridgeResponse status(BridgeExchange exchange) throws Exception {
        long lag = bridge.mainThread().mainThreadLagMs();
        JsonObject body = bridge.callSync(() -> {
            JsonObject result = new JsonObject();
            result.addProperty("online", true);
            double[] tps = Bukkit.getTPS();
            JsonObject tpsObject = new JsonObject();
            tpsObject.addProperty("m1", round2(tps.length > 0 ? tps[0] : 20.0));
            tpsObject.addProperty("m5", round2(tps.length > 1 ? tps[1] : 20.0));
            tpsObject.addProperty("m15", round2(tps.length > 2 ? tps[2] : 20.0));
            result.add("tps", tpsObject);
            result.addProperty("mspt", round2(Bukkit.getAverageTickTime()));
            result.addProperty("players_online", Bukkit.getOnlinePlayers().size());
            result.addProperty("players_max", Bukkit.getMaxPlayers());
            JsonArray worlds = new JsonArray();
            for (World world : Bukkit.getWorlds()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", world.getName());
                entry.addProperty("players", world.getPlayerCount());
                entry.addProperty("loaded_chunks", world.getChunkCount());
                entry.addProperty("entities", world.getEntityCount());
                worlds.add(entry);
            }
            result.add("worlds", worlds);
            return result;
        });
        body.addProperty("main_thread_lag_ms", lag);
        body.addProperty("ai_enabled", aiEnabled());
        body.addProperty("plugin_version", bridge.plugin().getPluginMeta().getVersion());
        return BridgeResponse.ok(body);
    }

    /** {@code POST /server/broadcast} {@code {"message"}}. */
    public BridgeResponse broadcast(BridgeExchange exchange) throws Exception {
        JsonObject request = exchange.json();
        String message = BridgeExchange.reqString(request, "message");
        validateMessage("message", message, false);
        String actor = exchange.actor();
        bridge.callSync(() -> Bukkit.broadcast(Component.text(message)));
        bridge.adminLog().serverBroadcast(actor, message);
        return BridgeResponse.okTrue();
    }

    /** {@code POST /server/countdown} {@code {"seconds", "action": "stop|restart", "message"?}}. 공지만 하고 서버는 끄지 않는다. */
    public BridgeResponse startCountdown(BridgeExchange exchange) throws Exception {
        JsonObject request = exchange.json();
        Integer seconds = BridgeExchange.optInt(request, "seconds");
        if (seconds == null) {
            throw BridgeException.badRequest("'seconds' 가 필요합니다.");
        }
        if (seconds < 1 || seconds > MAX_COUNTDOWN_SECONDS) {
            throw BridgeException.invalidField("seconds 는 1~" + MAX_COUNTDOWN_SECONDS + " 사이여야 합니다.");
        }
        String action = BridgeExchange.reqString(request, "action");
        if (!action.equals("stop") && !action.equals("restart")) {
            throw BridgeException.invalidField("action 은 stop 또는 restart 여야 합니다.");
        }
        String message = BridgeExchange.optString(request, "message");
        if (message != null && message.isBlank()) {
            message = null;
        }
        if (message != null) {
            validateMessage("message", message, true);
        }
        String actor = exchange.actor();
        String finalMessage = message;
        String countdownId = bridge.callSync(() -> {
            if (countdown != null) {
                throw BridgeException.conflict("이미 진행 중인 카운트다운이 있습니다.");
            }
            Countdown created = new Countdown("cd-" + UUID.randomUUID(), seconds, action, finalMessage,
                    System.currentTimeMillis() + seconds * 1000L);
            countdown = created;
            announce(created, seconds, true);
            created.lastRemaining = seconds;
            MyMinecraftPlugin plugin = bridge.plugin();
            created.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickCountdown(created), 20L, 20L);
            return created.id;
        });
        bridge.adminLog().serverCountdown(actor, seconds, action, false);
        JsonObject body = new JsonObject();
        body.addProperty("ok", true);
        body.addProperty("countdown_id", countdownId);
        return BridgeResponse.ok(body);
    }

    /** {@code DELETE /server/countdown} {@code {"countdown_id"?}}. 진행 중인 카운트다운이 없으면 404. */
    public BridgeResponse cancelCountdown(BridgeExchange exchange) throws Exception {
        JsonObject request = exchange.json();
        String countdownId = BridgeExchange.optString(request, "countdown_id");
        String actor = exchange.actor();
        Countdown cancelled = bridge.callSync(() -> {
            Countdown current = countdown;
            if (current == null || (countdownId != null && !countdownId.equals(current.id))) {
                throw BridgeException.notFound("진행 중인 카운트다운이 없습니다.");
            }
            stopCountdown(current);
            Bukkit.broadcast(prefix().append(Component.text(
                    "예정된 서버 " + actionLabel(current.action) + " 카운트다운이 취소되었습니다.", NamedTextColor.WHITE)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(Component.empty());
            }
            return current;
        });
        bridge.adminLog().serverCountdown(actor, cancelled.seconds, cancelled.action, true);
        return BridgeResponse.okTrue();
    }

    /** {@code POST /server/save} — 접속자 데이터와 모든 월드를 저장한다. 제한 시간은 {@code save-timeout-ms}. */
    public BridgeResponse save(BridgeExchange exchange) throws Exception {
        String actor = exchange.actor();
        long elapsed = bridge.callSync(() -> {
            long started = System.nanoTime();
            Bukkit.savePlayers();
            for (World world : Bukkit.getWorlds()) {
                world.save();
            }
            return (System.nanoTime() - started) / 1_000_000L;
        }, bridge.saveTimeoutMs());
        bridge.adminLog().serverSave(actor, elapsed);
        JsonObject body = new JsonObject();
        body.addProperty("ok", true);
        body.addProperty("elapsed_ms", elapsed);
        return BridgeResponse.ok(body);
    }

    /** 통로 정지 시(메인 스레드) 진행 중인 카운트다운 태스크를 멈춘다. */
    public void shutdown() {
        Countdown current = countdown;
        if (current != null) {
            stopCountdown(current);
        }
    }

    private void tickCountdown(Countdown current) {
        if (countdown != current) {
            if (current.task != null) {
                current.task.cancel();
            }
            return;
        }
        long remainingMillis = current.endAtMillis - System.currentTimeMillis();
        int remaining = (int) Math.ceil(remainingMillis / 1000.0);
        if (remaining <= 0) {
            stopCountdown(current);
            Bukkit.broadcast(prefix().append(Component.text(
                    "곧 서버가 " + actionLabel(current.action) + "됩니다.", NamedTextColor.WHITE)));
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(Component.text("서버 " + actionLabel(current.action) + "까지 ", NamedTextColor.YELLOW)
                    .append(Component.text(formatSeconds(remaining), NamedTextColor.RED)));
        }
        if (remaining != current.lastRemaining && isAnnouncePoint(remaining)) {
            announce(current, remaining, false);
        }
        current.lastRemaining = remaining;
    }

    private void announce(Countdown current, int remaining, boolean first) {
        Component line = prefix().append(Component.text(
                formatSeconds(remaining) + " 후 서버가 " + actionLabel(current.action) + "됩니다.", NamedTextColor.WHITE));
        // 관리자 메시지는 시작 공지와 10초 이상 남은 공지에만 붙인다(마지막 초읽기는 짧게).
        if (current.message != null && (first || remaining >= 10)) {
            line = line.append(Component.text(" " + current.message, NamedTextColor.GRAY));
        }
        Bukkit.broadcast(line);
    }

    private void stopCountdown(Countdown current) {
        if (current.task != null) {
            current.task.cancel();
        }
        if (countdown == current) {
            countdown = null;
        }
    }

    private boolean aiEnabled() {
        long now = System.currentTimeMillis();
        if (now - aiCheckedAtMillis >= AI_STATUS_CACHE_MILLIS) {
            // isEnabled()는 설정값만 보지만 키를 env:/file:로 풀면서 경고를 남길 수 있어 1분에 한 번만 확인한다.
            cachedAiEnabled = bridge.plugin().getGeminiNewsClient().isEnabled();
            aiCheckedAtMillis = now;
        }
        return cachedAiEnabled;
    }

    private static Component prefix() {
        return Component.text("[서버] ", NamedTextColor.RED);
    }

    static String actionLabel(String action) {
        return "restart".equals(action) ? "재시작" : "종료";
    }

    /** 채팅 공지를 하는 남은 시간(초)인지. */
    static boolean isAnnouncePoint(int remainingSeconds) {
        return ANNOUNCE_POINTS.contains(remainingSeconds);
    }

    /** 90 → "1분 30초", 60 → "1분", 5 → "5초". */
    static String formatSeconds(int seconds) {
        int minutes = seconds / 60;
        int rest = seconds % 60;
        if (minutes == 0) {
            return rest + "초";
        }
        return rest == 0 ? minutes + "분" : minutes + "분 " + rest + "초";
    }

    private static void validateMessage(String field, String message, boolean allowEmpty) throws BridgeException {
        int length = message.codePointCount(0, message.length());
        if ((!allowEmpty && message.isBlank()) || length < 1 || length > MAX_MESSAGE_LENGTH) {
            throw BridgeException.invalidField(field + " 는 1~" + MAX_MESSAGE_LENGTH + "자여야 합니다.");
        }
        if (BridgeExchange.hasControlChars(message)) {
            throw BridgeException.invalidField(field + " 에 줄바꿈·제어문자를 넣을 수 없습니다.");
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
