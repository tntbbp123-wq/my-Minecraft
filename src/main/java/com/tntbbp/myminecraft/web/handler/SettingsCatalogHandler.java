package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.web.BridgeException;
import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.WebBridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.logging.Level;

/**
 * 웹 관리 "서버 설정" 화면용 설정 카탈로그 {@code GET /settings-catalog} (api-bridge.md §2.14).
 *
 * <p>jar 안의 {@value #RESOURCE}(config.yml 키별 한국어 이름·설명·형식) 원문을 그대로 돌려준다.
 * 파일 형식 검증은 웹 쪽(gn-admin 카탈로그 로더)이 한다. 서버가 켜져 있는 동안 jar 파일이 새 버전으로
 * 바뀌어도(다음 재시작 때 적용) 지금 돌고 있는 버전의 카탈로그를 주도록, 통로를 열 때 한 번만 읽어 둔다.
 */
public class SettingsCatalogHandler {

    public static final String RESOURCE = "admin-settings-catalog.yml";

    private final WebBridge bridge;
    /** 통로를 열 때 읽은 원문. jar에 파일이 없거나 읽기에 실패했으면 null. */
    private final byte[] content;

    public SettingsCatalogHandler(WebBridge bridge) {
        this.bridge = bridge;
        this.content = readResource(bridge);
    }

    private static byte[] readResource(WebBridge bridge) {
        try (InputStream stream = bridge.plugin().getResource(RESOURCE)) {
            if (stream == null) {
                bridge.plugin().getLogger().warning("jar 안에 " + RESOURCE
                        + " 가 없어 웹 관리 설정 화면에 한국어 설명이 나오지 않습니다.");
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            bridge.plugin().getLogger().log(Level.WARNING, RESOURCE + " 를 읽지 못했습니다", e);
            return null;
        }
    }

    /** {@code GET /settings-catalog}. 메인 스레드가 필요 없다. */
    public BridgeResponse catalog(BridgeExchange exchange) throws BridgeException {
        if (content == null) {
            throw new BridgeException(404, "settings_catalog_missing",
                    "이 플러그인 jar에는 설정 카탈로그(" + RESOURCE + ")가 없습니다.");
        }
        return BridgeResponse.ok(toJson(bridge.plugin().getPluginMeta().getName(),
                bridge.plugin().getPluginMeta().getVersion(), content));
    }

    /** Bukkit 없이 테스트 가능한 순수 변환. */
    static JsonObject toJson(String pluginName, String pluginVersion, byte[] bytes) {
        JsonObject body = new JsonObject();
        body.addProperty("plugin", pluginName);
        body.addProperty("plugin_version", pluginVersion);
        body.addProperty("resource", RESOURCE);
        body.addProperty("format", "yaml");
        body.addProperty("sha256", sha256Hex(bytes));
        body.addProperty("size_bytes", bytes.length);
        body.addProperty("content", new String(bytes, StandardCharsets.UTF_8));
        return body;
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);
        }
    }
}
