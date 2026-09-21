package com.tntbbp.myminecraft.manager.item;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 전설 등급 궁수 방어구 '아르테미스 세트'. 달의 위상에 따라 성능이 실시간으로 달라진다.
 *
 * <p>네 부위 전부 보호/화염 보호/폭발 보호/발사체 보호 V가 기본으로 붙어 있고, 기본 방어력
 * 수치도 네더라이트보다 높게 고정된다.
 *
 * <p><b>월광 공명</b> — 보름달에 가까울수록 이동속도·공격속도가 오르고, 활·쇠뇌 피해가 커지며,
 * 피격을 완전히 흘려내는 확률도 올라간다. 마인크래프트의 달 위상은 8단계(0이 보름달)이고,
 * 밤에만 달이 뜨므로 <b>밤일 때만</b> 공명이 걸린다.
 *
 * <p>세트 효과는 네 부위를 모두 입었을 때만 켜진다.
 */
public class ArtemisSetManager {

    /** 세트를 이루는 부위. 방어구 슬롯 순서와 같다. */
    public enum Piece {
        HELMET("투구", Material.NETHERITE_HELMET, EquipmentSlotGroup.HEAD),
        CHESTPLATE("흉갑", Material.NETHERITE_CHESTPLATE, EquipmentSlotGroup.CHEST),
        LEGGINGS("각반", Material.NETHERITE_LEGGINGS, EquipmentSlotGroup.LEGS),
        BOOTS("장화", Material.NETHERITE_BOOTS, EquipmentSlotGroup.FEET);

        private final String displayName;
        private final Material material;
        private final EquipmentSlotGroup slot;

        Piece(String displayName, Material material, EquipmentSlotGroup slot) {
            this.displayName = displayName;
            this.material = material;
            this.slot = slot;
        }

        public String displayName() {
            return displayName;
        }

        public Material material() {
            return material;
        }

        public EquipmentSlotGroup slot() {
            return slot;
        }

        /** 이 부위의 네더라이트 기본 방어력. */
        public double baseArmor() {
            return switch (this) {
                case HELMET -> 3.0;
                case CHESTPLATE -> 8.0;
                case LEGGINGS -> 6.0;
                case BOOTS -> 3.0;
            };
        }

        public static Piece of(Material material) {
            for (Piece piece : values()) {
                if (piece.material() == material) {
                    return piece;
                }
            }
            return null;
        }

        /** 바닐라 방어구 재질(가죽~네더라이트) 중 이 부위에 해당하는 것들. 제작 재료 판정에 쓴다. */
        public static Piece ofAnyArmor(Material material) {
            String name = material.name();
            if (name.endsWith("_HELMET") || material == Material.TURTLE_HELMET) {
                return HELMET;
            }
            if (name.endsWith("_CHESTPLATE")) {
                return CHESTPLATE;
            }
            if (name.endsWith("_LEGGINGS")) {
                return LEGGINGS;
            }
            if (name.endsWith("_BOOTS")) {
                return BOOTS;
            }
            return null;
        }
    }

    /** 제작법의 네 방향에 들어가야 하는 인챈트. */
    public static final Map<String, Enchantment> RECIPE_ENCHANTS = new LinkedHashMap<>() {{
        put("북(위)", Enchantment.PROTECTION);
        put("동(오른쪽)", Enchantment.BLAST_PROTECTION);
        put("서(왼쪽)", Enchantment.PROJECTILE_PROTECTION);
        put("남(아래)", Enchantment.FIRE_PROTECTION);
    }};

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey pieceKey;

