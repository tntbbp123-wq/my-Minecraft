package com.tntbbp.myminecraft.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeExchangeTest {

    @Test
    void sanitizesActor() {
        assertEquals("unknown", BridgeExchange.sanitizeActor(null));
        assertEquals("unknown", BridgeExchange.sanitizeActor("   "));
        assertEquals("snrnsrk9901", BridgeExchange.sanitizeActor(" snrnsrk9901\n"));
        assertEquals("a".repeat(64), BridgeExchange.sanitizeActor("a".repeat(100)));
    }

    @Test
    void normalizesRequestId() throws BridgeException {
        assertNull(BridgeExchange.normalizeRequestId(null));
        assertNull(BridgeExchange.normalizeRequestId("  "));
        assertEquals("abc", BridgeExchange.normalizeRequestId(" abc "));
        BridgeException tooLong = assertThrows(BridgeException.class,
                () -> BridgeExchange.normalizeRequestId("x".repeat(129)));
        assertEquals(400, tooLong.status());
        assertThrows(BridgeException.class, () -> BridgeExchange.normalizeRequestId("a\u0000b"));
    }

    @Test
    void detectsControlChars() {
        assertTrue(BridgeExchange.hasControlChars("a\nb"));
        assertTrue(BridgeExchange.hasControlChars("a\tb"));
        assertFalse(BridgeExchange.hasControlChars("공지 테스트 123"));
        assertFalse(BridgeExchange.hasControlChars(null));
    }

    @Test
    void parsesQuery() {
        Map<String, String> query = BridgeExchange.parseQuery("name=Gonk%20or&limit=200&cursor=&flag&name=dup");
        assertEquals("Gonk or", query.get("name"));
        assertEquals("200", query.get("limit"));
        assertEquals("", query.get("cursor"));
        assertEquals("", query.get("flag"));
        assertTrue(BridgeExchange.parseQuery(null).isEmpty());
    }

    @Test
    void jsonFieldTypeChecks() throws BridgeException {
        JsonObject body = JsonParser.parseString(
                "{\"s\":\"x\",\"n\":60,\"f\":1.5,\"b\":true,\"z\":null,\"big\":10000000000}").getAsJsonObject();
        assertEquals("x", BridgeExchange.optString(body, "s"));
        assertNull(BridgeExchange.optString(body, "missing"));
        assertNull(BridgeExchange.optString(body, "z"));
        assertEquals(60, BridgeExchange.optInt(body, "n"));
        assertEquals(10_000_000_000L, BridgeExchange.optLong(body, "big"));
        assertEquals(1.5, BridgeExchange.optDouble(body, "f"));
        assertEquals(Boolean.TRUE, BridgeExchange.optBoolean(body, "b"));

        assertEquals(400, assertThrows(BridgeException.class, () -> BridgeExchange.optString(body, "n")).status());
        assertEquals(400, assertThrows(BridgeException.class, () -> BridgeExchange.optInt(body, "f")).status());
        assertEquals(400, assertThrows(BridgeException.class, () -> BridgeExchange.optInt(body, "s")).status());
        assertEquals(422, assertThrows(BridgeException.class, () -> BridgeExchange.optInt(body, "big")).status());
        assertEquals(400, assertThrows(BridgeException.class, () -> BridgeExchange.reqString(body, "missing")).status());
        assertEquals(400, assertThrows(BridgeException.class, () -> BridgeExchange.optBoolean(body, "s")).status());
    }

    @Test
    void errorBodyShape() {
        assertEquals("{\"error\":{\"code\":\"not_found\",\"message\":\"없음\"}}",
                BridgeExchange.GSON.toJson(BridgeResponse.errorBody("not_found", "없음")));
        BridgeResponse stub = BridgeResponse.notImplemented();
        assertEquals(501, stub.status());
        assertFalse(IdempotencyCache.isCacheable(stub.status()));
    }
}
