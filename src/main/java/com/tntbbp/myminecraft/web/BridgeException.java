package com.tntbbp.myminecraft.web;

/**
 * 핸들러가 던지면 통로가 {@code {"error": {"code", "message"}}} 응답으로 바꿔 보내는 예외.
 * 에러 코드는 gn-admin {@code docs/api-bridge.md} §1.4 표의 값만 쓴다.
 */
public class BridgeException extends Exception {

    private final int status;
    private final String code;

    public BridgeException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** 400 bad_request — 본문/쿼리 형식 오류(JSON 아님, 필드 타입 오류 등). */
    public static BridgeException badRequest(String message) {
        return new BridgeException(400, "bad_request", message);
    }

    /** 422 invalid_field — 필드 값이 규칙 위반(범위·형식). message에 어느 필드인지 적는다. */
    public static BridgeException invalidField(String message) {
        return new BridgeException(422, "invalid_field", message);
    }

    /** 404 not_found. */
    public static BridgeException notFound(String message) {
        return new BridgeException(404, "not_found", message);
    }

    /** 409 conflict — 일반 충돌(중복 이름, 이미 진행 중 등). */
    public static BridgeException conflict(String message) {
        return new BridgeException(409, "conflict", message);
    }
}
