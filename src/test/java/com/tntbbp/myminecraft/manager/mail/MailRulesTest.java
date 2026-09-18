package com.tntbbp.myminecraft.manager.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MailRulesTest {

    @Test
    void expiresAtClampsBelowOneDay() {
        assertEquals(MailRules.DAY_MS, MailRules.expiresAt(0L, 0));
        assertEquals(1000L + MailRules.DAY_MS, MailRules.expiresAt(1000L, -5));
        assertEquals(1000L + 7 * MailRules.DAY_MS, MailRules.expiresAt(1000L, 7));
    }

    @Test
    void attemptCountCapsAtSlotsTimesMaxStack() {
        assertEquals(0, MailRules.attemptCount(0, 64, 36));
        assertEquals(2304, MailRules.attemptCount(5000, 64, 36));
        assertEquals(10, MailRules.attemptCount(10, 1, 36));
    }

    @Test
    void splitStacksDividesByMaxStackSize() {
        assertArrayEquals(new int[]{64, 64, 2}, MailRules.splitStacks(130, 64));
        assertArrayEquals(new int[]{64}, MailRules.splitStacks(64, 64));
        assertArrayEquals(new int[0], MailRules.splitStacks(0, 64));
        assertArrayEquals(new int[]{1, 1, 1}, MailRules.splitStacks(3, 1));
        assertArrayEquals(new int[]{1, 1, 1}, MailRules.splitStacks(3, 0));
    }

    @Test
    void formatRemainingHumanReadable() {
        assertEquals("6일 23시간", MailRules.formatRemaining((6L * 24 * 60 + 23 * 60) * 60_000L));
        assertEquals("1일", MailRules.formatRemaining(MailRules.DAY_MS));
        assertEquals("5시간 10분", MailRules.formatRemaining((5L * 60 + 10) * 60_000L));
        assertEquals("3분", MailRules.formatRemaining(3L * 60_000L));
        assertEquals("곧 만료", MailRules.formatRemaining(0L));
        assertEquals("곧 만료", MailRules.formatRemaining(-1L));
        assertEquals("1분", MailRules.formatRemaining(5000L));
    }
}
