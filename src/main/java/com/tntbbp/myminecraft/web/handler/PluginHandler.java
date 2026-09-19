package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.WebBridge;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 설치된 플러그인 실행 상태 조회 {@code GET /plugins/runtime} (api-bridge.md §2.13).
 * 웹 관리 P5 플러그인 화면의 로드 실패 표시용.
 */
public class PluginHandler {

    /** Bukkit {@link Plugin}에서 뽑은 순수 데이터. Bukkit 없이 JSON 변환을 테스트하기 위해 분리했다. */
    public record PluginInfo(String name, String version, boolean enabled, String apiVersion,
                              List<String> depend, List<String> softDepend) {
    }

    private final WebBridge bridge;

    public PluginHandler(WebBridge bridge) {
        this.bridge = bridge;
    }

    /** {@code GET /plugins/runtime} — 이름순 정렬. */
    public BridgeResponse runtime(BridgeExchange exchange) throws Exception {
        List<PluginInfo> infos = bridge.callSync(() -> {
            List<PluginInfo> result = new ArrayList<>();
            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                result.add(toPluginInfo(plugin));
            }
            return result;
        });
        return BridgeResponse.ok(toJson(infos));
    }

    private static PluginInfo toPluginInfo(Plugin plugin) {
        PluginMeta meta = plugin.getPluginMeta();
        return new PluginInfo(meta.getName(), meta.getVersion(), plugin.isEnabled(), meta.getAPIVersion(),
                List.copyOf(meta.getPluginDependencies()), List.copyOf(meta.getPluginSoftDependencies()));
    }

    /** Bukkit 없이 테스트 가능한 순수 변환. 이름순으로 정렬해 돌려준다. */
    static JsonObject toJson(List<PluginInfo> infos) {
        List<PluginInfo> sorted = new ArrayList<>(infos);
        sorted.sort(Comparator.comparing(PluginInfo::name));
        JsonArray plugins = new JsonArray();
        for (PluginInfo info : sorted) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", info.name());
            entry.addProperty("version", info.version());
            entry.addProperty("enabled", info.enabled());
            entry.addProperty("api_version", info.apiVersion());
            JsonArray depend = new JsonArray();
            info.depend().forEach(depend::add);
            entry.add("depend", depend);
            JsonArray softdepend = new JsonArray();
            info.softDepend().forEach(softdepend::add);
            entry.add("softdepend", softdepend);
            plugins.add(entry);
        }
        JsonObject body = new JsonObject();
        body.add("plugins", plugins);
        return body;
    }
}
