package com.tntbbp.myminecraft.manager.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.combat.ShockManager;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.WeaponAttributes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 신화 등급 무기 '드라켄피어스'의 아이템 정의와 두 액티브 능력.
 * [F] 드라켄 라이트닝(연쇄 번개 찌르기) / [웅크리기+F] 드라코닉 팽(쌍단검 변형).
 */
public class DrakenPierceManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey metamorphFlagKey;
    private final NamespacedKey attackDamageAttrKey;
    private final NamespacedKey metamorphSpeedAttrKey;
    private final Map<UUID, Long> lightningCooldowns = new HashMap<>();
    private final Map<UUID, Long> metamorphosisCooldowns = new HashMap<>();

    public DrakenPierceManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "draken_pierce");
        this.metamorphFlagKey = new NamespacedKey(plugin, "draken_pierce_metamorph");
        this.attackDamageAttrKey = new NamespacedKey(plugin, "draken_pierce_attack_damage");
        this.metamorphSpeedAttrKey = new NamespacedKey(plugin, "draken_pierce_metamorph_speed");
    }

    public ItemStack createItem() {
        double attackDamage = plugin.getConfig().getDouble("draken-pierce.attack-damage", 11.0);
        double penetration = defensePenetrationPercent();

        ItemStack item = new ItemBuilder(Material.TRIDENT)
                .name("§b§l드라켄피어스 §7(Draken Pierce)")
                .lore(List.of(
                        "§d§l신화 (Mythic) §8| §7무기 §8| §7공격력 §c" + formatNumber(attackDamage)
                                + " §8| §7공격속도 §e1.1",
                        "§b방어 관통 §f" + formatNumber(penetration) + "% §7(고정, 항상 적용)",
                        "",
                        "§6[F] §f드라켄 라이트닝 §7(재사용 " + lightningCooldownSeconds() + "초)",
                        "§7 전방에 번개 창극을 사출해, 적중 대상 주변으로 최대 "
                                + plugin.getConfig().getInt("draken-pierce.lightning.chain-count", 3) + "명까지 연쇄 타격",
                        "§7 적중한 모든 대상에게 §b감전§7(둔화+번개 도트) 부여",
                        "",
                        "§6[웅크리기+F] §f드라코닉 팽 §7(Draconic Fang · 재사용 " + metamorphosisCooldownSeconds() + "초)",
                        "§7 전방에 번개 폭발을 일으킨 뒤 쌍단검으로 " + metamorphosisDurationSeconds() + "초간 변형",
                        "§7 변형 중 이동속도·공격속도가 대폭 상승",
                        "",
                        "§7\"와이번의 날개뼈와 히드라의 송곳니를 두른 창끝\""
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

        WeaponAttributes.applyBase(meta, Material.TRIDENT, attackDamage, attackDamageAttrKey);

        int modelData = modelData();
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        // 드라켄피어스는 신화 등급 무기이므로 처음부터 등급은 신화로 표시되지만, 강화 한계치를
        // 30강까지 풀려면 다른 무기와 마찬가지로 초월의 제단을 거쳐야 한다 (자동 초월 없음).
        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.MASTER);
        return item;
    }

    private String formatNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public boolean isDrakenPierce(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 패시브: 방어관통 -----

    public double defensePenetrationPercent() {
        return plugin.getConfig().getDouble("draken-pierce.defense-penetration-percent", 45.0);
    }

    public int slowAmplifier() {
        return plugin.getConfig().getInt("draken-pierce.shock.slow-amplifier", 1);
    }

    private int modelData() {
        return plugin.getConfig().getInt("draken-pierce.model-data", 0);
    }

    // ----- [F] 드라켄 라이트닝 -----

    public int lightningCooldownSeconds() {
        return plugin.getConfig().getInt("draken-pierce.lightning.cooldown-seconds", 20);
    }

    public long remainingLightningCooldownSeconds(UUID uuid) {
        Long last = lightningCooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        long remaining = lightningCooldownSeconds() - (System.currentTimeMillis() - last) / 1000;
        return Math.max(0, remaining);
    }

    public boolean useDrakenLightning(Player caster) {
        if (remainingLightningCooldownSeconds(caster.getUniqueId()) > 0) {
            return false;
        }
        lightningCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        double range = plugin.getConfig().getDouble("draken-pierce.lightning.range", 10.0);
        double coneAngle = plugin.getConfig().getDouble("draken-pierce.lightning.cone-angle-degrees", 50.0);
        double damage = plugin.getConfig().getDouble("draken-pierce.lightning.damage", 9.0);
        int chainCount = plugin.getConfig().getInt("draken-pierce.lightning.chain-count", 3);
        double chainRadius = plugin.getConfig().getDouble("draken-pierce.lightning.chain-radius", 4.0);
        double chainDamageMultiplier = plugin.getConfig().getDouble("draken-pierce.lightning.chain-damage-multiplier", 0.6);
        int shockDuration = plugin.getConfig().getInt("draken-pierce.lightning.shock-duration-seconds", 5);
        double shockDps = plugin.getConfig().getDouble("draken-pierce.lightning.shock-damage-per-second", 2.5);

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        world.playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.6f);
        world.playSound(eye, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 1.2f);

        LivingEntity primary = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : targetsInCone(world, eye, direction, range, coneAngle, caster)) {
            double distance = candidate.getLocation().distance(eye);
            if (distance < bestDistance) {
                bestDistance = distance;
                primary = candidate;
            }
        }

        if (primary == null) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, eye.clone().add(direction.clone().multiply(2.0)), 30, 0.5, 0.5, 0.5, 0.05);
            return true;
        }

        ShockManager shockManager = plugin.getShockManager();
        Set<LivingEntity> hit = new LinkedHashSet<>();
        primary.damage(damage, caster);
        shockManager.applyShock(primary, caster.getUniqueId(), shockDuration, shockDps, slowAmplifier());
        hit.add(primary);
        spawnBolt(world, eye.toVector(), entityCenter(primary));

        LivingEntity current = primary;
        for (int i = 0; i < chainCount; i++) {
            LivingEntity next = null;
            double bestChainDistance = chainRadius;
            for (Entity entity : world.getNearbyEntities(current.getLocation(), chainRadius, chainRadius, chainRadius)) {
                if (!(entity instanceof LivingEntity chainCandidate) || entity.equals(caster) || hit.contains(chainCandidate)) {
                    continue;
                }
                double distance = chainCandidate.getLocation().distance(current.getLocation());
                if (distance <= bestChainDistance) {
                    bestChainDistance = distance;
                    next = chainCandidate;
                }
            }
            if (next == null) {
                break;
            }
            next.damage(damage * chainDamageMultiplier, caster);
            shockManager.applyShock(next, caster.getUniqueId(), shockDuration, shockDps, slowAmplifier());
            spawnBolt(world, entityCenter(current), entityCenter(next));
            hit.add(next);
            current = next;
        }

        return true;
    }

    // ----- [Q] 드라코닉 메타모포시스 -----

    public int metamorphosisCooldownSeconds() {
        return plugin.getConfig().getInt("draken-pierce.metamorphosis.cooldown-seconds", 25);
    }

    public int metamorphosisDurationSeconds() {
        return plugin.getConfig().getInt("draken-pierce.metamorphosis.duration-seconds", 7);
    }

    public long remainingMetamorphosisCooldownSeconds(UUID uuid) {
        Long last = metamorphosisCooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        long remaining = metamorphosisCooldownSeconds() - (System.currentTimeMillis() - last) / 1000;
        return Math.max(0, remaining);
    }

    public boolean useMetamorphosis(Player caster) {
        if (remainingMetamorphosisCooldownSeconds(caster.getUniqueId()) > 0) {
            return false;
        }
        metamorphosisCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        double burstRange = plugin.getConfig().getDouble("draken-pierce.metamorphosis.burst-range", 4.0);
        double burstConeAngle = plugin.getConfig().getDouble("draken-pierce.metamorphosis.burst-cone-angle-degrees", 140.0);
        double burstDamage = plugin.getConfig().getDouble("draken-pierce.metamorphosis.burst-damage", 6.0);
        int shockDuration = plugin.getConfig().getInt("draken-pierce.metamorphosis.shock-duration-seconds", 4);
        double shockDps = plugin.getConfig().getDouble("draken-pierce.metamorphosis.shock-damage-per-second", 2.0);
        int speedAmplifier = plugin.getConfig().getInt("draken-pierce.metamorphosis.speed-amplifier", 2);
        int durationSeconds = metamorphosisDurationSeconds();

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        world.playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.6f, 1.8f);
        world.playSound(eye, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.2f, 1.5f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, eye.clone().add(direction.clone().multiply(1.5)), 40, 1.0, 1.0, 1.0, 0.05);

        ShockManager shockManager = plugin.getShockManager();
        for (LivingEntity target : targetsInCone(world, eye, direction, burstRange, burstConeAngle, caster)) {
            target.damage(burstDamage, caster);
            shockManager.applyShock(target, caster.getUniqueId(), shockDuration, shockDps, slowAmplifier());
        }

        ItemStack mainHand = caster.getInventory().getItemInMainHand();
        if (isDrakenPierce(mainHand)) {
            caster.getInventory().setItemInMainHand(applyMetamorphVisual(mainHand));
        }

        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationSeconds * 20,
                Math.max(0, speedAmplifier), false, true, true));

        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> revertMetamorphosis(caster.getUniqueId()), durationSeconds * 20L);

        return true;
    }

    private ItemStack applyMetamorphVisual(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(metamorphFlagKey, PersistentDataType.BYTE, (byte) 1);
        int metamorphModelData = plugin.getConfig().getInt("draken-pierce.metamorphosis.model-data", 0);
        if (metamorphModelData != 0) {
            meta.setCustomModelData(metamorphModelData);
        }
        double attackSpeedBonus = plugin.getConfig().getDouble("draken-pierce.metamorphosis.attack-speed-bonus", 0.6);
        AttributeModifier speedModifier = new AttributeModifier(
                metamorphSpeedAttrKey, attackSpeedBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, speedModifier);
        item.setItemMeta(meta);
        return item;
    }

    /** 변형 지속시간이 끝나면 인벤토리 전체(핫바/메인/보조 손)를 훑어 변형된 아이템을 원상복구한다. */
    private void revertMetamorphosis(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack reverted = revertTransformedItem(contents[slot]);
            if (reverted != null) {
                inventory.setItem(slot, reverted);
            }
        }
        ItemStack revertedOffhand = revertTransformedItem(inventory.getItemInOffHand());
        if (revertedOffhand != null) {
            inventory.setItemInOffHand(revertedOffhand);
        }
    }

    private ItemStack revertTransformedItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(metamorphFlagKey, PersistentDataType.BYTE)) {
            return null;
        }
        meta.getPersistentDataContainer().remove(metamorphFlagKey);
        int normalModelData = modelData();
        meta.setCustomModelData(normalModelData != 0 ? normalModelData : null);
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);
        item.setItemMeta(meta);
        return item;
    }

    // ----- 공용 -----

    private List<LivingEntity> targetsInCone(World world, Location eye, Vector direction, double range,
                                              double coneAngleDegrees, Player exclude) {
        List<LivingEntity> result = new java.util.ArrayList<>();
        double halfAngleCos = Math.cos(Math.toRadians(coneAngleDegrees / 2.0));
        for (Entity entity : world.getNearbyEntities(eye, range, range, range)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(exclude)) {
                continue;
            }
            Vector toTarget = target.getLocation().toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance < 0.001 || distance > range) {
                continue;
            }
            double dot = direction.dot(toTarget.normalize());
            if (dot < halfAngleCos) {
                continue;
            }
            result.add(target);
        }
        return result;
    }

    private Vector entityCenter(LivingEntity entity) {
        return entity.getLocation().toVector().add(new Vector(0, entity.getHeight() / 2.0, 0));
    }

    private void spawnBolt(World world, Vector from, Vector to) {
        Vector delta = to.clone().subtract(from);
        double length = delta.length();
        if (length < 0.001) {
            return;
        }
        int steps = Math.max(1, (int) (length * 4));
        Vector step = delta.multiply(1.0 / steps);
        Vector point = from.clone();
        for (int i = 0; i <= steps; i++) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, point.getX(), point.getY(), point.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
            point.add(step);
        }
    }
}
