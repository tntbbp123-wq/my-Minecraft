package com.tntbbp.myminecraft.manager.mail;

import com.tntbbp.myminecraft.model.Mail;
import com.tntbbp.myminecraft.model.MailAttachment;
import com.tntbbp.myminecraft.model.MailSenderType;
import com.tntbbp.myminecraft.model.Mailbox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailCodecTest {

    private static Mailbox roundTrip(UUID owner, Mailbox mailbox) {
        String text = MailCodec.write(mailbox).saveToString();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return MailCodec.read(owner, yaml);
    }

    @Test
    void roundTripPreservesEveryField() {
        UUID owner = UUID.randomUUID();
        Mailbox original = new Mailbox(owner);

        MailAttachment vanilla = new MailAttachment("AAAA", "DIAMOND", "다이아몬드", null, 5, 2);
        MailAttachment special = new MailAttachment("AAAA", "AMETHYST_SHARD", "강화석", "강화석", 3, 0);
        Mail systemMail = new Mail(10, MailSenderType.SYSTEM, "시스템", "안내: 사냥대회 결과... 축하합니다!",
                "첫째 줄\n둘째 줄\n셋째 줄", List.of(vanilla, special), 1000L, false,
                1789600000000L, 1789600000000L + 7 * MailRules.DAY_MS, false, 0L, false);

        Mail adminMail = new Mail(11, MailSenderType.ADMIN, "운영자", "지급 완료", "감사합니다",
                List.of(), 0L, false, 1789600000000L, 1789600000000L + 30 * MailRules.DAY_MS,
                true, 1789600500000L, false);

        Mail heldMail = new Mail(12, MailSenderType.QUEST, "퀘스트", "보류된 우편", "우편함이 가득 찼습니다",
                List.of(new MailAttachment("AAAA", "IRON_INGOT", "철 주괴", null, 4, 1)), 500L, true,
                1789600000000L, 1789600000000L + 7 * MailRules.DAY_MS, false, 0L, true);

        original.add(systemMail);
        original.add(adminMail);
        original.add(heldMail);

        Mailbox result = roundTrip(owner, original);

        assertEquals(owner, result.owner());
        assertEquals(3, result.mails().size());

        Mail rSystem = result.find(10);
        assertNotNull(rSystem);
        assertEquals(MailSenderType.SYSTEM, rSystem.senderType());
        assertEquals("시스템", rSystem.senderName());
        assertEquals("안내: 사냥대회 결과... 축하합니다!", rSystem.title());
        assertEquals("첫째 줄\n둘째 줄\n셋째 줄", rSystem.body());
        assertEquals(1000L, rSystem.attachedG());
        assertFalse(rSystem.isGClaimed());
        assertEquals(1789600000000L, rSystem.createdAt());
        assertEquals(1789600000000L + 7 * MailRules.DAY_MS, rSystem.expiresAt());
        assertFalse(rSystem.isClaimed());
        assertFalse(rSystem.isHeld());
        assertEquals(2, rSystem.attachments().size());

        MailAttachment rVanilla = rSystem.attachments().get(0);
        assertEquals("DIAMOND", rVanilla.material());
        assertEquals("다이아몬드", rVanilla.displayName());
        assertNull(rVanilla.itemName());
        assertEquals(5, rVanilla.count());
        assertEquals(2, rVanilla.claimed());

        MailAttachment rSpecial = rSystem.attachments().get(1);
        assertEquals("강화석", rSpecial.itemName());
        assertEquals(3, rSpecial.count());
        assertEquals(0, rSpecial.claimed());

        Mail rAdmin = result.find(11);
        assertNotNull(rAdmin);
        assertEquals(MailSenderType.ADMIN, rAdmin.senderType());
        assertTrue(rAdmin.isClaimed());
        assertEquals(1789600500000L, rAdmin.claimedAt());
        assertTrue(rAdmin.attachments().isEmpty());

        Mail rHeld = result.find(12);
        assertNotNull(rHeld);
        assertEquals(MailSenderType.QUEST, rHeld.senderType());
        assertTrue(rHeld.isHeld());
        assertTrue(rHeld.isGClaimed());
        assertEquals(500L, rHeld.attachedG());
        assertEquals(1, rHeld.attachments().size());
    }

    @Test
    void skipsZeroCountAttachmentsAndNonNumericMailKeys() {
        UUID owner = UUID.randomUUID();
        Mailbox original = new Mailbox(owner);
        MailAttachment kept = new MailAttachment("AAAA", "STONE", "돌", null, 4, 1);
        MailAttachment dropped = new MailAttachment("AAAA", "AIR", "없음", null, 0, 0);
        original.add(new Mail(1, MailSenderType.SYSTEM, null, "제목", "내용",
                List.of(kept, dropped), 0L, false, 0L, 10_000L, false, 0L, false));

        YamlConfiguration yaml = MailCodec.write(original);
        ConfigurationSection mails = yaml.getConfigurationSection("mails");
        assertNotNull(mails);
        ConfigurationSection garbage = mails.createSection("not-a-number");
        garbage.set("title", "garbage");

        String text = yaml.saveToString();
        YamlConfiguration reloaded = new YamlConfiguration();
        try {
            reloaded.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        Mailbox result = MailCodec.read(owner, reloaded);

        assertEquals(1, result.mails().size());
        Mail mail = result.find(1);
        assertNotNull(mail);
        assertEquals(1, mail.attachments().size());
        assertEquals("STONE", mail.attachments().get(0).material());
    }

    @Test
    void emptyOrNullYamlProducesEmptyMailbox() {
        UUID owner = UUID.randomUUID();
        assertTrue(MailCodec.read(owner, null).isEmpty());
        assertTrue(MailCodec.read(owner, new YamlConfiguration()).isEmpty());
    }
}
