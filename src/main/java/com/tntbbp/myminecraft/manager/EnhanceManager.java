package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** 대장간 강화 시스템의 계산/적용 로직 및 전용 아이템(강화석, 확률 강화 두루마리) 정의. */
public class EnhanceManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey levelKey;
    private final NamespacedKey stoneKey;
    private final NamespacedKey scrollKey;

    public EnhanceManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.levelKey = new NamespacedKey(plugin, "enhance_level");
        this.stoneKey = new NamespacedKey(plugin, "enhance_stone");
        this.scrollKey = new NamespacedKey(plugin, "enhance_scroll");
    }

    // ----- 강화석 -----

    public Material stoneIconMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("enhance.material", "AMETHYST_SHARD"));
        return material != null ? material : Material.AMETHYST_SHARD;
    }

    public ItemStack createEnhanceStone(int amount) {
        ItemStack item = new ItemBuilder(stoneIconMaterial())
                .name("§b강화석")
                .lore(List.of(
                        "§7대장간 강화에 사용되는 전용 재료입니다.",
                        "§7강화 시도 시 1개가 소모됩니다."
                ))
                .amount(amount)
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(stoneKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isEnhanceStone(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(stoneKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 확률 강화 두루마리 -----

    public Material scrollIconMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("enhance.scroll-material", "PAPER"));
        return material != null ? material : Material.PAPER;
    }

    public double scrollBonusPercent() {
        return plugin.getConfig().getDouble("enhance.scroll-bonus-percent", 15.0);
    }

    public ItemStack createProbabilityScroll(int amount) {
        ItemStack item = new ItemBuilder(scrollIconMaterial())
                .name("§d확률 강화 두루마리")
                .lore(List.of(
                        "§7강화 시도 시 함께 사용하면",
                        "§7성공 확률이 §a+" + trimZero(scrollBonusPercent()) + "%§7 증가합니다.",
                        "§7(사용 시 1개 소모)"
                ))
                .amount(amount)
                .glow()
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(scrollKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isProbabilityScroll(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(scrollKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private String trimZero(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // ----- 강화 계산 -----

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

    public double successChance(int currentLevel, boolean useScroll) {
        double base = plugin.getConfig().getDouble("enhance.base-success-percent", 100.0);
        double decrease = plugin.getConfig().getDouble("enhance.decrease-per-level", 8.0);
        double min = plugin.getConfig().getDouble("enhance.min-success-percent", 15.0);
        double chance = Math.max(min, base - decrease * currentLevel);
        if (useScroll) {
            chance = Math.min(100.0, chance + scrollBonusPercent());
        }
        return chance;
    }

    public double cost(int currentLevel) {
        double base = plugin.getConfig().getDouble("enhance.base-cost", 100.0);
        double perLevel = plugin.getConfig().getDouble("enhance.cost-per-level", 50.0);
        return base + perLevel * currentLevel;
    }

    /** 강화 성공 여부를 판정한다. 재료/자금 차감은 호출부에서 이미 끝났다고 가정한다. */
    public boolean rollSuccess(int currentLevel, boolean useScroll) {
        double chance = successChance(currentLevel, useScroll);
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
