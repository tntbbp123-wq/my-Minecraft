package com.tntbbp.myminecraft.web;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyCacheTest {

    private final AtomicLong clock = new AtomicLong(1_000_000L);

    @Test
    void replaysStoredResponse() {
        IdempotencyCache cache = new IdempotencyCache(600_000L, 10, clock::get);
        assertEquals(IdempotencyCache.State.STARTED, cache.lookupOrBegin("a").state());
        cache.put("a", 200, "{\"ok\":true}");
        cache.end("a");

        IdempotencyCache.Lookup again = cache.lookupOrBegin("a");
        assertEquals(IdempotencyCache.State.CACHED, again.state());
        assertEquals(200, again.stored().status());
        assertEquals("{\"ok\":true}", again.stored().body());
    }

    @Test
    void reportsInFlightUntilEnded() {
        IdempotencyCache cache = new IdempotencyCache(600_000L, 10, clock::get);
        assertEquals(IdempotencyCache.State.STARTED, cache.lookupOrBegin("a").state());
        assertEquals(IdempotencyCache.State.IN_FLIGHT, cache.lookupOrBegin("a").state());
        cache.end("a");
        // 저장하지 않고 끝났으면(504 등) 다시 실행할 수 있어야 한다
        assertEquals(IdempotencyCache.State.STARTED, cache.lookupOrBegin("a").state());
    }

    @Test
    void beginAndGetBasics() {
        IdempotencyCache cache = new IdempotencyCache(600_000L, 10, clock::get);
        assertTrue(cache.begin("x"));
        assertFalse(cache.begin("x"));
        cache.end("x");
        assertTrue(cache.get("x").isEmpty());
        cache.put("x", 422, "{}");
        assertEquals(422, cache.get("x").orElseThrow().status());
    }

    @Test
    void expiresAfterTtl() {
        IdempotencyCache cache = new IdempotencyCache(600_000L, 10, clock::get);
        cache.put("a", 200, "{}");
        clock.addAndGet(599_999L);
        assertTrue(cache.get("a").isPresent());
        clock.addAndGet(2L);
        assertTrue(cache.get("a").isEmpty());
        assertEquals(IdempotencyCache.State.STARTED, cache.lookupOrBegin("a").state());
    }

    @Test
    void evictsOldestBeyondCapacity() {
        IdempotencyCache cache = new IdempotencyCache(600_000L, 3, clock::get);
        cache.put("a", 200, "a");
        cache.put("b", 200, "b");
        cache.put("c", 200, "c");
        cache.get("a"); // 최근에 쓴 것은 남는다(LRU)
        cache.put("d", 200, "d");
        assertEquals(3, cache.size());
        assertTrue(cache.get("a").isPresent());
        assertTrue(cache.get("b").isEmpty());
        assertTrue(cache.get("d").isPresent());
    }

    @Test
    void cacheableStatuses() {
        assertTrue(IdempotencyCache.isCacheable(200));
        assertTrue(IdempotencyCache.isCacheable(201));
        assertTrue(IdempotencyCache.isCacheable(400));
        assertTrue(IdempotencyCache.isCacheable(404));
        assertTrue(IdempotencyCache.isCacheable(409));
        assertTrue(IdempotencyCache.isCacheable(422));
        assertFalse(IdempotencyCache.isCacheable(401));
        assertFalse(IdempotencyCache.isCacheable(413));
        assertFalse(IdempotencyCache.isCacheable(429));
        assertFalse(IdempotencyCache.isCacheable(500));
        assertFalse(IdempotencyCache.isCacheable(501));
        assertFalse(IdempotencyCache.isCacheable(503));
        assertFalse(IdempotencyCache.isCacheable(504));
    }
}
