package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.log.EventLog;
import com.tntbbp.myminecraft.log.LogRedaction;
import com.tntbbp.myminecraft.web.BridgeException;
import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.CommandRunner;
import com.tntbbp.myminecraft.web.WebBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** 원격 명령 (api-bridge.md §2.7~2.10). 실행은 {@link CommandRunner}가 메인 스레드에서 한다. */
public class CommandHandler {

    private static final int MAX_COMMAND_LENGTH = 1024;

    private final WebBridge bridge;
    private final List<String> redactLabels;
    private final boolean logActivityCommands;

    /** 메인 스레드(통로 시작 시)에서 만든다. 가림 목록·기록 여부는 이때 한 번 읽는다. */
    public CommandHandler(WebBridge bridge) {
        this.bridge = bridge;
        MyMinecraftPlugin plugin = bridge.plugin();
        this.redactLabels = List.copyOf(plugin.getConfig().getStringList("activity-log.redact-commands"));
        this.logActivityCommands = plugin.getConfig().getBoolean("activity-log.enabled", true)
                && plugin.getConfig().getBoolean("activity-log.commands", true);
    }

    /** {@code GET /commands}의 한 항목(같은 이름·같은 출처의 키들을 하나로 묶음). */
    private static final class CommandGroup {
        private final String name;
        private final String owner;
        private final TreeSet<String> aliases = new TreeSet<>();
        private String description;
        private String usage;

        private CommandGroup(String name, String owner) {
            this.name = name;
            this.owner = owner;
        }
    }

    /**
     * {@code GET /commands} — 등록된 명령·출처·별칭 (CommandMap.getKnownCommands 기준).
     * Paper는 바닐라 명령을 키마다 새 래퍼 객체로 돌려주므로(예: {@code give}와 {@code minecraft:give}),
     * 객체가 아니라 "네임스페이스를 뗀 이름 + 출처"로 묶는다.
     */
    public BridgeResponse list(BridgeExchange exchange) throws Exception {
        JsonObject body = bridge.callSync(() -> {
            Map<String, String> pluginNames = new LinkedHashMap<>();
            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                pluginNames.put(plugin.getName().toLowerCase(Locale.ROOT), plugin.getName());
            }
            Map<String, Command> known = new LinkedHashMap<>(Bukkit.getCommandMap().getKnownCommands());
            // "bukkit:reload" 같은 네임스페이스 키에서 (명령 객체 → 네임스페이스), (이름 → 후보들)을 먼저 모은다
            Map<Command, String> namespaceByCommand = new IdentityHashMap<>();
            Map<String, List<Map.Entry<String, Command>>> namespacesByLabel = new LinkedHashMap<>();
            for (Map.Entry<String, Command> entry : known.entrySet()) {
                String key = entry.getKey();
                int colon = key.indexOf(':');
                if (colon > 0 && entry.getValue() != null) {
                    String namespace = key.substring(0, colon);
                    namespaceByCommand.putIfAbsent(entry.getValue(), namespace);
                    namespacesByLabel.computeIfAbsent(key.substring(colon + 1), k -> new ArrayList<>())
                            .add(Map.entry(namespace, entry.getValue()));
                }
            }

            Map<String, CommandGroup> groups = new LinkedHashMap<>();
            for (Map.Entry<String, Command> entry : known.entrySet()) {
                Command command = entry.getValue();
                if (command == null) {
                    continue;
                }
                String key = entry.getKey();
                int colon = key.indexOf(':');
                String label = colon > 0 ? key.substring(colon + 1) : key;
                String name = stripNamespace(command.getName());
                String namespace = colon > 0 ? key.substring(0, colon)
                        : namespaceOf(command, name, label, namespaceByCommand, namespacesByLabel);
                String owner = ownerOf(command, namespace, pluginNames);

                CommandGroup group = groups.computeIfAbsent(owner + "|" + name, k -> new CommandGroup(name, owner));
                group.aliases.add(label);
                for (String alias : command.getAliases()) {
                    group.aliases.add(stripNamespace(alias));
                }
                // 설명·사용법은 네임스페이스 없는 키 쪽을 우선한다
                if (group.usage == null || colon < 0) {
                    group.description = nullToEmpty(command.getDescription());
                    group.usage = nullToEmpty(command.getUsage());
                }
            }

            List<CommandGroup> ordered = new ArrayList<>(groups.values());
            ordered.sort(Comparator.comparing((CommandGroup group) -> group.name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(group -> group.owner));
            JsonArray commands = new JsonArray();
            for (CommandGroup group : ordered) {
                group.aliases.remove(group.name);
                JsonArray aliasArray = new JsonArray();
                group.aliases.forEach(aliasArray::add);
                JsonObject entry = new JsonObject();
                entry.addProperty("name", group.name);
                entry.addProperty("plugin", group.owner);
                entry.addProperty("description", group.description);
                entry.addProperty("usage", group.usage);
                entry.add("aliases", aliasArray);
                commands.add(entry);
            }
            JsonObject result = new JsonObject();
            result.add("commands", commands);
            return result;
        });
        return BridgeResponse.ok(body);
    }

