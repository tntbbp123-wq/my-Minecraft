package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** 대장간 강화 시스템의 계산/적용 로직. */
public class EnhanceManager {

    public enum EnhanceOutcome {
        SUCCESS,
        FAIL,
        MAX_LEVEL,
        INVALID_ITEM,
        WRONG_MATERIAL,
        NOT_ENOUGH_MATERIAL,
        NOT_ENOUGH_BALANCE
    }

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey levelKey;

    public EnhanceManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.levelKey = new NamespacedKey(plugin, "enhance_level");
    }

    public Material requiredMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("enhance.material", "AMETHYST_SHARD"));
        return material != null ? material : Material.AMETHYST_SHARD;
    }

    public int maxLevel() {
        return plugin.getConfig().getInt("enhance.max-level", 10);
    }

    public int getLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        Integer level = meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    public double successChance(int currentLevel) {
        double base = plugin.getConfig().getDouble("enhance.base-success-percent", 100.0);
        double decrease = plugin.getConfig().getDouble("enhance.decrease-per-level", 8.0);
        double min = plugin.getConfig().getDouble("enhance.min-success-percent", 15.0);
        return Math.max(min, base - decrease * currentLevel);
    }

    public double cost(int currentLevel) {
        double base = plugin.getConfig().getDouble("enhance.base-cost", 100.0);
        double perLevel = plugin.getConfig().getDouble("enhance.cost-per-level", 50.0);
        return base + perLevel * currentLevel;
    }

    /** 강화를 시도하고 성공 시 item을 직접 갱신한다. 실제 판정(재료/자금 차감)은 호출부에서 이미 끝났다고 가정한다. */
    public boolean rollSuccess(int currentLevel) {
        double chance = successChance(currentLevel);
        return Math.random() * 100.0 < chance;
    }

    public void applyEnhance(ItemStack item, int newLevel) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, newLevel);

        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.contains("강화 레벨"));
        lore.add(0, "§b강화 레벨: +" + newLevel);
        meta.setLore(lore);

        Enchantment enchantment = enchantmentFor(item.getType());
        if (enchantment != null && newLevel > 0) {
            meta.addEnchant(enchantment, newLevel, true);
        }
        item.setItemMeta(meta);
    }

    private Enchantment enchantmentFor(Material type) {
        String name = type.name();
        if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
            return Enchantment.SHARPNESS;
        }
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            return Enchantment.PROTECTION;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            return Enchantment.EFFICIENCY;
        }
        return Enchantment.UNBREAKING;
    }
}
