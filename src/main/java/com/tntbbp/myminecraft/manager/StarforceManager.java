package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 스타포스 시스템: 전용 재료 '별가루'를 소모해 무기에 '성'을 붙인다.
 * 별 개수당 방어관통(%)이 오르며, 최대 개수(10성)를 달성하면 무기 종류별 특수 능력이 열린다
 * (10성 특수 능력 자체는 별도 리스너에서 처리한다).
 */
public class StarforceManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey starKey;
    private final NamespacedKey stardustKey;

    public StarforceManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.starKey = new NamespacedKey(plugin, "star_count");
        this.stardustKey = new NamespacedKey(plugin, "stardust");
    }

    // ----- 별가루 -----

    public Material stardustMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("starforce.material", "GLOWSTONE_DUST"));
        return material != null ? material : Material.GLOWSTONE_DUST;
    }

    public int stardustModelData() {
        return plugin.getConfig().getInt("starforce.model-data", 0);
    }

    public int stardustPerStar() {
        return plugin.getConfig().getInt("starforce.stardust-per-star", 1);
    }

    public ItemStack createStardust(int amount) {
        ItemStack item = new ItemBuilder(stardustMaterial())
                .name("§e별가루")
                .lore(List.of(
                        "§7스타포스에 사용되는 전용 재료입니다.",
                        "§7무기에 성을 붙일 때마다 " + stardustPerStar() + "개가 소모됩니다."
                ))
                .amount(amount)
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(stardustKey, PersistentDataType.BYTE, (byte) 1);
        int modelData = stardustModelData();
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean isStardust(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(stardustKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 별(성) -----

    public int maxStars() {
        return plugin.getConfig().getInt("starforce.max-stars", 10);
    }

    public double percentPerStar() {
        return plugin.getConfig().getDouble("starforce.defense-penetration-per-star", 3.0);
    }

    public int getStars(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer stars = item.getItemMeta().getPersistentDataContainer().get(starKey, PersistentDataType.INTEGER);
        return stars == null ? 0 : stars;
    }

    /** 이 무기가 현재 갖고 있는 방어관통 수치(%). */
    public double defensePenetrationPercent(ItemStack item) {
        return getStars(item) * percentPerStar();
    }

    /** 10성(최대 별 개수)을 달성해 무기 종류별 특수 능력이 활성화된 상태인지. */
    public boolean hasTenStarAbility(ItemStack item) {
        return getStars(item) >= maxStars();
    }

    /** 별을 하나 부여하고 "★★★☆☆..."/"방어관통: n%" lore 줄을 새로 쓴다. */
    public void applyStar(ItemStack item, int newStars) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, newStars);

        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.contains("★") || line.contains("방어관통"));

        int max = maxStars();
        String starsLine = "§e" + "★".repeat(Math.min(newStars, max)) + "§8" + "☆".repeat(Math.max(0, max - newStars));
        lore.add(0, "§7방어관통: §f" + trimZero(newStars * percentPerStar()) + "%");
        lore.add(0, starsLine);
        meta.setLore(lore);

        item.setItemMeta(meta);
    }

    private String trimZero(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
