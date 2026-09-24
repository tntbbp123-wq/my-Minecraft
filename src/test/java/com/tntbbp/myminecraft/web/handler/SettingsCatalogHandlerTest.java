package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.web.RouteTable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code GET /settings-catalog} 라우트·응답 JSON 모양 테스트(api-bridge.md §2.14). Bukkit 없이 순수 변환만 검증한다. */
class SettingsCatalogHandlerTest {

    @Test
    void routeIsRegisteredAsReadOnlyGet() {
        RouteTable<String> table = new RouteTable<>();
        table.add("GET", "/plugins/runtime", false, "plugins-runtime");
        table.add("GET", "/settings-catalog", false, "settings-catalog");

        RouteTable.Match<String> match = table.match("GET", "/settings-catalog");
        assertEquals("settings-catalog", match.route().handler());
        assertFalse(match.route().write());
        assertEquals(null, table.match("POST", "/settings-catalog"));
    }

    @Test
    void toJsonProducesContractShape() {
        byte[] bytes = "version: 1\n# 한국어\n".getBytes(StandardCharsets.UTF_8);
        JsonObject body = SettingsCatalogHandler.toJson("MyMinecraft", "1.1.32", bytes);

        assertEquals("MyMinecraft", body.get("plugin").getAsString());
        assertEquals("1.1.32", body.get("plugin_version").getAsString());
        assertEquals("admin-settings-catalog.yml", body.get("resource").getAsString());
        assertEquals("yaml", body.get("format").getAsString());
        assertEquals(bytes.length, body.get("size_bytes").getAsInt());
        assertEquals("version: 1\n# 한국어\n", body.get("content").getAsString());
        assertEquals(SettingsCatalogHandler.sha256Hex(bytes), body.get("sha256").getAsString());
        assertEquals(7, body.size(), "계약에 없는 필드가 생기면 api-bridge.md 도 같이 고쳐야 한다");
    }

    @Test
    void sha256IsLowercaseHex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                SettingsCatalogHandler.sha256Hex("abc".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void catalogResourceIsPackaged() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(SettingsCatalogHandler.RESOURCE)) {
            assertNotNull(stream, "jar 에 들어갈 " + SettingsCatalogHandler.RESOURCE + " 가 src/main/resources 에 있어야 한다");
            assertTrue(stream.readAllBytes().length > 0);
        }
    }
}
