package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.WebBridge;

/** {@code GET /health} — 통로 도달·인증 확인. 메인 스레드가 필요 없다(서버 준비 전·종료 중에도 응답). */
public class HealthHandler {

    private final WebBridge bridge;

    public HealthHandler(WebBridge bridge) {
        this.bridge = bridge;
    }

    public BridgeResponse health(BridgeExchange exchange) {
        JsonObject body = new JsonObject();
        body.addProperty("status", "ok");
        body.addProperty("plugin_version", bridge.plugin().getPluginMeta().getVersion());
        body.addProperty("uptime_ms", bridge.uptimeMs());
        body.addProperty("shutting_down", bridge.shuttingDown());
        return BridgeResponse.ok(body);
    }
}
