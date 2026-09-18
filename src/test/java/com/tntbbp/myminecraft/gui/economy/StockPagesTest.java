package com.tntbbp.myminecraft.gui.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 주식 거래소 GUI 페이지 계산(StockPages) 단위 테스트. perPage는 실제 값인 21을 쓴다. */
class StockPagesTest {

    private static final int PER_PAGE = 21;

    @Test
    void pageCountBoundaries() {
        assertEquals(1, StockPages.pageCount(0, PER_PAGE));
        assertEquals(1, StockPages.pageCount(1, PER_PAGE));
        assertEquals(1, StockPages.pageCount(21, PER_PAGE));
        assertEquals(2, StockPages.pageCount(22, PER_PAGE));
        assertEquals(2, StockPages.pageCount(42, PER_PAGE));
        assertEquals(3, StockPages.pageCount(43, PER_PAGE));
        assertThrows(IllegalArgumentException.class, () -> StockPages.pageCount(10, 0));
        assertThrows(IllegalArgumentException.class, () -> StockPages.pageCount(10, -1));
    }

    @Test
    void clampPageBoundaries() {
        assertEquals(0, StockPages.clampPage(-3, 22, PER_PAGE));
        assertEquals(1, StockPages.clampPage(5, 22, PER_PAGE));
        assertEquals(0, StockPages.clampPage(0, 22, PER_PAGE));
    }

    @Test
    void fromToIndex() {
        assertEquals(0, StockPages.fromIndex(0, PER_PAGE));
        assertEquals(21, StockPages.toIndex(0, PER_PAGE, 22));
        assertEquals(21, StockPages.fromIndex(1, PER_PAGE));
        assertEquals(22, StockPages.toIndex(1, PER_PAGE, 22));
    }

    @Test
    void previousAndNext() {
        assertFalse(StockPages.hasPrevious(0));
        assertTrue(StockPages.hasPrevious(1));
        assertTrue(StockPages.hasNext(0, 22, PER_PAGE));
        assertFalse(StockPages.hasNext(1, 22, PER_PAGE));
    }
}
