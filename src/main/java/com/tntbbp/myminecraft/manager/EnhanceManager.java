package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 대장간 강화 시스템의 계산/적용 로직 및 전용 아이템(강화석, 등급별 확률 강화 두루마리) 정의. */
public class EnhanceManager {

    /** 확률 강화 두루마리의 등급 정의 (id, 표시 이름, 아이콘, 색상 코드, CustomModelData, 성공 확률 보너스%). */
    public record ScrollGrade(String id, String displayName, Material material, String colorCode, int modelData, double bonusPercent) {
    }

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey levelKey;
    private final NamespacedKey stoneKey;
    private final NamespacedKey scrollGradeKey;
    private final NamespacedKey transcendedKey;
    private final NamespacedKey attackDamageModifierKey;
    private final NamespacedKey attackSpeedModifierKey;
    private final List<ScrollGrade> scrollGrades = new ArrayList<>();

    public EnhanceManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.levelKey = new NamespacedKey(plugin, "enhance_level");
        this.stoneKey = new NamespacedKey(plugin, "enhance_stone");
        this.scrollGradeKey = new NamespacedKey(plugin, "enhance_scroll_grade");
        this.transcendedKey = new NamespacedKey(plugin, "enhance_transcended");
        this.attackDamageModifierKey = new NamespacedKey(plugin, "enhance_attack_damage");
        this.attackSpeedModifierKey = new NamespacedKey(plugin, "enhance_attack_speed");
        loadScrollGrades();
    }

    private void loadScrollGrades() {
        List<Map<?, ?>> list = plugin.getConfig().getMapList("enhance.scrolls");
        for (Map<?, ?> raw : list) {
            String id = String.valueOf(raw.get("id"));
            String name = String.valueOf(raw.get("name"));
            Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (material == null) {
                material = Material.PAPER;
            }
            double bonus = raw.get("bonus-percent") instanceof Number n ? n.doubleValue() : 10.0;
            String color = raw.get("color") != null ? String.valueOf(raw.get("color")) : "f";
            int modelData = raw.get("model-data") instanceof Number n ? n.intValue() : 0;
            scrollGrades.add(new ScrollGrade(id, name, material, color, modelData, bonus));
        }
        if (scrollGrades.isEmpty()) {
            scrollGrades.add(new ScrollGrade("common", "일반등급 두루마리", Material.PAPER, "f", 500010, 10.0));
        }
    }

    // ----- 강화석 -----

    public Material stoneIconMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("enhance.material", "AMETHYST_SHARD"));
        return material != null ? material : Material.AMETHYST_SHARD;
    }

    public int stoneModelData() {
        return plugin.getConfig().getInt("enhance.model-data", 0);
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
        int modelData = stoneModelData();
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
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

    // ----- 확률 강화 두루마리 (등급별) -----

    public List<ScrollGrade> scrollGrades() {
        return scrollGrades;
    }

    public ScrollGrade getScrollGrade(String id) {
        for (ScrollGrade grade : scrollGrades) {
            if (grade.id().equalsIgnoreCase(id)) {
                return grade;
            }
        }
        return null;
    }

    public ScrollGrade defaultScrollGrade() {
        return scrollGrades.get(0);
    }

    public ItemStack createProbabilityScroll(ScrollGrade grade, int amount) {
        ItemStack item = new ItemBuilder(grade.material())
                .name("§" + grade.colorCode() + grade.displayName())
                .lore(List.of(
                        "§7강화 시도 시 함께 사용하면",
                        "§7성공 확률이 §a+" + trimZero(grade.bonusPercent()) + "%§7 증가합니다.",
                        "§7(사용 시 1개 소모)"
                ))
                .amount(amount)
                .glow()
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(scrollGradeKey, PersistentDataType.STRING, grade.id());
        if (grade.modelData() != 0) {
            meta.setCustomModelData(grade.modelData());
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean isProbabilityScroll(ItemStack item) {
        return getScrollGradeId(item) != null;
    }

    public String getScrollGradeId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(scrollGradeKey, PersistentDataType.STRING);
    }

    /** 두루마리 아이템 하나가 제공하는 성공 확률 보너스(%). 두루마리가 아니거나 알 수 없는 등급이면 0. */
    public double scrollBonusOf(ItemStack item) {
        String gradeId = getScrollGradeId(item);
        if (gradeId == null) {
            return 0.0;
        }
        ScrollGrade grade = getScrollGrade(gradeId);
        return grade != null ? grade.bonusPercent() : 0.0;
    }

    private String trimZero(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // ----- 강화 계산 -----

    /** 초월하지 않은 장비의 기본 강화 한계치. */
    public int maxLevel() {
        return plugin.getConfig().getInt("enhance.max-level", 20);
    }

    /** 이 장비가 초월되었는지에 따른 실제 강화 한계치 (초월 시 transcended-max-level). */
    public int maxLevel(ItemStack item) {
        return isTranscended(item) ? transcendedMaxLevel() : maxLevel();
    }

    /** 초월한 장비의 강화 한계치. */
    public int transcendedMaxLevel() {
        return plugin.getConfig().getInt("enhance.transcended-max-level", 30);
    }

    public boolean isTranscended(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(transcendedKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    /** <초월의 제단>에서 무기가 초월되었음을 기록해 강화 한계치를 30강으로 풀어준다. */
    public void markTranscended(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(transcendedKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    /** 이번 강화 시도에 필요한 강화석 개수. 강화할 때마다 1씩 증가한다 (0강→1강: 1개, 1강→2강: 2개, ...). */
    public int requiredStones(int currentLevel) {
        return currentLevel + 1;
    }

    public int getLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        Integer level = meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    public double successChance(int currentLevel, double scrollBonus) {
        double base = plugin.getConfig().getDouble("enhance.base-success-percent", 100.0);
        double decrease = plugin.getConfig().getDouble("enhance.decrease-per-level", 8.0);
        double min = plugin.getConfig().getDouble("enhance.min-success-percent", 15.0);
        double chance = Math.max(min, base - decrease * currentLevel);
        return Math.min(100.0, chance + scrollBonus);
    }

    public double cost(int currentLevel) {
        double base = plugin.getConfig().getDouble("enhance.base-cost", 100.0);
        double perLevel = plugin.getConfig().getDouble("enhance.cost-per-level", 50.0);
        return base + perLevel * currentLevel;
    }

    /** 강화 성공 여부를 판정한다. 재료/자금 차감은 호출부에서 이미 끝났다고 가정한다. */
    public boolean rollSuccess(int currentLevel, double scrollBonus) {
        double chance = successChance(currentLevel, scrollBonus);
        return Math.random() * 100.0 < chance;
    }

    public void applyEnhance(ItemStack item, int previousLevel, int newLevel) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, newLevel);

        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.contains("강화 레벨"));
        lore.add(0, "§b강화 레벨: +" + newLevel);
        meta.setLore(lore);

        if (isWeapon(item.getType())) {
            applyWeaponStatBonus(meta, previousLevel, newLevel);
        } else {
            Enchantment enchantment = enchantmentFor(item.getType());
            if (enchantment != null && newLevel > 0) {
                meta.addEnchant(enchantment, newLevel, true);
            }
        }
        item.setItemMeta(meta);
    }

    /** 무기 강화 시 부여되는 기본 공격력/공격속도 보너스를 재계산해서 다시 적용한다 (이전 레벨의 보너스를 정확히 제거 후 새로 부여). */
    private void applyWeaponStatBonus(ItemMeta meta, int previousLevel, int newLevel) {
        double damagePerLevel = plugin.getConfig().getDouble("enhance.attack-damage-per-level", 0.5);
        double speedPerLevel = plugin.getConfig().getDouble("enhance.attack-speed-per-level", 0.02);

        if (previousLevel > 0) {
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(
                    attackDamageModifierKey, damagePerLevel * previousLevel,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(
                    attackSpeedModifierKey, speedPerLevel * previousLevel,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }

        if (newLevel <= 0) {
            return;
        }
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(
                attackDamageModifierKey, damagePerLevel * newLevel,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(
                attackSpeedModifierKey, speedPerLevel * newLevel,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
    }

    /** 다른 매니저(초월의 제단 등)에서도 "이 아이템이 무기인가"를 같은 기준으로 판단할 수 있도록 공개. */
    public static boolean isWeapon(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("TRIDENT")
                || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("MACE");
    }

    private Enchantment enchantmentFor(Material type) {
        String name = type.name();
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            return Enchantment.PROTECTION;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            return Enchantment.EFFICIENCY;
        }
        return Enchantment.UNBREAKING;
    }
}
