package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** 무기 등급(일반~마스터) 시스템. <초월의 제단>에서 무기를 초월하면 등급이 한 단계 상승한다. */
public class GradeManager {

    public enum Grade {
        COMMON("일반", "f"),
        RARE("레어", "9"),
        UNIQUE("유니크", "a"),
        ANCIENT("고대", "6"),
        LEGEND("레전드", "c"),
        MYSTIC("미스틱", "d"),
        MASTER("마스터", "4");

        private final String displayName;
        private final String colorCode;

        Grade(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String displayName() {
            return displayName;
        }

        public String colorCode() {
            return colorCode;
        }

        public boolean isMax() {
            return this == MASTER;
        }

        public Grade next() {
            Grade[] values = values();
            int nextOrdinal = ordinal() + 1;
            return nextOrdinal < values.length ? values[nextOrdinal] : this;
        }
    }

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey gradeKey;

    public GradeManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.gradeKey = new NamespacedKey(plugin, "item_grade");
    }

    public Grade getGrade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Grade.COMMON;
        }
        String name = item.getItemMeta().getPersistentDataContainer().get(gradeKey, PersistentDataType.STRING);
        if (name == null) {
            return Grade.COMMON;
        }
        try {
            return Grade.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return Grade.COMMON;
        }
    }

    /** 아이템에 등급을 부여/갱신하고 등급 표시 lore 줄을 새로 쓴다. */
    public void applyGrade(ItemStack item, Grade grade) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(gradeKey, PersistentDataType.STRING, grade.name());

        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.contains("등급: "));
        lore.add(0, "§7등급: §" + grade.colorCode() + grade.displayName());
        meta.setLore(lore);

        item.setItemMeta(meta);
    }

    /**
     * 무기를 한 단계 초월시킨다 (등급 +1, 강화 한계치를 30강까지 해제).
     * 이미 최고 등급(마스터)이면 아무 것도 하지 않고 false를 반환한다.
     */
    public boolean transcend(ItemStack item) {
        Grade current = getGrade(item);
        if (current.isMax()) {
            return false;
        }
        applyGrade(item, current.next());
        plugin.getEnhanceManager().markTranscended(item);
        return true;
    }
}
