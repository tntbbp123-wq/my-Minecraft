package com.tntbbp.myminecraft.manager.item;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 무기 등급(일반~신화) 표시 시스템. 등급은 재질(또는 신화 등급 무기의 경우 고정값)로 정해지며,
 * <초월의 제단>은 등급을 바꾸지 않는다 — 초월은 오직 강화 한계치를 20강에서 30강으로 풀어주는
 * 역할만 한다 (EnhanceManager.markTranscended 참고).
 */
public class GradeManager {

    public enum Grade {
        COMMON("일반", "f"),
        RARE("레어", "9"),
        UNIQUE("유니크", "a"),
        ANCIENT("고대", "6"),
        // 전설은 노란색(e)이다. 황금색(6)은 이미 고대가 쓰고 있어 겹치면 구분이 안 된다.
        LEGEND("전설", "e"),
        MASTER("신화", "d"),
        /** 신화 위의 마지막 등급. 레이드 보스 부산물과 (앞으로 만들) 세상에 하나뿐인 종언급 장비에 붙는다. */
        FINALE("종언", "4");

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
     * 아직 등급 부여 이력이 없는 무기의 기본 등급. 바닐라 아이템은 재질과 무관하게 모두
     * 일반 등급에서 시작한다 (신화 등급은 레바테인/드라켄피어스 같은 전용 커스텀 무기에만
     * applyGrade로 직접 부여됨).
     */
    public Grade defaultGradeFor(Material type) {
        return Grade.COMMON;
    }

    /** 등급이 기록돼 있는지 (한 번이라도 {@link #applyGrade}를 거쳤는지). */
    public boolean hasGrade(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(gradeKey, PersistentDataType.STRING);
    }

    /**
     * 기본 등급(일반)을 자동으로 붙이는 대상인지. <b>내구도가 있는 장비</b>(무기·방어구·도구·방패·겉날개 등)만이다.
     *
     * <p>흙·조약돌처럼 쌓이는 아이템에 등급 줄을 쓰면, 새로 캔 아이템(줄 없음)과 등급이 붙은 아이템이 서로
     * 겹쳐지지 않아 인벤토리가 난장판이 된다. 내구도가 있는 장비는 원래 한 칸에 하나라 그런 문제가 없다.
     */
    public boolean isGradable(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getType().getMaxDurability() > 0;
    }

    /**
     * 등급이 없는 장비에 기본 등급(일반)을 붙인다. 이미 등급이 있으면(커스텀 무기 등) 건드리지 않는다.
     *
     * @return 붙였으면 true
     */
    public boolean ensureDefaultGrade(ItemStack item) {
        if (!isGradable(item) || hasGrade(item)) {
            return false;
        }
        applyGrade(item, defaultGradeFor(item.getType()));
        return true;
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
     * 무기를 초월시켜 강화 한계치를 20강에서 30강으로 해제한다. 등급 자체는 바뀌지 않는다
     * (신화 등급 무기도 예외 없이 이 과정을 거쳐야 30강까지 강화할 수 있다).
     * 이미 초월했다면 아무 것도 하지 않고 false를 반환한다.
     */
    public boolean transcend(ItemStack item) {
        EnhanceManager enhanceManager = plugin.getEnhanceManager();
        if (enhanceManager.isTranscended(item)) {
            return false;
        }
        enhanceManager.markTranscended(item);
        return true;
    }
}
