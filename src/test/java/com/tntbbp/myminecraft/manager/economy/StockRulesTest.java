package com.tntbbp.myminecraft.manager.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 주식 종목 정의·가격 규칙(StockRules) 단위 테스트. */
class StockRulesTest {

    @Test
    void idForName() {
        assertEquals("custom_금광", StockRules.idForName("금광"));
        assertEquals("custom_gold_mine", StockRules.idForName("  Gold  Mine "));
    }

    @Test
    void paletteIndexIsAlwaysInRange() {
        String id = "custom_gold_mine";
        int size = 12;
        assertEquals(Math.abs(id.hashCode()) % size, StockRules.paletteIndex(id, size));

        String minValueId = "polygenelubricants";
        assertEquals(Integer.MIN_VALUE, minValueId.hashCode(), "이 문자열의 hashCode는 Integer.MIN_VALUE여야 한다");
        int index = StockRules.paletteIndex(minValueId, size);
        assertTrue(index >= 0 && index < size);
    }

    @Test
    void validateNameChecks() {
        assertNotNull(StockRules.validateName(null));
        assertNotNull(StockRules.validateName("   "));
        assertNull(StockRules.validateName("a".repeat(32)));
        assertNotNull(StockRules.validateName("a".repeat(33)));
        assertNotNull(StockRules.validateName("a\nb"));
        assertNull(StockRules.validateName("금광"));
    }

    @Test
    void validateBusinessChecks() {
        assertNull(StockRules.validateBusiness(null));
        assertNull(StockRules.validateBusiness(""));
        assertNull(StockRules.validateBusiness("a".repeat(100)));
        assertNotNull(StockRules.validateBusiness("a".repeat(101)));
        assertNotNull(StockRules.validateBusiness("a\nb"));
    }

    @Test
    void validateDefinitionCustom() {
        assertNull(StockRules.validateDefinition(true, 0, 10, 1));
        assertNotNull(StockRules.validateDefinition(true, -1, 10, 1));
        assertNotNull(StockRules.validateDefinition(true, 10, 10, 1));
        assertNotNull(StockRules.validateDefinition(true, 10, 5, 1));
        assertNotNull(StockRules.validateDefinition(true, 0, 10, 0));
        assertNotNull(StockRules.validateDefinition(true, 0, 10, -1));
        assertNotNull(StockRules.validateDefinition(true, Double.NaN, 10, 1));
        assertNotNull(StockRules.validateDefinition(true, 0, Double.POSITIVE_INFINITY, 1));
    }

    @Test
    void validateDefinitionPercent() {
        assertNull(StockRules.validateDefinition(false, 1, 0, 0));
        assertNull(StockRules.validateDefinition(false, 1, 5, 0));
        assertNotNull(StockRules.validateDefinition(false, 5, 5, 0));
        assertNotNull(StockRules.validateDefinition(false, -1, 5, 0));
    }

    @Test
    void clampBehaviour() {
        assertEquals(1.0, StockRules.clamp(0.5, 1.0, 10.0));
        assertEquals(10.0, StockRules.clamp(20.0, 1.0, 10.0));
        assertEquals(5.0, StockRules.clamp(5.0, 1.0, 10.0));
        assertEquals(100.0, StockRules.clamp(100.0, 1.0, 0.0));
    }

    @Test
    void round2Behaviour() {
        assertEquals(3.14, StockRules.round2(3.14159));
        assertEquals(2.5, StockRules.round2(2.5));
        assertEquals(480.0, StockRules.round2(480.0));
    }

    @Test
    void parseRevisionVariants() {
        assertEquals(7, StockRules.parseRevision("7"));
        assertEquals(7, StockRules.parseRevision("\"7\""));
        assertEquals(7, StockRules.parseRevision("W/\"7\""));
        assertEquals(12, StockRules.parseRevision(" 12 "));
        assertNull(StockRules.parseRevision(null));
        assertNull(StockRules.parseRevision(""));
        assertNull(StockRules.parseRevision("abc"));
        assertNull(StockRules.parseRevision("\"\""));
    }

    @Test
    void validateReasonChecks() {
        assertNotNull(StockRules.validateReason(null, true));
        assertNotNull(StockRules.validateReason("   ", true));
        assertNull(StockRules.validateReason(null, false));
        assertNull(StockRules.validateReason("a".repeat(200), true));
        assertNotNull(StockRules.validateReason("a".repeat(201), true));
        assertNotNull(StockRules.validateReason("a\nb", true));
    }
}
