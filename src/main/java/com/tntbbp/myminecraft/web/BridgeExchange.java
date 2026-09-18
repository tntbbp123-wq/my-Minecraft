package com.tntbbp.myminecraft.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 통로 요청 하나에 대한 JSON 입출력 공용 헬퍼. 헤더({@code X-GN-Actor}, {@code X-Request-Id}) 파싱,
 * 본문 크기 제한(초과 시 413 payload_too_large), 경로 변수·쿼리 조회, JSON 필드 검사를 제공한다.
 */
public final class BridgeExchange {

    public static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    public static final String HEADER_ACTOR = "X-GN-Actor";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_REPLAY = "X-Bridge-Idempotent-Replay";
    public static final String UNKNOWN_ACTOR = "unknown";

    private static final int MAX_ACTOR_LENGTH = 64;
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    private final HttpExchange exchange;
    private final String path;
    private final Map<String, String> pathParams;
    private final Map<String, String> query;
    private final int maxBodyBytes;
    private byte[] body;
    private JsonObject json;

    public BridgeExchange(HttpExchange exchange, String path, Map<String, String> pathParams, int maxBodyBytes) {
        this.exchange = exchange;
        this.path = path;
        this.pathParams = pathParams == null ? Map.of() : pathParams;
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
        this.maxBodyBytes = maxBodyBytes;
    }

    public String method() {
        return exchange.getRequestMethod();
    }

    /** {@code /v1}을 뗀 경로 (예: {@code /players/online}). */
    public String path() {
        return path;
    }

    /** 경로 변수 (예: {@code /players/{uuid}/profile}의 {@code uuid}). */
    public String pathParam(String name) {
        return pathParams.get(name);
    }

