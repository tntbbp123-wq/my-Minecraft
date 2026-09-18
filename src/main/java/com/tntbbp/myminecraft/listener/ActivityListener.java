package com.tntbbp.myminecraft.listener;

import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.log.EventLog;
import com.tntbbp.myminecraft.log.LogRedaction;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 접속·퇴장·채팅·명령어·사망을 활동(activity) 기록으로 남긴다 (gn-admin {@code docs/api-bridge.md} §5.3).
 * 전부 MONITOR 우선순위에서 결과만 보고 기록하며, 게임 동작은 바꾸지 않는다. 기록은 큐에 넣기만 하므로
 * 비동기 채팅 스레드에서도 기다리지 않는다.
 *
 * <p>{@code activity-log.redact-commands}에 있는 명령(예: /디스코드연동확인)은 인자를 {@code ***}로 가린다.
 * 콘솔·RCON 명령만 {@code server_command}로 남기고, 명령 블록은 너무 잦아서 남기지 않는다.
 */
public class ActivityListener implements Listener {

    private final EventLog eventLog;
    private final boolean logChat;
    private final boolean logCommands;
    private final List<String> redactLabels;
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();

    public ActivityListener(MyMinecraftPlugin plugin, EventLog eventLog) {
        this.eventLog = eventLog;
        this.logChat = plugin.getConfig().getBoolean("activity-log.chat", true);
        this.logCommands = plugin.getConfig().getBoolean("activity-log.commands", true);
        this.redactLabels = List.copyOf(plugin.getConfig().getStringList("activity-log.redact-commands"));
        // 플러그인이 켜질 때 이미 접속해 있던 플레이어는 지금부터 세션을 센다.
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            joinedAt.put(player.getUniqueId(), now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        joinedAt.put(player.getUniqueId(), System.currentTimeMillis());
        JsonObject data = playerData(player);
        InetSocketAddress address = player.getAddress();
        data.addProperty("ip", address == null || address.getAddress() == null
                ? "" : address.getAddress().getHostAddress());
        data.addProperty("first_join", !player.hasPlayedBefore());
        data.addProperty("world", player.getWorld().getName());
        eventLog.write(EventLog.Channel.ACTIVITY, "join", player.getName(), data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Long joined = joinedAt.remove(player.getUniqueId());
        JsonObject data = playerData(player);
        data.addProperty("session_ms", joined == null ? 0L : Math.max(0L, System.currentTimeMillis() - joined));
        data.addProperty("reason", event.getReason().name().toLowerCase(Locale.ROOT));
        eventLog.write(EventLog.Channel.ACTIVITY, "quit", player.getName(), data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!logChat) {
            return;
        }
        Player player = event.getPlayer();
        JsonObject data = playerData(player);
        data.addProperty("message", plain(event.message()));
        eventLog.write(EventLog.Channel.ACTIVITY, "chat", player.getName(), data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!logCommands) {
            return;
        }
        Player player = event.getPlayer();
        JsonObject data = playerData(player);
        data.addProperty("command", LogRedaction.redactCommand(event.getMessage(), redactLabels));
        data.addProperty("cancelled", event.isCancelled());
        eventLog.write(EventLog.Channel.ACTIVITY, "command", player.getName(), data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (!logCommands) {
            return;
        }
        String sender = consoleName(event.getSender());
        if (sender == null) {
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("sender", sender);
        data.addProperty("command", LogRedaction.redactCommand(event.getCommand(), redactLabels));
        eventLog.write(EventLog.Channel.ACTIVITY, "server_command", sender, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        JsonObject data = playerData(player);
        data.addProperty("cause_message", plain(event.deathMessage()));
        data.addProperty("killer", killer == null ? null : killer.getName());
        eventLog.write(EventLog.Channel.ACTIVITY, "death", player.getName(), data);
    }

    private static JsonObject playerData(Player player) {
        JsonObject data = new JsonObject();
        data.addProperty("uuid", player.getUniqueId().toString());
        data.addProperty("name", player.getName());
        return data;
    }

    /** 콘솔이면 "console", RCON이면 "rcon", 그 밖(명령 블록 등)은 null(기록 안 함). */
    private static String consoleName(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return "console";
        }
        if (sender instanceof RemoteConsoleCommandSender) {
            return "rcon";
        }
        return null;
    }

    private static String plain(Component component) {
        if (component == null) {
            return "";
        }
        try {
            return PlainTextComponentSerializer.plainText().serialize(component);
        } catch (RuntimeException e) {
            return "";
        }
    }
}
