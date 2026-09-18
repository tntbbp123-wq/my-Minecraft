package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.manager.economy.NewsManager;
import com.tntbbp.myminecraft.web.BridgeException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 뉴스 통로 입력 검사·AI 초안 상태·뉴스 JSON 모델 테스트. */
class NewsHandlerHelpersTest {

    @Test
    void parseDirection() throws BridgeException {
        assertEquals(1, NewsHandler.parseDirection("up", true));
        assertEquals(-1, NewsHandler.parseDirection("down", true));
        assertEquals(0, NewsHandler.parseDirection(null, false));
        assertEquals(422, assertThrows(BridgeException.class, () -> NewsHandler.parseDirection("UP", true)).status());
        assertEquals(422, assertThrows(BridgeException.class, () -> NewsHandler.parseDirection(null, true)).status());
    }

    @Test
    void parseMagnitude() throws BridgeException {
        assertEquals(5.0, NewsHandler.parseMagnitude(5.0, true));
        assertEquals(0.0, NewsHandler.parseMagnitude(null, false));
        assertEquals(422, assertThrows(BridgeException.class, () -> NewsHandler.parseMagnitude(0.0, true)).status());
        assertEquals(422, assertThrows(BridgeException.class, () -> NewsHandler.parseMagnitude(-1.0, true)).status());
        assertEquals(422, assertThrows(BridgeException.class, () -> NewsHandler.parseMagnitude(null, true)).status());
    }

    @Test
    void validateContent() throws BridgeException {
        assertEquals("금광에서 대형 광맥 발견", NewsHandler.validateContent("금광에서 대형 광맥 발견", true));
        assertNull(NewsHandler.validateContent(null, false));
        assertEquals("가".repeat(256), NewsHandler.validateContent("가".repeat(256), true));
        for (String bad : new String[]{"   ", "가".repeat(257), "a\nb", "10% 상승"}) {
            assertEquals(422, assertThrows(BridgeException.class,
                    () -> NewsHandler.validateContent(bad, true)).status(), bad);
        }
        assertEquals(422, assertThrows(BridgeException.class,
                () -> NewsHandler.validateContent(null, true)).status());
    }

    @Test
    void parseNewsId() throws BridgeException {
        assertEquals(7, NewsHandler.parseNewsId("7"));
        assertEquals(7, NewsHandler.parseNewsId(" 7 "));
        assertEquals(400, assertThrows(BridgeException.class, () -> NewsHandler.parseNewsId("abc")).status());
        assertEquals(400, assertThrows(BridgeException.class, () -> NewsHandler.parseNewsId(null)).status());
    }

    @Test
    void redactErrorHidesApiKey() {
        String redacted = NewsHandler.redactError("https://x/y:generateContent?key=AIzaSECRET123&z=1 failed");
        assertTrue(redacted.contains("key=***"));
        assertFalse(redacted.contains("AIzaSECRET123"));
        assertTrue(NewsHandler.redactError("x".repeat(600)).length() <= 501);
    }

    @Test
    void draftLifecycle() {
        NewsHandler.Draft draft = new NewsHandler.Draft("d-1", 0L);
        JsonObject pending = draft.toJson();
        assertEquals("d-1", pending.get("draft_id").getAsString());
        assertEquals("pending", pending.get("status").getAsString());
        assertTrue(pending.get("draft").isJsonNull());
        assertTrue(pending.get("error").isJsonNull());

        NewsHandler.finish(draft, "금광 대형 광맥 발견 10% 상승", "up", 6.0);
        JsonObject done = draft.toJson();
        assertEquals("done", done.get("status").getAsString());
        JsonObject body = done.getAsJsonObject("draft");
        assertEquals("금광 대형 광맥 발견", body.get("content").getAsString());
        assertEquals("up", body.get("direction").getAsString());
        assertEquals(6.0, body.get("magnitude_percent").getAsDouble());
        assertTrue(done.get("error").isJsonNull());
    }

    @Test
    void draftTruncatesAndRejectsEmpty() {
        NewsHandler.Draft longDraft = new NewsHandler.Draft("d-2", 0L);
        NewsHandler.finish(longDraft, "가".repeat(300), "down", 3.0);
        assertEquals(256, longDraft.content.codePointCount(0, longDraft.content.length()));

        NewsHandler.Draft empty = new NewsHandler.Draft("d-3", 0L);
        NewsHandler.finish(empty, "", "up", 1.0);
        assertEquals("error", empty.status);
    }

    @Test
    void draftFailureIsRedacted() {
        NewsHandler.Draft draft = new NewsHandler.Draft("d-4", 0L);
        NewsHandler.fail(draft, new CompletionException(new RuntimeException("Gemini API 오류 key=SECRET")));
        assertEquals("error", draft.status);
        assertTrue(draft.error.contains("Gemini API 오류"));
        assertTrue(draft.error.contains("key=***"));
        assertFalse(draft.error.contains("SECRET"));
        assertTrue(draft.toJson().get("draft").isJsonNull());
    }

    @Test
    void newsJsonMatchesContract() {
        NewsManager.NewsView cancelled = new NewsManager.NewsView(7, "custom_금광", "금광", 5.0, "내용", true,
                "snrnsrk9901", 1L, 2L, 3L, false, false, true, 99L, "잘못 작성");
        JsonObject json = NewsHandler.newsJson(cancelled);
        assertEquals(Set.of("id", "stock_id", "stock_name", "direction", "magnitude_percent", "content", "fake",
                "author", "created_at", "reveal_at", "apply_at", "status", "revealed", "applied", "cancelled",
                "cancelled_at", "cancel_reason"), json.keySet());
        assertEquals("cancelled", json.get("status").getAsString());
        assertEquals(99L, json.get("cancelled_at").getAsLong());
        assertEquals("up", json.get("direction").getAsString());

        NewsManager.NewsView pending = new NewsManager.NewsView(8, "custom_금광", "금광", -2.0, "내용", false,
                "console", 1L, 2L, 3L, false, false, false, null, null);
        JsonObject pendingJson = NewsHandler.newsJson(pending);
        assertTrue(pendingJson.get("cancelled_at").isJsonNull());
        assertTrue(pendingJson.get("cancel_reason").isJsonNull());
        assertEquals("down", pendingJson.get("direction").getAsString());
        assertEquals(2.0, pendingJson.get("magnitude_percent").getAsDouble());
    }
}
