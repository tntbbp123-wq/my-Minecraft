package com.tntbbp.myminecraft.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockTest {

    /** 상한이 없는(최대값 0) 기본 종목. */
    private static Stock baseStock() {
        return new Stock("gold", "금광", Material.GOLD_INGOT, 1000.0, 100.0, 10.0, "금 채굴");
    }

    @Test
    void 뉴스_변동폭만큼_오르내린다() {
        Stock stock = baseStock();
        stock.applyNewsImpact(10.0);
        assertEquals(1100.0, stock.getPrice());
        stock.applyNewsImpact(-50.0);
        assertEquals(550.0, stock.getPrice());
    }

    @Test
    void 최소값_아래로는_내려가지_않는다() {
        Stock stock = baseStock();
        stock.applyNewsImpact(-99.0);
        assertEquals(100.0, stock.getPrice());
    }

    @Test
    void NaN_변동폭은_무시한다() {
        // 예전에는 Math.round(NaN) = 0 이라 주가가 0이 됐다 → 누구나 공짜로 무한 매수
        Stock stock = baseStock();
        stock.applyNewsImpact(Double.NaN);
        assertEquals(1000.0, stock.getPrice());
        assertEquals(1000.0, stock.getPreviousPrice());
    }

    @Test
    void 무한대_변동폭은_무시한다() {
        // 예전에는 상한 없는 종목이 Long.MAX_VALUE/100 ≈ 9경 G로 뛰었다
        Stock stock = baseStock();
        stock.applyNewsImpact(Double.POSITIVE_INFINITY);
        assertEquals(1000.0, stock.getPrice());
    }
}
