package com.tntbbp.myminecraft.manager.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.SkillTargets;
import com.tntbbp.myminecraft.util.WeaponAttributes;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 유니크 등급 장병기 '방천화극(方天畫戟)'. 느리고 무겁지만 여러 명을 한 번에 쓸어 넘긴다.
 *
 * <ul>
 *   <li>패시브 <b>투신의 분노</b> — 체력이 절반 아래로 떨어지면 공격속도가 오르고,
 *       평타가 주변까지 베는 범위가 두 배가 된다</li>
 *   <li>[F] <b>패왕의 일격</b> — 크게 회전한 뒤 지면을 내려찍어 전방 부채꼴에 충격파</li>
 * </ul>
 */
public class FangtianJiManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> strikeCooldowns = new ConcurrentHashMap<>();

    public FangtianJiManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "fangtian_ji");
    }

    public ItemStack createItem() {
        ItemStack item = new ItemBuilder(Material.NETHERITE_AXE)
                .name("§a§l방천화극 §7(方天畫戟)")
                .lore(List.of(
                        "§a§l유니크 (Unique) §8| §7장병기 §8| §7공격력 §c" + num(attackDamage())
                                + " §8| §7공격속도 §e" + num(attackSpeed()),
                        "§7치명타 §c" + num(critDamage()) + " §8| §7초승달 날과 창끝, 붉은 술이 달린 장병기",
                        "",
                        "§c[패시브] §f투신의 분노",
                        "§7 체력 §f" + num(ragePercent()) + "% §7이하에서",
                        "§7 공격속도 §f+" + num(rageAttackSpeedPercent()) + "%§7, 쓸어버리기 범위 §f2배",
                        "",
                        "§6[F] §f패왕의 일격 §7(재사용 " + strikeCooldownSeconds() + "초)",
                        "§7 크게 회전한 뒤 지면을 내려찍어",
                        "§7 전방 부채꼴 §f" + num(strikeRange()) + "블록 §7에 §c" + num(strikeDamage()) + " §7대지 파열 피해",
                        "",
                        "§7\"사람 중엔 여포, 말 중엔 적토\""
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        WeaponAttributes.applyBase(meta, Material.NETHERITE_AXE, attackDamage(), attackSpeed(),
                new NamespacedKey(plugin, "fangtian_ji_attack_damage"));
        int modelData = plugin.getConfig().getInt("fangtian-ji.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.UNIQUE);
        return item;
    }

    public boolean isFangtianJi(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 설정 -----

    public double attackDamage() {
        return plugin.getConfig().getDouble("fangtian-ji.attack-damage", 18.0);
    }

    public double attackSpeed() {
        return plugin.getConfig().getDouble("fangtian-ji.attack-speed", 0.8);
    }

    public double critDamage() {
        return plugin.getConfig().getDouble("fangtian-ji.crit-damage", 26.0);
    }

    public double ragePercent() {
        return plugin.getConfig().getDouble("fangtian-ji.rage.health-percent", 50.0);
    }

    public double rageAttackSpeedPercent() {
        return plugin.getConfig().getDouble("fangtian-ji.rage.attack-speed-percent", 20.0);
    }

    /** 평소 평타가 함께 베는 반경. 분노 상태에서는 두 배가 된다. */
    public double sweepRadius() {
        return plugin.getConfig().getDouble("fangtian-ji.rage.sweep-radius", 2.0);
    }

    public double sweepDamagePercent() {
        return plugin.getConfig().getDouble("fangtian-ji.rage.sweep-damage-percent", 40.0);
    }

    public int strikeCooldownSeconds() {
        return plugin.getConfig().getInt("fangtian-ji.strike.cooldown-seconds", 20);
    }

    public double strikeRange() {
        return plugin.getConfig().getDouble("fangtian-ji.strike.range", 7.0);
    }

    public double strikeConeAngle() {
        return plugin.getConfig().getDouble("fangtian-ji.strike.cone-angle-degrees", 100.0);
    }

    public double strikeDamage() {
        return plugin.getConfig().getDouble("fangtian-ji.strike.damage", 16.0);
    }

    // ----- 패시브: 투신의 분노 -----

    /** 체력이 설정 비율 아래로 떨어졌는지. */
    public boolean isEnraged(Player player) {
        double max = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        return max > 0 && player.getHealth() / max * 100.0 <= ragePercent();
    }

    /**
     * 분노 상태에서 공격속도를 올려준다. 무기를 든 동안만 붙었다 떨어지도록
     * 포션 효과(성급함)가 아니라 짧은 지속시간으로 계속 갱신한다.
     */
    public void applyRageHaste(Player player) {
        int amplifier = Math.max(0, (int) Math.round(rageAttackSpeedPercent() / 10.0) - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, amplifier, true, false, false));
    }

    /** 평타가 함께 베는 주변 적들. 분노 상태면 반경이 두 배다. */
    public List<LivingEntity> sweepTargets(Player attacker, LivingEntity hit) {
        double radius = sweepRadius() * (isEnraged(attacker) ? 2.0 : 1.0);
        List<LivingEntity> targets = SkillTargets.inCone(hit.getWorld(), hit.getLocation(),
                hit.getLocation().getDirection(), radius, 360.0, attacker);
        targets.remove(hit);
        return targets;
    }

    public void spawnSweepArc(Player attacker, LivingEntity hit, double radius) {
        World world = hit.getWorld();
        for (double angle = 0; angle < 360; angle += 12) {
            double radians = Math.toRadians(angle);
            Location point = hit.getLocation().clone().add(
                    Math.cos(radians) * radius, 0.8, Math.sin(radians) * radius);
            world.spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0, 0, 0, 0);
        }
    }

    // ----- [F] 패왕의 일격 -----

    public long remainingStrikeCooldown(UUID uuid) {
        Long last = strikeCooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        return Math.max(0, strikeCooldownSeconds() - (System.currentTimeMillis() - last) / 1000);
    }

    public boolean useOverlordStrike(Player caster) {
        if (remainingStrikeCooldown(caster.getUniqueId()) > 0) {
            return false;
        }
        strikeCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        world.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.8f, 0.6f);
        world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.8f);
        spawnShockwave(world, caster.getLocation(), direction, strikeRange());

        for (LivingEntity target : SkillTargets.inCone(world, eye, direction,
                strikeRange(), strikeConeAngle(), caster)) {
            target.damage(strikeDamage(), caster);
            target.setVelocity(target.getVelocity().add(direction.clone().multiply(0.6).setY(0.35)));
        }
        return true;
    }

    private void spawnShockwave(World world, Location origin, Vector direction, double range) {
        Vector forward = direction.clone().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            return;
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        for (double step = 1.0; step <= range; step += 0.8) {
            double width = step * 0.5;
            for (double offset = -width; offset <= width; offset += 0.5) {
                Location point = origin.clone()
                        .add(forward.clone().multiply(step))
                        .add(right.clone().multiply(offset))
                        .add(0, 0.2, 0);
                world.spawnParticle(Particle.BLOCK, point, 3, 0.1, 0.05, 0.1, 0,
                        Material.DIRT.createBlockData());
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(190, 60, 40), 1.3f));
            }
        }
    }

    private String num(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
