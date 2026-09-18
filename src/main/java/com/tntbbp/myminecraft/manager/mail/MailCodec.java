package com.tntbbp.myminecraft.manager.mail;

import com.tntbbp.myminecraft.model.Mail;
import com.tntbbp.myminecraft.model.MailAttachment;
import com.tntbbp.myminecraft.model.MailSenderType;
import com.tntbbp.myminecraft.model.Mailbox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 우편함 ↔ YAML 변환 (Bukkit 서버 없이 동작하는 순수 로직, 단위 테스트 대상).
 *
 * <pre>
 * version: 1
 * mails:
 *   '1012':
 *     sender-type: admin
 *     sender-name: 운영자
 *     title: 안내
 *     body: 접속 감사합니다.
 *     attached-g: 5000
 *     g-claimed: false
 *     created-at: 1789600000000
 *     expires-at: 1792192000000
 *     claimed: false
 *     claimed-at: 0
 *     held: false
 *     attachments:
 *     - data: (ItemStack#serializeAsBytes base64)
 *       material: AMETHYST_SHARD
 *       display-name: 강화석
 *       item-name: 강화석      # 바닐라 아이템이면 없음
 *       count: 3
 *       claimed: 0
 * </pre>
 */
public final class MailCodec {

    public static final int VERSION = 1;

    private MailCodec() {
    }

    public static YamlConfiguration write(Mailbox mailbox) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", VERSION);
        ConfigurationSection mails = yaml.createSection("mails");
        for (Mail mail : mailbox.mails()) {
            ConfigurationSection section = mails.createSection(Long.toString(mail.id()));
            section.set("sender-type", mail.senderType().id());
            section.set("sender-name", mail.senderName());
            section.set("title", mail.title());
            section.set("body", mail.body());
            section.set("attached-g", mail.attachedG());
            section.set("g-claimed", mail.isGClaimed());
            section.set("created-at", mail.createdAt());
            section.set("expires-at", mail.expiresAt());
            section.set("claimed", mail.isClaimed());
            section.set("claimed-at", mail.claimedAt());
            section.set("held", mail.isHeld());
            List<Map<String, Object>> attachments = new ArrayList<>();
            for (MailAttachment attachment : mail.attachments()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("data", attachment.data());
                entry.put("material", attachment.material());
                entry.put("display-name", attachment.displayName());
                if (attachment.itemName() != null) {
                    entry.put("item-name", attachment.itemName());
                }
                entry.put("count", attachment.count());
                entry.put("claimed", attachment.claimed());
                attachments.add(entry);
            }
            section.set("attachments", attachments);
        }
        return yaml;
    }

    /** 잘못된 항목(id가 숫자가 아님 등)은 건너뛴다. */
    public static Mailbox read(UUID owner, ConfigurationSection yaml) {
        Mailbox mailbox = new Mailbox(owner);
        ConfigurationSection mails = yaml == null ? null : yaml.getConfigurationSection("mails");
        if (mails == null) {
            return mailbox;
        }
        for (String key : mails.getKeys(false)) {
            ConfigurationSection section = mails.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            long id;
            try {
                id = Long.parseLong(key.strip());
            } catch (NumberFormatException e) {
                continue;
            }
            List<MailAttachment> attachments = new ArrayList<>();
            for (Map<?, ?> entry : section.getMapList("attachments")) {
                MailAttachment attachment = readAttachment(entry);
                if (attachment != null) {
                    attachments.add(attachment);
                }
            }
            mailbox.add(new Mail(
                    id,
                    MailSenderType.fromId(section.getString("sender-type")),
                    section.getString("sender-name"),
                    section.getString("title", ""),
                    section.getString("body", ""),
                    attachments,
                    section.getLong("attached-g", 0L),
                    section.getBoolean("g-claimed", false),
                    section.getLong("created-at", 0L),
                    section.getLong("expires-at", 0L),
                    section.getBoolean("claimed", false),
                    section.getLong("claimed-at", 0L),
                    section.getBoolean("held", false)));
        }
        return mailbox;
    }

    private static MailAttachment readAttachment(Map<?, ?> entry) {
        Object data = entry.get("data");
        if (data == null) {
            return null;
        }
        Object material = entry.get("material");
        Object displayName = entry.get("display-name");
        Object itemName = entry.get("item-name");
        int count = intValue(entry.get("count"));
        if (count <= 0) {
            return null;
        }
        return new MailAttachment(String.valueOf(data),
                material == null ? "STONE" : String.valueOf(material),
                displayName == null ? (material == null ? "?" : String.valueOf(material)) : String.valueOf(displayName),
                itemName == null ? null : String.valueOf(itemName),
                count,
                intValue(entry.get("claimed")));
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, number.longValue()));
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).strip());
            } catch (NumberFormatException ignored) {
                // 아래 0
            }
        }
        return 0;
    }
}
