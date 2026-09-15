package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** 무기 등급(일반~신화) 시스템. <초월의 제단>에서 무기를 초월하면 등급이 한 단계 상승한다. */
public class GradeManager {

    public enum Grade {
        COMMON("일반", "f"),
        RARE("레어", "9"),
        UNIQUE("유니크", "a"),
        ANCIENT("고대", "6"),
        LEGEND("전설", "c"),
        MASTER("신화", "d");

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
        if (item == null) {
            return Grade.COMMON;
        }
        if (item.hasItemMeta()) {
            String name = item.getItemMeta().getPersistentDataContainer().get(gradeKey, PersistentDataType.STRING);
            if (name != null) {
                try {
                    return Grade.valueOf(name);
                } catch (IllegalArgumentException ex) {
                    // 알 수 없는 값이면 재질 기반 기본 등급으로 대체
                }
            }
        }
        return defaultGradeFor(item.getType());
    }

    /**
     * 아직 초월/등급 부여 이력이 없는 무기의 기본 등급을 재질(광물 종류)에 따라 정한다.
     * 실제 바닐라 공격력 순서(금=나무 &lt; 돌 &lt; 철 &lt; 다이아 &lt; 네더라이트)를 반영했다.
     * 삼지창/마법 지팡이(활)/철퇴처럼 재질 단계가 없는 무기는 희귀도에 맞춰 고정 등급을 준다.
     */
    public Grade defaultGradeFor(Material type) {
        String name = type.name();
        if (name.equals("NETHERITE_SWORD") || name.equals("NETHERITE_AXE")) {
            return Grade.LEGEND;
        }
        if (name.equals("DIAMOND_SWORD") || name.equals("DIAMOND_AXE")) {
            return Grade.ANCIENT;
        }
        if (name.equals("IRON_SWORD") || name.equals("IRON_AXE")) {
            return Grade.UNIQUE;
        }
        if (name.equals("STONE_SWORD") || name.equals("STONE_AXE")) {
            return Grade.RARE;
        }
        if (name.equals("WOODEN_SWORD") || name.equals("WOODEN_AXE")
                || name.equals("GOLDEN_SWORD") || name.equals("GOLDEN_AXE")) {
            return Grade.COMMON;
        }
        if (type == Material.MACE) {
            return Grade.LEGEND;
        }
        if (type == Material.TRIDENT) {
            return Grade.ANCIENT;
        }
        if (type == Material.BOW || type == Material.CROSSBOW) {
            return Grade.RARE;
        }
        return Grade.COMMON;
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
     * 이미 최고 등급(신화)이면 아무 것도 하지 않고 false를 반환한다.
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
