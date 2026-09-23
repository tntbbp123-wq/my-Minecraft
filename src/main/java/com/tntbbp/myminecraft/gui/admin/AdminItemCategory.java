package com.tntbbp.myminecraft.gui.admin;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 메뉴에서 특수 아이템을 나눠 보여주는 칸.
 *
 * <p>아이템이 어느 칸에 들어갈지는 {@link SpecialItemCatalog#categoryOf}의 분류로 정한다. 웹 관리자의
 * 카탈로그와 같은 분류를 쓰므로, 무기를 새로 추가해도 카탈로그에 무기로 등록되면 여기 무기 칸에도
 * 자동으로 들어간다.
 */
public enum AdminItemCategory {

    WEAPON("무기", "§c", Material.NETHERITE_SWORD, Set.of("weapon")),
    ARMOR("방어구", "§b", Material.NETHERITE_CHESTPLATE, Set.of("armor")),
    MATERIAL("재료", "§a", Material.AMETHYST_SHARD, Set.of("material")),
    ENHANCE("강화 아이템", "§e", Material.ANVIL, Set.of("enhance", "starforce", "scroll")),
    BLACK_MARKET("암시장 아이템", "§5", Material.FIRE_CHARGE, Set.of("blackmarket")),
    COIN("화폐", "§6", Material.GOLD_INGOT, Set.of("coin"));

    private final String displayName;
    private final String color;
    private final Material icon;
    private final Set<String> catalogIds;

    AdminItemCategory(String displayName, String color, Material icon, Set<String> catalogIds) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.catalogIds = catalogIds;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    public Material icon() {
        return icon;
    }

    /** 카탈로그 분류({@link SpecialItemCatalog#CATEGORY_IDS} 중 하나)가 들어갈 칸. 없으면 null. */
    public static AdminItemCategory of(String catalogId) {
        for (AdminItemCategory category : values()) {
            if (category.catalogIds.contains(catalogId)) {
                return category;
            }
        }
        return null;
    }

    /**
     * 모든 특수 아이템을 칸별로 나눈다. 칸 안의 순서는 카탈로그 순서를 그대로 따른다.
     *
     * <p>어느 칸에도 안 맞는 분류가 생기면 <b>강화 아이템</b> 칸으로 보낸다. 조용히 빠지는 것보다는
     * 엉뚱한 칸에라도 보이는 편이 낫다. (그런 일이 없도록 시험이 모든 분류를 확인한다.)
     */
    public static Map<AdminItemCategory, List<String>> group(MyMinecraftPlugin plugin) {
        Map<AdminItemCategory, List<String>> grouped = new EnumMap<>(AdminItemCategory.class);
        for (AdminItemCategory category : values()) {
            grouped.put(category, new ArrayList<>());
        }
        for (String name : SpecialItemCatalog.allItemNames(plugin)) {
            AdminItemCategory category = of(SpecialItemCatalog.categoryOf(plugin, name));
            grouped.get(category == null ? ENHANCE : category).add(name);
        }
        return grouped;
    }
}