    /**
     * 네임스페이스 없는 키의 출처. 같은 명령 객체가 네임스페이스 키로도 등록돼 있으면 그것을 쓰고,
     * 아니면(Paper 바닐라 래퍼처럼 키마다 객체가 다르면) 같은 이름의 네임스페이스 키 중 같은 종류의 명령을 고른다.
     */
    private static String namespaceOf(Command command, String name, String label,
                                      Map<Command, String> namespaceByCommand,
                                      Map<String, List<Map.Entry<String, Command>>> namespacesByLabel) {
        String byIdentity = namespaceByCommand.get(command);
        if (byIdentity != null) {
            return byIdentity;
        }
        List<Map.Entry<String, Command>> candidates = namespacesByLabel.get(name);
        if (candidates == null) {
            candidates = namespacesByLabel.get(label);
        }
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Command> candidate : candidates) {
            if (candidate.getValue().getClass() == command.getClass()) {
                return candidate.getKey();
            }
        }
        return candidates.get(0).getKey();
    }

    private static String stripNamespace(String label) {
        if (label == null) {
            return "";
        }
        int colon = label.indexOf(':');
        return colon > 0 ? label.substring(colon + 1) : label;
    }

    /** {@code POST /commands/complete} {@code {"line"}} — 읽기성 POST(멱등 키 불필요). */
    public BridgeResponse complete(BridgeExchange exchange) throws Exception {
        String line = BridgeExchange.reqString(exchange.json(), "line");
        if (line.length() > MAX_COMMAND_LENGTH) {
            throw BridgeException.invalidField("line 이 너무 깁니다 (최대 " + MAX_COMMAND_LENGTH + "자).");
        }
        if (BridgeExchange.hasControlChars(line)) {
            throw BridgeException.invalidField("line 에 줄바꿈·제어문자를 넣을 수 없습니다.");
        }
        CommandRunner runner = bridge.commandRunner();
        List<String> suggestions = bridge.callSync(() -> runner.complete(line));
        JsonArray array = new JsonArray();
        suggestions.forEach(array::add);
        JsonObject body = new JsonObject();
        body.add("suggestions", array);
        return BridgeResponse.ok(body);
    }

    /** {@code POST /commands/execute} {@code {"command"}} — 콘솔급 권한으로 실행, 출력 캡처. reload 계열은 422. */
    public BridgeResponse execute(BridgeExchange exchange) throws Exception {
        String command = CommandRunner.normalize(BridgeExchange.reqString(exchange.json(), "command"));
        if (command.isEmpty()) {
            throw BridgeException.invalidField("command 가 비어 있습니다.");
        }
        if (command.length() > MAX_COMMAND_LENGTH) {
            throw BridgeException.invalidField("command 가 너무 깁니다 (최대 " + MAX_COMMAND_LENGTH + "자).");
        }
        if (BridgeExchange.hasControlChars(command)) {
            throw BridgeException.invalidField("command 에 줄바꿈·제어문자를 넣을 수 없습니다.");
        }
        if (CommandRunner.isBlocked(command)) {
            throw new BridgeException(422, "blocked_command", "통로에서는 reload 계열 명령을 실행할 수 없습니다.");
        }
        String actor = exchange.actor();
        String redacted = LogRedaction.redactCommand(command, redactLabels);

        CommandRunner runner = bridge.commandRunner();
        CommandRunner.Execution execution = bridge.callSync(() -> runner.execute(command));
        long now = System.currentTimeMillis();
        if (logActivityCommands) {
            // Bukkit.dispatchCommand는 ServerCommandEvent를 내지 않으므로 활동 기록은 여기서 직접 남긴다.
            JsonObject data = new JsonObject();
            String sender = "web:" + actor;
            data.addProperty("sender", sender);
            data.addProperty("command", redacted);
            bridge.eventLog().write(EventLog.Channel.ACTIVITY, "server_command", sender, data);
        }
        List<String> output = execution.output();
        String status = execution.status(now);
        bridge.adminLog().commandExecute(actor, redacted, status, output.size());

        JsonObject body = new JsonObject();
        body.addProperty("execution_id", execution.id());
        body.addProperty("status", status);
        body.add("output", toArray(output));
        body.addProperty("captured_at", now);
        return BridgeResponse.ok(body);
    }

    /** {@code GET /commands/executions/{id}} — 늦게 온 출력 조회(10분 보관). 메인 스레드 불필요. */
    public BridgeResponse execution(BridgeExchange exchange) throws BridgeException {
        CommandRunner.Execution execution = bridge.commandRunner().get(exchange.pathParam("id"));
        if (execution == null) {
            throw BridgeException.notFound("실행 기록이 없거나 만료되었습니다.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("execution_id", execution.id());
        body.addProperty("status", execution.status(System.currentTimeMillis()));
        body.add("output", toArray(execution.output()));
        body.addProperty("updated", execution.updated());
        return BridgeResponse.ok(body);
    }

    /** 명령을 등록한 플러그인 이름. 플러그인 명령이 아니면 네임스페이스(minecraft/bukkit/paper 등), 모르면 "". */
    private static String ownerOf(Command command, String namespace, Map<String, String> pluginNames) {
        if (command instanceof PluginIdentifiableCommand identifiable && identifiable.getPlugin() != null) {
            return identifiable.getPlugin().getName();
        }
        if (namespace == null || namespace.isEmpty()) {
            return "";
        }
        return pluginNames.getOrDefault(namespace.toLowerCase(Locale.ROOT), namespace);
    }

    private static JsonArray toArray(List<String> lines) {
        JsonArray array = new JsonArray();
        lines.forEach(array::add);
        return array;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