    public ArtemisSetManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.pieceKey = new NamespacedKey(plugin, "artemis_piece");
    }

    // ----- 설정 -----

    public boolean enabled() {
        return plugin.getConfig().getBoolean("artemis.enabled", true);
    }

    /** 기본 방어력 배율. 네더라이트 대비 몇 배로 고정할지. */
    public double armorMultiplier() {
        return plugin.getConfig().getDouble("artemis.armor-multiplier", 1.5);
    }

    public int enchantLevel() {
        return plugin.getConfig().getInt("artemis.enchant-level", 5);
    }

    /** 제작 재료로 인정할 최소 인챈트 레벨. */
    public int recipeEnchantLevel() {
        return plugin.getConfig().getInt("artemis.recipe-enchant-level", 4);
    }

    public double evadeBasePercent() {
        return plugin.getConfig().getDouble("artemis.set.evade-base-percent", 20.0);
    }

    /** 보름달일 때의 회피 확률. 위상이 멀어질수록 기본값 쪽으로 내려온다. */
    public double evadeFullMoonPercent() {
        return plugin.getConfig().getDouble("artemis.set.evade-full-moon-percent", 35.0);
    }

    public int evadeSwiftnessSeconds() {
        return plugin.getConfig().getInt("artemis.set.evade-swiftness-seconds", 2);
    }

    public double moonSpeedPercent() {
        return plugin.getConfig().getDouble("artemis.moon.speed-percent", 25.0);
    }

    public double moonBowDamageMultiplier() {
        return plugin.getConfig().getDouble("artemis.moon.bow-damage-multiplier", 1.5);
    }

    // ----- 달의 위상 -----

    /**
     * 보름달에 얼마나 가까운지 0.0~1.0으로 돌려준다. 1.0이 보름달, 0.0이 그믐이다.
     *
     * <p>낮에는 달이 없으므로 0을 돌려준다. 위상은 8단계이고 0번이 보름달이라, 0에서 멀어진
     * 만큼 값이 내려간다(0→1.0, 1·7→0.75, 2·6→0.5, 3·5→0.25, 4→0.0).
     */
    public double moonPower(World world) {
        long time = world.getTime();
        boolean night = time >= 13000 && time <= 23000;
        if (!night) {
            return 0.0;
        }
        int phase = (int) ((world.getFullTime() / 24000L) % 8L);
        int distance = Math.min(phase, 8 - phase);   // 0~4
        return 1.0 - distance / 4.0;
    }

    public boolean isFullMoonish(World world) {
        return moonPower(world) >= 0.75;
    }

    // ----- 아이템 -----

    public ItemStack createPiece(Piece piece) {
        double armor = piece.baseArmor() * armorMultiplier();
        int level = enchantLevel();

        List<String> lore = new ArrayList<>(List.of(
                "§c§l전설 (Legendary) §8| §7아르테미스 세트 §8| §7방어력 §f" + num(armor),
                "§7달빛을 받아 스스로 벼려지는 은빛 갑주",
                "",
                "§b[패시브] §f월광 공명",
                "§7 밤이 되면 달의 위상에 따라 성능이 오른다",
                "§7 보름달에 가까울수록:",
                "§7  이동·공격속도 §f+" + num(moonSpeedPercent()) + "%§7, 활·쇠뇌 피해 §f"
                        + num(moonBowDamageMultiplier()) + "배",
                "§7  회피 확률 §f" + num(evadeFullMoonPercent()) + "%§7까지 상승",
                "",
                "§e[세트 4부위] §f여신의 회피",
                "§7 피격 시 §f" + num(evadeBasePercent()) + "% §7확률로 피해를 완전히 흘리고",
                "§7 §f신속 III §7" + evadeSwiftnessSeconds() + "초",
                "",
                "§e[세트 4부위] §f철벽방어",
                "§7 기본 방어력이 네더라이트의 §f" + num(armorMultiplier()) + "배",
                "",
                "§7\"달이 차오를수록 화살은 빗나가지 않는다\""
        ));

        ItemStack item = new ItemBuilder(piece.material())
                .name("§b§l아르테미스의 " + piece.displayName())
                .lore(lore)
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(pieceKey, PersistentDataType.STRING, piece.name());
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

        meta.addEnchant(Enchantment.PROTECTION, level, true);
        meta.addEnchant(Enchantment.FIRE_PROTECTION, level, true);
        meta.addEnchant(Enchantment.BLAST_PROTECTION, level, true);
        meta.addEnchant(Enchantment.PROJECTILE_PROTECTION, level, true);

        // 방어력 수정자를 직접 넣으면 재질 기본값이 대체되므로, 배율을 곱한 값을 통째로 넣는다.
        meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(
                new NamespacedKey(plugin, "artemis_armor_" + piece.name().toLowerCase()),
                armor, AttributeModifier.Operation.ADD_NUMBER, piece.slot()));

        int modelData = plugin.getConfig().getInt("artemis.model-data." + piece.name().toLowerCase(), 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.LEGEND);
        return item;
    }

    public Piece pieceOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String name = item.getItemMeta().getPersistentDataContainer().get(pieceKey, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        try {
            return Piece.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 네 부위를 모두 입고 있는지. */
    public boolean hasFullSet(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        int count = 0;
        for (ItemStack piece : armor) {
            if (pieceOf(piece) != null) {
                count++;
            }
        }
        return count == Piece.values().length;
    }

    /** 지금 이 플레이어의 회피 확률(%). 세트를 다 입지 않았으면 0. */
    public double evadePercent(Player player) {
        if (!enabled() || !hasFullSet(player)) {
            return 0.0;
        }
        double base = evadeBasePercent();
        return base + (evadeFullMoonPercent() - base) * moonPower(player.getWorld());
    }

    private String num(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
