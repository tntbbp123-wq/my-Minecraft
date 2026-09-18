package com.tntbbp.myminecraft.web;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * 쓰기 요청의 {@code X-Request-Id → (HTTP 상태, 본문)} 보관소 (api-bridge.md §1.5).
 * 같은 ID가 다시 오면 저장된 응답을 그대로 돌려주고 재실행하지 않는다. 처리 중인 ID가 또 오면
 * {@code 409 duplicate_in_flight}. TTL이 지나거나 개수 상한을 넘으면 오래된 것부터 버린다.
 * Bukkit에 의존하지 않는 순수 클래스.
 */
public final class IdempotencyCache {

    public record Stored(int status, String body, long storedAt) {
    }

    /** {@link #lookupOrBegin} 결과. */
    public enum State {
        /** 처음 보는 ID — 처리 중으로 표시했다. 끝나면 {@link #put}/{@link #end}를 부른다. */
        STARTED,
        /** 이미 처리 중인 ID. */
        IN_FLIGHT,
        /** 저장된 응답이 있다. */
        CACHED
    }

    public record Lookup(State state, Stored stored) {
    }

    private final long ttlMillis;
    private final int maxEntries;
    private final LongSupplier clock;
    private final LinkedHashMap<String, Stored> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> inFlight = new HashSet<>();

    public IdempotencyCache(long ttlMillis, int maxEntries, LongSupplier clock) {
        this.ttlMillis = ttlMillis;
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
    }

    /** 멱등 캐시에 저장할 응답인지: 2xx와 확정 4xx(400/404/409/422)만. 인프라성 실패는 재시도할 수 있게 저장하지 않는다. */
    public static boolean isCacheable(int status) {
        return (status >= 200 && status < 300) || status == 400 || status == 404 || status == 409 || status == 422;
    }

    public synchronized Optional<Stored> get(String requestId) {
        purgeExpired();
        return Optional.ofNullable(entries.get(requestId));
    }

    /** 저장된 응답이 있으면 CACHED, 처리 중이면 IN_FLIGHT, 아니면 처리 중으로 표시하고 STARTED. 한 번에(원자적으로) 판정한다. */
    public synchronized Lookup lookupOrBegin(String requestId) {
        purgeExpired();
        Stored stored = entries.get(requestId);
        if (stored != null) {
            return new Lookup(State.CACHED, stored);
        }
        if (!inFlight.add(requestId)) {
            return new Lookup(State.IN_FLIGHT, null);
        }
        return new Lookup(State.STARTED, null);
    }

    /** 처리 중 표시. 이미 처리 중이면 false. */
    public synchronized boolean begin(String requestId) {
        return inFlight.add(requestId);
    }

    /** 처리 중 표시를 푼다 (저장 여부와 무관하게 항상 부른다). */
    public synchronized void end(String requestId) {
        inFlight.remove(requestId);
    }

    public synchronized void put(String requestId, int status, String body) {
        entries.put(requestId, new Stored(status, body, clock.getAsLong()));
        while (entries.size() > maxEntries) {
            Iterator<String> eldest = entries.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    public synchronized int size() {
        purgeExpired();
        return entries.size();
    }

    private void purgeExpired() {
        long cutoff = clock.getAsLong() - ttlMillis;
        Iterator<Map.Entry<String, Stored>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().storedAt() < cutoff) {
                iterator.remove();
            }
        }
    }
}