    /** 쿼리 값. 없으면 null. */
    public String query(String name) {
        return query.get(name);
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    /** {@code X-GN-Actor} (웹 로그인 사용자). 없으면 {@code "unknown"}. */
    public String actor() {
        return sanitizeActor(header(HEADER_ACTOR));
    }

    /** {@code X-Request-Id}. 없거나 비었으면 null. */
    public String requestId() throws BridgeException {
        return normalizeRequestId(header(HEADER_REQUEST_ID));
    }

    /** 요청 본문. {@code max-body-bytes}를 넘으면 413 payload_too_large. */
    public byte[] body() throws BridgeException, IOException {
        if (body != null) {
            return body;
        }
        String declared = header("Content-Length");
        if (declared != null) {
            try {
                if (Long.parseLong(declared.trim()) > maxBodyBytes) {
                    throw payloadTooLarge();
                }
            } catch (NumberFormatException ignored) {
                // 아래에서 실제로 읽으며 확인한다
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream in = exchange.getRequestBody()) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > maxBodyBytes) {
                    throw payloadTooLarge();
                }
            }
        }
        body = out.toByteArray();
        return body;
    }

    /** 본문을 JSON 객체로 읽는다. 비어 있으면 빈 객체. JSON 객체가 아니면 400 bad_request. */
    public JsonObject json() throws BridgeException, IOException {
        if (json != null) {
            return json;
        }
        String text = new String(body(), StandardCharsets.UTF_8).strip();
        if (text.isEmpty()) {
            json = new JsonObject();
            return json;
        }
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw BridgeException.badRequest("요청 본문은 JSON 객체여야 합니다.");
            }
            json = parsed.getAsJsonObject();
            return json;
        } catch (JsonParseException e) {
            throw BridgeException.badRequest("요청 본문이 올바른 JSON이 아닙니다.");
        }
    }

    /** 응답을 보낸다. 본문은 UTF-8 JSON. */
    public void send(int status, String bodyText, Map<String, String> extraHeaders) throws IOException {
        sendRaw(exchange, status, bodyText, extraHeaders);
    }

    static void sendRaw(HttpExchange exchange, int status, String bodyText, Map<String, String> extraHeaders)
            throws IOException {
        byte[] bytes = (bodyText == null ? "{}" : bodyText).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (extraHeaders != null) {
            for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
                exchange.getResponseHeaders().set(header.getKey(), header.getValue());
            }
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private BridgeException payloadTooLarge() {
        return new BridgeException(413, "payload_too_large",
                "요청 본문이 너무 큽니다 (최대 " + maxBodyBytes + " bytes).");
    }

    // ---------------------------------------------------------------- 순수 헬퍼

    /** X-GN-Actor 값 정리: 제어문자 제거, 앞뒤 공백 제거, 최대 64자. 비면 {@code "unknown"}. */
    public static String sanitizeActor(String raw) {
        if (raw == null) {
            return UNKNOWN_ACTOR;
        }
        StringBuilder cleaned = new StringBuilder();
        raw.codePoints()
                .filter(cp -> !Character.isISOControl(cp))
                .forEach(cleaned::appendCodePoint);
        String value = cleaned.toString().strip();
        if (value.isEmpty()) {
            return UNKNOWN_ACTOR;
        }
        if (value.codePointCount(0, value.length()) > MAX_ACTOR_LENGTH) {
            value = value.substring(0, value.offsetByCodePoints(0, MAX_ACTOR_LENGTH));
        }
        return value;
    }

    /** X-Request-Id 정리. 없거나 비었으면 null, 너무 길거나 제어문자가 있으면 400 bad_request. */
    public static String normalizeRequestId(String raw) throws BridgeException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (value.length() > MAX_REQUEST_ID_LENGTH || hasControlChars(value)) {
            throw BridgeException.badRequest("X-Request-Id 형식이 올바르지 않습니다.");
        }
        return value;
    }

    /** 개행·탭 등 제어문자(널 포함)가 있는지. */
    public static boolean hasControlChars(String value) {
        if (value == null) {
            return false;
        }
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            try {
                result.putIfAbsent(URLDecoder.decode(key, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {
                // 잘못 인코딩된 쿼리 조각은 무시
            }
        }
        return result;
    }

    /** 문자열 필드. 없거나 null이면 null, 문자열이 아니면 400 bad_request. */
    public static String optString(JsonObject object, String key) throws BridgeException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw BridgeException.badRequest("'" + key + "' 는 문자열이어야 합니다.");
        }
        return element.getAsString();
    }

    /** 필수 문자열 필드. 없으면 400 bad_request. */
    public static String reqString(JsonObject object, String key) throws BridgeException {
        String value = optString(object, key);
        if (value == null) {
            throw BridgeException.badRequest("'" + key + "' 가 필요합니다.");
        }
        return value;
    }

    /** 정수 필드. 없거나 null이면 null, 정수가 아니면 400 bad_request. */
    public static Long optLong(JsonObject object, String key) throws BridgeException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw BridgeException.badRequest("'" + key + "' 는 정수여야 합니다.");
        }
        try {
            return element.getAsJsonPrimitive().getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw BridgeException.badRequest("'" + key + "' 는 정수여야 합니다.");
        }
    }

    /** 정수 필드(int 범위). 없거나 null이면 null. */
    public static Integer optInt(JsonObject object, String key) throws BridgeException {
        Long value = optLong(object, key);
        if (value == null) {
            return null;
        }
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw BridgeException.invalidField("'" + key + "' 값이 너무 큽니다.");
        }
        return value.intValue();
    }

    /** 실수 필드. 없거나 null이면 null, 숫자가 아니면 400 bad_request. */
    public static Double optDouble(JsonObject object, String key) throws BridgeException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw BridgeException.badRequest("'" + key + "' 는 숫자여야 합니다.");
        }
        double value = element.getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw BridgeException.badRequest("'" + key + "' 는 숫자여야 합니다.");
        }
        return value;
    }

    /** 참/거짓 필드. 없거나 null이면 null, bool이 아니면 400 bad_request. */
    public static Boolean optBoolean(JsonObject object, String key) throws BridgeException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !((JsonPrimitive) element).isBoolean()) {
            throw BridgeException.badRequest("'" + key + "' 는 true/false 여야 합니다.");
        }
        return element.getAsBoolean();
    }
}
