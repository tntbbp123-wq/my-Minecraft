package com.tntbbp.myminecraft.manager.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.OpImmunity;
import com.tntbbp.myminecraft.util.WeaponAttributes;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 신화 등급 무기 '자하신검(紫霞神劍)'.
 *
 * <ul>
 *   <li>패시브 <b>자하의 숨결</b> — 이동속도가 빨라지고, 평타 1회가 2회 타격으로 판정된다</li>
 *   <li>특수 <b>파사자하</b> — 상대의 저항/보호/방패 막기 감면을 무시하고 그대로 꽂아 넣는다</li>
 *   <li>[F 또는 웅크리기+우클릭] <b>낙매성우</b> — 3초 안에 적중하면 그 일격이 7연타로 폭발한다</li>
 * </ul>
 */
public class JahaShingeomManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> skillCooldowns = new HashMap<>();
    private final Map<UUID, Long> armedUntilMillis = new ConcurrentHashMap<>();
    /** 추가 타격이 스스로를 또 불러 무한 반복되지 않도록 걸어두는 잠금. */
    private final Set<UUID> extraHitInProgress = ConcurrentHashMap.newKeySet();

    public JahaShingeomManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "jaha_shingeom");
    }

    public ItemStack createItem() {
        double attackDamage = attackDamage();

        ItemStack item = new ItemBuilder(Material.NETHERITE_SWORD)
                .name("§5§l자하신검 §7(紫霞神劍)")
                .lore(List.of(
                        "§d§l신화 (Mythic) §8| §7무기 §8| §7공격력 §c" + formatNumber(attackDamage)
                                + " §8| §7공격속도 §e1.6",
                        "",
                        "§c[패시브] §f자하의 숨결 §7(紫霞)",
                        "§7 이동속도 §f+" + formatNumber(movementSpeedPercent()) + "%",
                        "§7 평타 1회가 §f2회 타격§7으로 판정 (두 번째 타격 "
                                + formatNumber(secondHitMultiplier() * 100) + "%)",
                        "§7 찌르기 여파로 넉백 §f×" + formatNumber(knockbackMultiplier()),
                        "",
                        "§c[특수] §f파사자하 §7(破邪紫霞)",
                        "§7 상대의 §f저항·보호 마법·방패 막기§7 감면을 전부 무시",
                        "",
                        "§6[F / 웅크리기+우클릭] §f낙매성우 §7(落梅星雨 · 재사용 "
                                + skillCooldownSeconds() + "초)",
                        "§7 시전 후 §f" + windowSeconds() + "초 §7안에 적을 때리면",
                        "§7 그 일격이 §f" + totalHits() + "연타§7로 폭발한다 (추가타 "
                                + formatNumber(extraHitMultiplier() * 100) + "%)",
                        "",
                        "§7\"붉은 매화가 밤하늘의 별처럼 쏟아진다\""
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

        WeaponAttributes.applyBase(meta, Material.NETHERITE_SWORD, attackDamage,
                new NamespacedKey(plugin, "jaha_shingeom_attack_damage"));
        // 이동속도는 손에 들고 있는 동안만 붙도록 MAINHAND 슬롯에 건다.
        meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, new AttributeModifier(
                new NamespacedKey(plugin, "jaha_shingeom_movement_speed"),
                movementSpeedPercent() / 100.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                EquipmentSlotGroup.MAINHAND));

        int modelData = plugin.getConfig().getInt("jaha-shingeom.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.MASTER);
        return item;
    }

    public boolean isJahaShingeom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 설정 -----

    public double attackDamage() {
        return plugin.getConfig().getDouble("jaha-shingeom.attack-damage", 12.0);
    }

    public double movementSpeedPercent() {
        return plugin.getConfig().getDouble("jaha-shingeom.passive.movement-speed-percent", 12.0);
    }

    public double secondHitMultiplier() {
        return plugin.getConfig().getDouble("jaha-shingeom.passive.second-hit-multiplier", 1.0);
    }

    public double knockbackMultiplier() {
        return plugin.getConfig().getDouble("jaha-shingeom.passive.knockback-multiplier", 1.25);
    }

    public int skillCooldownSeconds() {
        return plugin.getConfig().getInt("jaha-shingeom.plum-rain.cooldown-seconds", 60);
    }

    public int windowSeconds() {
        return plugin.getConfig().getInt("jaha-shingeom.plum-rain.window-seconds", 3);
    }

    public int totalHits() {
        return plugin.getConfig().getInt("jaha-shingeom.plum-rain.total-hits", 7);
    }

    public double extraHitMultiplier() {
        return plugin.getConfig().getDouble("jaha-shingeom.plum-rain.extra-hit-multiplier", 0.6);
    }

    // ----- 패시브: 더블 타격 -----

    public boolean isExtraHitInProgress(UUID attackerUuid) {
        return extraHitInProgress.contains(attackerUuid);
    }

    /**
     * 평타 여파를 한 번 더 꽂아 넣는다. 바닐라 무적 시간(noDamageTicks) 때문에 그냥 때리면
     * 두 번째 타격이 그대로 씹히므로, 무적 시간을 풀고 때린 뒤 되돌려준다.
     */
    public void scheduleSecondHit(Player attacker, LivingEntity target, double baseDamage) {
        double multiplier = secondHitMultiplier();
        if (multiplier <= 0 || OpImmunity.isImmune(target)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!attacker.isOnline() || target.isDead() || !target.isValid()) {
                return;
            }
            dealExtraHit(attacker, target, baseDamage * multiplier);
            target.getWorld().spawnParticle(Particle.DUST, target.getEyeLocation(), 8, 0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(170, 90, 220), 1.1f));
        }, 2L);
    }

    private void dealExtraHit(Player attacker, LivingEntity target, double damage) {
        extraHitInProgress.add(attacker.getUniqueId());
        int savedNoDamageTicks = target.getNoDamageTicks();
        try {
            target.setNoDamageTicks(0);
            plugin.getCurseManager().dealTrueDamage(target, attacker, damage);
        } finally {
            target.setNoDamageTicks(savedNoDamageTicks);
            extraHitInProgress.remove(attacker.getUniqueId());
        }
    }

    // ----- [F / 웅크리기+우클릭] 낙매성우 -----

    public long remainingCooldownSeconds(UUID uuid) {
        Long last = skillCooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        return Math.max(0, skillCooldownSeconds() - (System.currentTimeMillis() - last) / 1000);
    }

    public boolean armPlumRain(Player caster) {
        if (remainingCooldownSeconds(caster.getUniqueId()) > 0) {
            return false;
        }
        skillCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());
        armedUntilMillis.put(caster.getUniqueId(), System.currentTimeMillis() + windowSeconds() * 1000L);

        caster.getWorld().playSound(caster.getEyeLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1.4f, 1.6f);
        spawnPlumAura(caster);
        return true;
    }

    public boolean isArmed(UUID uuid) {
        Long until = armedUntilMillis.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            armedUntilMillis.remove(uuid);
            return false;
        }
        return true;
    }

    /** 낙매성우가 걸린 상태에서 적중했을 때, 최초 1타를 뺀 나머지를 연속으로 꽂아 넣는다. */
    public void triggerPlumRain(Player attacker, LivingEntity target, double baseDamage) {
        armedUntilMillis.remove(attacker.getUniqueId());

        int extraHits = Math.max(0, totalHits() - 1);
        double damagePerHit = baseDamage * extraHitMultiplier();

        attacker.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.6f, 1.8f);
        attacker.sendMessage("§d낙매성우! §f" + totalHits() + "연타가 터졌습니다.");

        new BukkitRunnable() {
            int hit = 0;

            @Override
            public void run() {
                if (hit >= extraHits || !attacker.isOnline() || target.isDead() || !target.isValid()) {
                    cancel();
                    return;
                }
                hit++;
                dealExtraHit(attacker, target, damagePerHit);
                target.getWorld().spawnParticle(Particle.DUST, target.getEyeLocation(), 12, 0.4, 0.4, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(220, 60, 140), 1.3f));
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.9f);
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** 평타 넉백을 살짝 더 세게 준다. */
    public void applyExtraKnockback(Player attacker, LivingEntity target) {
        double multiplier = knockbackMultiplier();
        if (multiplier <= 1.0) {
            return;
        }
        Vector direction = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (direction.lengthSquared() < 1.0E-4) {
            return;
        }
        direction = direction.normalize().multiply((multiplier - 1.0) * 0.5);
        direction.setY(Math.max(0.1, direction.getY()));
        target.setVelocity(target.getVelocity().add(direction));
    }

    private void spawnPlumAura(Player caster) {
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= windowSeconds() * 20 || !caster.isOnline() || !isArmed(caster.getUniqueId())) {
                    cancel();
                    return;
                }
                elapsed += 4;
                caster.getWorld().spawnParticle(Particle.DUST, caster.getLocation().add(0, 1.0, 0),
                        10, 0.5, 0.8, 0.5, 0,
                        new Particle.DustOptions(Color.fromRGB(190, 70, 160), 1.2f));
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private String formatNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
