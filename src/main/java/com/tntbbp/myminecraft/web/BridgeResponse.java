package com.tntbbp.myminecraft.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** 핸들러가 돌려주는 응답(HTTP 상태 + JSON 본문). 쓰기 요청이면 통로가 멱등 캐시에 그대로 저장한다. */
public record BridgeResponse(int status, JsonElement body) {

    public static BridgeResponse ok(JsonElement body) {
        return new BridgeResponse(200, body);
    }

    /** {@code {"ok": true}}. */
    public static BridgeResponse okTrue() {
        JsonObject body = new JsonObject();
        body.addProperty("ok", true);
        return ok(body);
    }

    public static BridgeResponse error(int status, String code, String message) {
        return new BridgeResponse(status, errorBody(code, message));
    }

    /** 아직 구현되지 않은 라우트(P2/P3 스텁)용 응답. 멱등 캐시에 저장되지 않는 501을 쓴다. */
    public static BridgeResponse notImplemented() {
        return error(501, "not_found", "아직 구현되지 않은 기능입니다.");
    }

    public static JsonObject errorBody(String code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        JsonObject body = new JsonObject();
        body.add("error", error);
        return body;
    }
}
