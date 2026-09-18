package com.tntbbp.myminecraft.manager.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 뉴스 상태 계산·수정/취소 가능 판정(공개 전 수정, 반영 전 취소) 규칙 테스트. */
class NewsStateTest {

    @Test
    void statusOf() {
        assertEquals("pending", NewsManager.statusOf(false, false, false));
        assertEquals("revealed", NewsManager.statusOf(true, false, false));
        assertEquals("applied", NewsManager.statusOf(true, true, false));
        assertEquals("cancelled", NewsManager.statusOf(false, false, true));
        assertEquals("cancelled", NewsManager.statusOf(true, false, true));
        assertEquals("cancelled", NewsManager.statusOf(true, true, true));
    }

    @Test
    void directionAndImpact() {
        assertEquals("down", NewsManager.directionOf(-0.5));
        assertEquals("up", NewsManager.directionOf(0.0));
        assertEquals("up", NewsManager.directionOf(3.0));
        assertEquals(5.0, NewsManager.impactOf(1, 5.0));
        assertEquals(-5.0, NewsManager.impactOf(-1, 5.0));
        assertEquals(-5.0, NewsManager.impactOf(-1, -5.0));
        assertEquals(5.0, NewsManager.impactOf(1, -5.0));
    }

    @Test
    void editOnlyBeforeReveal() {
        assertEquals(NewsManager.ChangeStatus.OK, NewsManager.editCheck(false, false, false));
        assertEquals(NewsManager.ChangeStatus.ALREADY_REVEALED, NewsManager.editCheck(true, false, false));
        assertEquals(NewsManager.ChangeStatus.ALREADY_APPLIED, NewsManager.editCheck(true, true, false));
        assertEquals(NewsManager.ChangeStatus.ALREADY_CANCELLED, NewsManager.editCheck(false, false, true));
    }

    @Test
    void cancelOnlyBeforeApply() {
        assertEquals(NewsManager.ChangeStatus.OK, NewsManager.cancelCheck(false, false));
        assertEquals(NewsManager.ChangeStatus.ALREADY_APPLIED, NewsManager.cancelCheck(true, false));
        assertEquals(NewsManager.ChangeStatus.ALREADY_CANCELLED, NewsManager.cancelCheck(false, true));
    }

    @Test
    void statusFilter() {
        for (String status : new String[]{"all", "pending", "revealed", "applied", "cancelled"}) {
            assertTrue(NewsManager.isStatusFilter(status), status);
        }
        assertFalse(NewsManager.isStatusFilter("ALL"));
        assertFalse(NewsManager.isStatusFilter(""));
        assertFalse(NewsManager.isStatusFilter("done"));
        assertFalse(NewsManager.isStatusFilter(null));
    }

    @Test
    void viewDerivesStatusDirectionMagnitude() {
        NewsManager.NewsView view = new NewsManager.NewsView(7, "custom_금광", "금광", -5.0, "내용", false, "admin",
                1L, 2L, 3L, true, false, false, null, null);
        assertEquals("revealed", view.status());
        assertEquals("down", view.direction());
        assertEquals(5.0, view.magnitudePercent());
    }
}
