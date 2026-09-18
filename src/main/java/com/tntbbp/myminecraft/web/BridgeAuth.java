package com.tntbbp.myminecraft.web;

import com.sun.net.httpserver.HttpExchange;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * {@code Authorization: Bearer <token>} 검사. 토큰은 {@code SecretResolver}로 {@code web-bridge.token}을 푼 값이다.
 * 비교는 상수 시간으로 한다: 두 값을 SHA-256으로 같은 길이로 만든 뒤 {@link MessageDigest#isEqual}로 비교해서
 * 길이·앞부분 일치 여부가 응답 시간으로 새지 않게 한다. 토큰 값은 어디에도 출력하지 않는다.
 */
public final class BridgeAuth {

    private static final String BEARER = "bearer ";

    private final byte[] expectedDigest;

    public BridgeAuth(String token) {
        this.expectedDigest = token == null || token.isEmpty() ? null : sha256(token);
    }

    /** 토큰이 설정돼 있는지. 없으면 모든 요청이 거부된다. */
    public boolean configured() {
        return expectedDigest != null;
    }

    public boolean check(HttpExchange exchange) {
        return matches(exchange.getRequestHeaders().getFirst("Authorization"));
    }

    /** Authorization 헤더 값이 맞는 Bearer 토큰인지. 순수 함수(테스트용). */
    public boolean matches(String authorizationHeader) {
        if (expectedDigest == null || authorizationHeader == null) {
            return false;
        }
        String header = authorizationHeader.strip();
        if (header.length() <= BEARER.length()
                || !header.substring(0, BEARER.length()).toLowerCase(Locale.ROOT).equals(BEARER)) {
            return false;
        }
        String presented = header.substring(BEARER.length()).strip();
        if (presented.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(sha256(presented), expectedDigest);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }
}
