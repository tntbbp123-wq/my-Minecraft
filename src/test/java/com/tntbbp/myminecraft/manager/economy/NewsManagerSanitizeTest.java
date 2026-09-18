package com.tntbbp.myminecraft.manager.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 뉴스 본문에서 퍼센트·등락 표현을 걸러내는 규칙 회귀 테스트. */
class NewsManagerSanitizeTest {

    @Test
    void removesPercentAndDirectionWords() {
        assertEquals("금광 예상", NewsManager.sanitizeContent("금광 10% 상승 예상"));
        assertEquals("금광 대형 광맥 발견", NewsManager.sanitizeContent("금광 대형 광맥 발견 +5.5 %"));
        assertEquals("주가 전망", NewsManager.sanitizeContent("주가 하락 전망"));
    }

    @Test
    void keepsPlainContent() {
        assertEquals("금광에서 대형 광맥 발견", NewsManager.sanitizeContent("  금광에서 대형 광맥 발견  "));
    }
}
