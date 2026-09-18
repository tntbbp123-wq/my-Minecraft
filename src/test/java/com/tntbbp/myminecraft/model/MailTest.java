package com.tntbbp.myminecraft.model;

import com.tntbbp.myminecraft.manager.mail.MailRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailTest {

    private static Mail mail(long id, MailSenderType type, boolean claimed, boolean held, long expiresAt) {
        return new Mail(id, type, null, "제목" + id, "내용", List.of(), 0L, false, 0L, expiresAt,
                claimed, claimed ? 1L : 0L, held);
    }

    private static Mail activeMail(long id, MailSenderType type) {
        return mail(id, type, false, false, 1_000_000_000L);
    }

    private static Mail heldMail(long id, MailSenderType type) {
        return mail(id, type, false, true, 1_000_000_000L);
    }

    private static Mail claimedMail(long id, MailSenderType type) {
        return mail(id, type, true, false, 1_000_000_000L);
    }

    // ---------------------------------------------------------------- Mail.applyDelivery

    @Test
    void applyDeliveryPartialThenCompletesWithG() {
        MailAttachment a = new MailAttachment("AAAA", "DIAMOND", "다이아몬드", null, 5, 0);
        MailAttachment b = new MailAttachment("AAAA", "STONE", "돌", null, 3, 0);
        Mail mail = new Mail(1, MailSenderType.SYSTEM, "시스템", "제목", "내용",
                List.of(a, b), 100L, false, 1000L, 1000L + 7 * MailRules.DAY_MS, false, 0L, false);

        boolean fullyAfterFirst = mail.applyDelivery(new int[]{2, 0}, false, 2000L);
        assertFalse(fullyAfterFirst);
        assertFalse(mail.isClaimed());
        assertEquals(3, mail.attachments().get(0).remaining());
        assertEquals(3, mail.attachments().get(1).remaining());
        assertEquals(100L, mail.pendingG());

        boolean fullyAfterSecond = mail.applyDelivery(new int[]{3, 3}, true, 3000L);
        assertTrue(fullyAfterSecond);
        assertTrue(mail.isClaimed());
        assertEquals(3000L, mail.claimedAt());
        assertEquals(0, mail.attachments().get(0).remaining());
        assertEquals(0, mail.attachments().get(1).remaining());
        assertEquals(0L, mail.pendingG());
    }

    @Test
    void gIsClaimedOnFirstDeliveryEvenWithPendingAttachments() {
        MailAttachment a = new MailAttachment("AAAA", "DIAMOND", "다이아몬드", null, 5, 0);
        Mail mail = new Mail(2, MailSenderType.SYSTEM, null, "제목", "내용",
                List.of(a), 100L, false, 1000L, 2000L, false, 0L, false);

        boolean fully = mail.applyDelivery(new int[]{1}, true, 1500L);

        assertFalse(fully);
        assertFalse(mail.isClaimed());
        assertTrue(mail.isGClaimed());
        assertEquals(0L, mail.pendingG());
        assertEquals(4, mail.attachments().get(0).remaining());
    }

    @Test
    void textOnlyMailIsClaimedOnFirstOpen() {
        Mail mail = new Mail(3, MailSenderType.SYSTEM, null, "공지", "읽기 전용",
                List.of(), 0L, false, 1000L, 2000L, false, 0L, false);

        boolean fully = mail.applyDelivery(new int[0], false, 1234L);

        assertTrue(fully);
        assertTrue(mail.isClaimed());
        assertEquals(1234L, mail.claimedAt());
    }

    @Test
    void attachmentAddClaimedNeverExceedsCount() {
        MailAttachment attachment = new MailAttachment("AAAA", "STONE", "돌", null, 3, 0);
        attachment.addClaimed(2);
        assertEquals(2, attachment.claimed());
        attachment.addClaimed(5);
        assertEquals(3, attachment.claimed());
        assertEquals(0, attachment.remaining());
    }

    // ---------------------------------------------------------------- Mail.release

    @Test
    void releaseRecalculatesExpiryFromOriginalRetention() {
        long createdAt = 1_000_000L;
        long originalExpires = createdAt + 7 * MailRules.DAY_MS;
        Mail mail = new Mail(4, MailSenderType.SYSTEM, null, "제목", "내용",
                List.of(), 0L, false, createdAt, originalExpires, false, 0L, true);
        assertTrue(mail.isHeld());

        long now = 5_000_000L;
        mail.release(now);

        assertFalse(mail.isHeld());
        assertTrue(mail.isActive());
        assertEquals(now + 7 * MailRules.DAY_MS, mail.expiresAt());
    }

    // ---------------------------------------------------------------- Mailbox 기본 동작

    @Test
    void mailboxCountsExcludeAdminFromRegularCount() {
        Mailbox mailbox = new Mailbox(UUID.randomUUID());
        mailbox.add(activeMail(1, MailSenderType.SYSTEM));
        mailbox.add(activeMail(2, MailSenderType.ADMIN));
        mailbox.add(heldMail(3, MailSenderType.SYSTEM));
        mailbox.add(claimedMail(4, MailSenderType.SYSTEM));

        assertEquals(2, mailbox.activeCount());
        assertEquals(1, mailbox.regularActiveCount());
        assertEquals(1, mailbox.heldCount());
    }

    @Test
    void purgeExpiredRemovesExpiredRegardlessOfClaimedOrHeld() {
        Mailbox mailbox = new Mailbox(UUID.randomUUID());
        mailbox.add(mail(1, MailSenderType.SYSTEM, false, false, 10_000L)); // 활성, 안 지남
        mailbox.add(mail(2, MailSenderType.SYSTEM, true, false, 500L));    // 수령완료, 지남
        mailbox.add(mail(3, MailSenderType.SYSTEM, false, true, 500L));   // 보류, 지남
        mailbox.add(mail(4, MailSenderType.SYSTEM, false, false, 500L));  // 활성, 지남

        long now = 1000L;
        assertTrue(mailbox.hasExpired(now));
        int purged = mailbox.purgeExpired(now);

        assertEquals(3, purged);
        assertEquals(List.of(1L), mailbox.mails().stream().map(Mail::id).toList());
        assertFalse(mailbox.hasExpired(now));
    }

    @Test
    void findAndRemove() {
        Mailbox mailbox = new Mailbox(UUID.randomUUID());
        mailbox.add(activeMail(5, MailSenderType.SYSTEM));

        assertNotNull(mailbox.find(5));
        assertNull(mailbox.find(99));
        assertTrue(mailbox.remove(5));
        assertFalse(mailbox.remove(5));
        assertNull(mailbox.find(5));
    }

    @Test
    void addKeepsAscendingOrderAndReplacesSameId() {
        Mailbox mailbox = new Mailbox(UUID.randomUUID());
        mailbox.add(activeMail(3, MailSenderType.SYSTEM));
        mailbox.add(activeMail(1, MailSenderType.SYSTEM));
        mailbox.add(activeMail(2, MailSenderType.SYSTEM));
        assertEquals(List.of(1L, 2L, 3L), mailbox.mails().stream().map(Mail::id).toList());

        mailbox.add(claimedMail(2, MailSenderType.SYSTEM));
        assertEquals(3, mailbox.mails().size());
        assertTrue(mailbox.find(2).isClaimed());
    }

    @Test
    void maxIdReturnsZeroWhenEmpty() {
        Mailbox mailbox = new Mailbox(UUID.randomUUID());
        assertEquals(0L, mailbox.maxId());
        mailbox.add(activeMail(7, MailSenderType.SYSTEM));
        mailbox.add(activeMail(3, MailSenderType.SYSTEM));
        assertEquals(7L, mailbox.maxId());
    }

    // ---------------------------------------------------------------- hasRoom / releaseHeld

    @Test
    void hasRoomRegularCapReachedButAdminUnlimitedByDefault() {
        MailLimits limits = new MailLimits(2, 0, 7, 30);
        Mailbox mailbox = new Mailbox(UUID.randomUUID());
        mailbox.add(activeMail(1, MailSenderType.SYSTEM));
        mailbox.add(activeMail(2, MailSenderType.SYSTEM));

        assertFalse(mailbox.hasRoom(MailSenderType.SYSTEM, limits));
        assertTrue(mailbox.hasRoom(MailSenderType.ADMIN, limits));
        assertEquals(2, mailbox.regularActiveCount());
    }

    @Test
    void hasRoomAdminHeldWhenAdminMaxCountReached() {
        MailLimits limits = new MailLimits(2, 3, 7, 30);
        Mailbox mailbox = new Mailbox(UUID.randomUUID());
        mailbox.add(activeMail(1, MailSenderType.SYSTEM));
        mailbox.add(activeMail(2, MailSenderType.ADMIN));
        mailbox.add(activeMail(3, MailSenderType.ADMIN));

        assertEquals(3, mailbox.activeCount());
        assertFalse(mailbox.hasRoom(MailSenderType.ADMIN, limits));
    }

    @Test
    void releaseHeldReleasesOldestFirstUpToAvailableRoom() {
        MailLimits limits = new MailLimits(2, 0, 7, 30);
        Mailbox mailbox = new Mailbox(UUID.randomUUID());
        mailbox.add(activeMail(1, MailSenderType.SYSTEM));
        mailbox.add(activeMail(2, MailSenderType.SYSTEM));
        mailbox.add(heldMail(3, MailSenderType.SYSTEM));
        mailbox.add(heldMail(4, MailSenderType.SYSTEM));

        // 우편 1을 전부 받아 공간을 하나 비운다
        Mail claimed = mailbox.find(1);
        claimed.applyDelivery(new int[0], false, 1500L);
        assertTrue(claimed.isClaimed());
        assertEquals(1, mailbox.regularActiveCount());

        List<Mail> released = mailbox.releaseHeld(2000L, limits);

        assertEquals(List.of(3L), released.stream().map(Mail::id).toList());
        assertFalse(mailbox.find(3).isHeld());
        assertTrue(mailbox.find(4).isHeld());
        assertEquals(2, mailbox.regularActiveCount());
    }

    // ---------------------------------------------------------------- MailLimits

    @Test
    void maxCountAndRetentionDaysPerSenderType() {
        MailLimits limits = new MailLimits(50, 10, 7, 30);
        assertEquals(50, limits.maxCountFor(MailSenderType.SYSTEM));
        assertEquals(50, limits.maxCountFor(MailSenderType.QUEST));
        assertEquals(10, limits.maxCountFor(MailSenderType.ADMIN));
        assertEquals(7, limits.retentionDaysFor(MailSenderType.SYSTEM));
        assertEquals(30, limits.retentionDaysFor(MailSenderType.ADMIN));
    }

    @Test
    void retentionDaysClampedAndRegularMaxUnlimitedWhenNonPositive() {
        MailLimits limits = new MailLimits(0, 0, 0, -5);
        assertEquals(1, limits.regularRetentionDays());
        assertEquals(1, limits.adminRetentionDays());
        assertTrue(limits.hasRoom(MailSenderType.SYSTEM, 1_000_000, 1_000_000));
    }

    // ---------------------------------------------------------------- MailSenderType

    @Test
    void senderTypeFromIdParsesKnownValuesAndFallsBackToSystem() {
        assertEquals(MailSenderType.ADMIN, MailSenderType.fromId("admin"));
        assertEquals(MailSenderType.ADMIN, MailSenderType.fromId("ADMIN"));
        assertEquals(MailSenderType.QUEST, MailSenderType.fromId(" quest "));
        assertEquals(MailSenderType.SYSTEM, MailSenderType.fromId("unknown"));
        assertEquals(MailSenderType.SYSTEM, MailSenderType.fromId(null));
    }
}
