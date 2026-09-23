package com.tntbbp.myminecraft.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** 거래 기록 등에 남길 아이템 이름. 색 코드는 뺀다. */
public final class ItemLabels {

    private ItemLabels() {
    }

    /**
     * 이름을 붙인 아이템이면 그 이름(색 코드 제거), 아니면 재질 이름을 소문자로 (예: {@code diamond}).
     * 플러그인 아이템은 거의 다 이름이 붙어 있어 웹 관리자에서 알아보기 쉽다.
     */
    public static String of(ItemStack item) {
        if (item == null) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String stripped = meta.getDisplayName().replaceAll("(?i)§[0-9A-FK-ORX]", "").trim();
            if (!stripped.isEmpty()) {
                return stripped;
            }
        }
        return item.getType().getKey().getKey();
    }
}
