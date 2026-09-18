package com.tntbbp.myminecraft.gui.mail;

import com.tntbbp.myminecraft.manager.mail.MailManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** 우편 GUI·채팅 문구 공용 헬퍼. */
public final class MailText {

    private static final int WRAP = 28;

    private MailText() {
    }

    /** 기울임 없는 GUI 설명 줄. */
    public static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * 아이템 이름. 표시 이름이 재질 이름 그대로(바닐라 아이템)면 게임 언어로 번역되는 이름을,
     * 아니면(특수 아이템 등) 저장된 표시 이름을 쓴다.
     */
    public static Component itemName(String displayName, String material) {
        if (displayName == null || displayName.equals(material)) {
            Material type = material == null ? null : Material.matchMaterial(material);
            if (type != null) {
                return Component.translatable(type.translationKey());
            }
            return Component.text(material == null ? "?" : material);
        }
        return Component.text(displayName);
    }

    /** 받은 것 요약: "강화석 x3, 다이아몬드 x10, 5,000G". */
    public static Component receivedSummary(List<MailManager.Received> received, long g, String currency) {
        TextComponent.Builder builder = Component.text();
        boolean first = true;
        for (MailManager.Received item : received) {
            if (!first) {
                builder.append(Component.text(", "));
            }
            builder.append(itemName(item.displayName(), item.material()))
                    .append(Component.text(" x" + String.format("%,d", item.count())));
            first = false;
        }
        if (g > 0) {
            if (!first) {
                builder.append(Component.text(", "));
            }
            builder.append(Component.text(String.format("%,d", g) + currency));
        }
        return builder.build();
    }

    /** 내용을 GUI 설명 줄 폭에 맞게 나눈다(줄바꿈 유지). */
    public static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        for (String raw : text.split("\n", -1)) {
            String line = raw.stripTrailing();
            if (line.isEmpty()) {
                lines.add("");
                continue;
            }
            int[] codePoints = line.codePoints().toArray();
            for (int start = 0; start < codePoints.length; start += WRAP) {
                int end = Math.min(codePoints.length, start + WRAP);
                lines.add(new String(codePoints, start, end - start));
            }
        }
        return lines;
    }
}
