package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.web.RouteTable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 플러그인 목록 라우트 등록·응답 JSON 모양 테스트. Bukkit 목이 없어 순수 변환({@link PluginHandler#toJson})만 검증한다. */
class PluginHandlerHelpersTest {

    @Test
    void routeIsRegisteredAsReadOnlyGet() {
        RouteTable<String> table = new RouteTable<>();
        table.add("GET", "/catalog/{kind}", false, "catalog");
        table.add("GET", "/plugins/runtime", false, "plugins-runtime");

        RouteTable.Match<String> match = table.match("GET", "/plugins/runtime");
        assertEquals("plugins-runtime", match.route().handler());
        assertFalse(match.route().write(), "GET 라우트는 멱등 캐시 대상(write)이 아니어야 한다");
        assertEquals(null, table.match("POST", "/plugins/runtime"), "POST로는 매치되지 않아야 한다");
    }

    @Test
    void toJsonSortsByNameAndProducesContractShape() {
        PluginHandler.PluginInfo b = new PluginHandler.PluginInfo(
                "BPlugin", "2.0", true, "1.20", List.of("A"), List.of());
        PluginHandler.PluginInfo a = new PluginHandler.PluginInfo(
                "APlugin", "1.0", false, "1.20", List.of(), List.of("BPlugin", "CPlugin"));

        JsonObject body = PluginHandler.toJson(List.of(b, a));
        JsonArray plugins = body.getAsJsonArray("plugins");
        assertEquals(2, plugins.size());

        JsonObject first = plugins.get(0).getAsJsonObject();
        assertEquals("APlugin", first.get("name").getAsString(), "이름순 정렬이어야 한다");
        assertEquals("1.0", first.get("version").getAsString());
        assertFalse(first.get("enabled").getAsBoolean());
        assertEquals("1.20", first.get("api_version").getAsString());
        assertEquals(Set.of("name", "version", "enabled", "api_version", "depend", "softdepend"), first.keySet());
        assertTrue(first.getAsJsonArray("depend").isEmpty());
        assertEquals(2, first.getAsJsonArray("softdepend").size());
        assertEquals("BPlugin", first.getAsJsonArray("softdepend").get(0).getAsString());

        JsonObject second = plugins.get(1).getAsJsonObject();
        assertEquals("BPlugin", second.get("name").getAsString());
        assertTrue(second.get("enabled").getAsBoolean());
        assertEquals(1, second.getAsJsonArray("depend").size());
    }

    @Test
    void toJsonHandlesNullApiVersion() {
        PluginHandler.PluginInfo info = new PluginHandler.PluginInfo(
                "OldPlugin", "1.0", true, null, List.of(), List.of());
        JsonObject body = PluginHandler.toJson(List.of(info));
        JsonObject entry = body.getAsJsonArray("plugins").get(0).getAsJsonObject();
        assertTrue(entry.get("api_version").isJsonNull());
    }
}
