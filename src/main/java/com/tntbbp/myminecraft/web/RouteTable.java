package com.tntbbp.myminecraft.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 메서드 + 경로 패턴({@code /players/{uuid}/profile} 처럼 {@code {이름}}이 변수)으로 핸들러를 찾는 표.
 * 여러 패턴이 맞으면 고정 조각이 더 많은 쪽을 고른다(예: {@code /news/ai-draft}가 {@code /news/{id}}보다 우선).
 * Bukkit에 의존하지 않는 순수 클래스.
 */
public final class RouteTable<H> {

    /** 등록된 라우트. {@code write}면 X-Request-Id 필수 + 멱등 캐시 대상. */
    public record Route<H>(String method, String pattern, List<String> segments, boolean write, H handler) {

        int literalCount() {
            int count = 0;
            for (String segment : segments) {
                if (!isVariable(segment)) {
                    count++;
                }
            }
            return count;
        }
    }

    public record Match<H>(Route<H> route, Map<String, String> params) {
    }

    private final List<Route<H>> routes = new ArrayList<>();

    /**
     * 라우트를 등록한다.
     *
     * @param readOnly POST라도 상태를 바꾸지 않는 요청이면 true (예: {@code POST /commands/complete}).
     *                 GET은 항상 읽기로 본다.
     */
    public void add(String method, String pattern, boolean readOnly, H handler) {
        String upper = method.toUpperCase(Locale.ROOT);
        boolean write = !readOnly && !upper.equals("GET");
        routes.add(new Route<>(upper, pattern, split(pattern), write, handler));
    }

    public List<Route<H>> routes() {
        return Collections.unmodifiableList(routes);
    }

    /** 맞는 라우트가 없으면 null. {@code path}는 {@code /v1}을 뗀, 이미 디코딩된 경로. */
    public Match<H> match(String method, String path) {
        String upper = method == null ? "" : method.toUpperCase(Locale.ROOT);
        List<String> parts = split(path);
        Match<H> best = null;
        int bestLiterals = -1;
        for (Route<H> route : routes) {
            if (!route.method().equals(upper) || route.segments().size() != parts.size()) {
                continue;
            }
            Map<String, String> params = bind(route.segments(), parts);
            if (params == null) {
                continue;
            }
            int literals = route.literalCount();
            if (literals > bestLiterals) {
                best = new Match<>(route, params);
                bestLiterals = literals;
            }
        }
        return best;
    }

    private static Map<String, String> bind(List<String> pattern, List<String> parts) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < pattern.size(); i++) {
            String expected = pattern.get(i);
            String actual = parts.get(i);
            if (isVariable(expected)) {
                if (actual.isEmpty()) {
                    return null;
                }
                params.put(expected.substring(1, expected.length() - 1), actual);
            } else if (!expected.equals(actual)) {
                return null;
            }
        }
        return params;
    }

    private static boolean isVariable(String segment) {
        return segment.length() > 2 && segment.startsWith("{") && segment.endsWith("}");
    }

    static List<String> split(String path) {
        List<String> parts = new ArrayList<>();
        if (path == null) {
            return parts;
        }
        for (String part : path.split("/", -1)) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }
}
