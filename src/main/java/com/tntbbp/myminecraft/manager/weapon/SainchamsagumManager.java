package com.tntbbp.myminecraft.manager.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.combat.CurseManager;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.OpImmunity;
import com.tntbbp.myminecraft.util.Particles;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 신화 등급 무기 '사인참사검(四寅斬邪劍)'.
 *
 * <ul>
 *   <li>패시브 <b>사기침노</b> — 들고만 있어도 주변 적의 이동속도·공격속도를 갉아먹는다</li>
 *   <li>[F] <b>인시의 참격</b> — 전방을 베어 '벽사의 저주'(방어력·회복량 반감)를 남긴다</li>
 *   <li>[웅크리기+우클릭] <b>칠성강림</b> — 7연속 천상참격, 마지막 타격에 침묵+제압+고정 피해</li>
 * </ul>
 */
public class SainchamsagumManager {

    private static final int AURA_INTERVAL_TICKS = 20;

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> slashCooldowns = new HashMap<>();
    private final Map<UUID, Long> ultimateCooldowns = new HashMap<>();
    private BukkitTask auraTask;

    public SainchamsagumManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "sainchamsagum");
    }

    public void start() {
        auraTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickAura, AURA_INTERVAL_TICKS, AURA_INTERVAL_TICKS);
    }

    public void stop() {
        if (auraTask != null) {
            auraTask.cancel();
        }
    }

    public ItemStack createItem() {
        double attackDamage = attackDamage();

        ItemStack item = new ItemBuilder(Material.NETHERITE_SWORD)
                .name("§4§l사인참사검 §7(四寅斬邪劍)")
                .lore(List.of(
                        "§d§l신화 (Mythic) §8| §7무기 §8| §7공격력 §c" + formatNumber(attackDamage)
                                + " §8| §7공격속도 §e1.6",
                        "",
                        "§c[패시브] §f사기침노 §7(四虎煞氣)",
                        "§7 반경 §f" + formatNumber(auraRadius()) + "블록 §7안의 적은 이동속도와",
                        "§7 공격속도가 계속 깎인다",
                        "",
                        "§6[F] §f인시의 참격 §7(寅時斬擊 · 재사용 " + slashCooldownSeconds() + "초)",
                        "§7 전방을 단숨에 내리베어 §c" + formatNumber(slashDamage()) + " §7피해",
                        "§7 적중 시 §5벽사의 저주§7: " + curseDurationSeconds() + "초간 방어력 "
                                + formatNumber(curseDefenseReducePercent()) + "%",
                        "§7 회복량 " + formatNumber(curseHealReducePercent()) + "% 감소",
                        "",
                        "§6[웅크리기+우클릭] §f칠성강림 §7(七星降臨 · 재사용 " + ultimateCooldownSeconds() + "초)",
                        "§7 북두칠성의 기운으로 §f" + ultimateStrikes() + "연속 §7천상참격",
                        "§7 시전 중 모든 상태 이상 면역",
                        "§7 마지막 참격: §f침묵 " + ultimateSilenceSeconds() + "초 §7+ §f제압 "
                                + ultimateStunSeconds() + "초 §7+ 방어무시 §c"
                                + formatNumber(ultimateFinalDamage()) + " §7피해",
                        "",
                        "§7\"호랑이의 해, 달, 날, 시각에 벼려낸 사악함을 베는 칼\""
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

        WeaponAttributes.applyBase(meta, Material.NETHERITE_SWORD, attackDamage,
                new NamespacedKey(plugin, "sainchamsagum_attack_damage"));

        int modelData = plugin.getConfig().getInt("sainchamsagum.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.MASTER);
        return item;
    }

    public boolean isSainchamsagum(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 설정 -----

    public double attackDamage() {
        return plugin.getConfig().getDouble("sainchamsagum.attack-damage", 13.0);
    }

    public double auraRadius() {
        return plugin.getConfig().getDouble("sainchamsagum.aura.radius", 7.0);
    }

    private int auraSlowAmplifier() {
        return plugin.getConfig().getInt("sainchamsagum.aura.slow-amplifier", 0);
    }

    private int auraFatigueAmplifier() {
        return plugin.getConfig().getInt("sainchamsagum.aura.mining-fatigue-amplifier", 0);
    }

    public int slashCooldownSeconds() {
        return plugin.getConfig().getInt("sainchamsagum.slash.cooldown-seconds", 18);
    }

    public double slashDamage() {
        return plugin.getConfig().getDouble("sainchamsagum.slash.damage", 14.0);
    }

    public int curseDurationSeconds() {
        return plugin.getConfig().getInt("sainchamsagum.slash.curse-duration-seconds", 8);
    }

    public double curseDefenseReducePercent() {
        return plugin.getConfig().getDouble("sainchamsagum.slash.curse-defense-reduce-percent", 50.0);
    }

    public double curseHealReducePercent() {
        return plugin.getConfig().getDouble("sainchamsagum.slash.curse-heal-reduce-percent", 50.0);
    }

    public int ultimateCooldownSeconds() {
        return plugin.getConfig().getInt("sainchamsagum.ultimate.cooldown-seconds", 90);
    }

    public int ultimateStrikes() {
        return plugin.getConfig().getInt("sainchamsagum.ultimate.strikes", 7);
    }

    public double ultimateFinalDamage() {
        return plugin.getConfig().getDouble("sainchamsagum.ultimate.final-fixed-damage", 14.0);
    }

    public int ultimateSilenceSeconds() {
        return plugin.getConfig().getInt("sainchamsagum.ultimate.final-silence-seconds", 4);
    }

    public int ultimateStunSeconds() {
        return plugin.getConfig().getInt("sainchamsagum.ultimate.final-stun-seconds", 2);
    }

    // ----- 패시브: 사기침노 -----

    private void tickAura() {
        double radius = auraRadius();
        for (Player holder : plugin.getServer().getOnlinePlayers()) {
            if (!isSainchamsagum(holder.getInventory().getItemInMainHand())) {
                continue;
            }
            for (LivingEntity target : SkillTargets.inCone(
                    holder.getWorld(), holder.getLocation(), holder.getLocation().getDirection(),
                    radius, 360.0, holder)) {
                if (OpImmunity.isImmune(target) || plugin.getCurseManager().isImmune(target.getUniqueId())) {
                    continue;
                }
                // 지속시간을 주기보다 살짝 길게 잡아야 검을 든 동안 끊기지 않는다.
                int duration = AURA_INTERVAL_TICKS * 2;
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration,
                        auraSlowAmplifier(), true, false, false));
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration,
                        auraFatigueAmplifier(), true, false, false));
            }
            holder.getWorld().spawnParticle(Particle.DUST, holder.getLocation().add(0, 1.0, 0),
                    6, radius / 3.0, 0.6, radius / 3.0, 0,
                    new Particle.DustOptions(Color.fromRGB(120, 0, 0), 1.2f));
        }
    }

    // ----- [F] 인시의 참격 -----

    public long remainingSlashCooldown(UUID uuid) {
        return remainingCooldown(slashCooldowns, uuid, slashCooldownSeconds());
    }

    public boolean useTigerHourSlash(Player caster) {
        if (remainingSlashCooldown(caster.getUniqueId()) > 0) {
            return false;
        }
        slashCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        double range = plugin.getConfig().getDouble("sainchamsagum.slash.range", 6.0);
        double coneAngle = plugin.getConfig().getDouble("sainchamsagum.slash.cone-angle-degrees", 90.0);

        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        world.playSound(eye, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.4f, 0.7f);
        world.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 0.6f);
        spawnSlashArc(world, eye, direction, range);

        CurseManager curseManager = plugin.getCurseManager();
        for (LivingEntity target : SkillTargets.inCone(world, eye, direction, range, coneAngle, caster)) {
            target.damage(slashDamage(), caster);
            curseManager.applyCurse(target, "벽사의 저주", curseDefenseReducePercent(),
                    curseHealReducePercent(), -1, curseDurationSeconds());
        }
        return true;
    }

    // ----- [웅크리기+우클릭] 칠성강림 -----

    public long remainingUltimateCooldown(UUID uuid) {
        return remainingCooldown(ultimateCooldowns, uuid, ultimateCooldownSeconds());
    }

    public boolean useSevenStarsDescent(Player caster) {
        if (remainingUltimateCooldown(caster.getUniqueId()) > 0) {
            return false;
        }
        ultimateCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        int strikes = ultimateStrikes();
        int intervalTicks = plugin.getConfig().getInt("sainchamsagum.ultimate.strike-interval-ticks", 8);
        double range = plugin.getConfig().getDouble("sainchamsagum.ultimate.range", 7.0);
        double coneAngle = plugin.getConfig().getDouble("sainchamsagum.ultimate.cone-angle-degrees", 70.0);
        double damagePerStrike = plugin.getConfig().getDouble("sainchamsagum.ultimate.damage-per-strike", 6.0);

        CurseManager curseManager = plugin.getCurseManager();
        // 마지막 참격이 떨어질 때까지 + 여유 0.5초 동안 상태 이상 면역
        curseManager.grantImmunity(caster, strikes * intervalTicks + 10);

        new BukkitRunnable() {
            int strike = 0;

            @Override
            public void run() {
                if (strike >= strikes || !caster.isOnline()) {
                    cancel();
                    return;
                }
                strike++;
                boolean finalStrike = strike == strikes;

                World world = caster.getWorld();
                Location eye = caster.getEyeLocation();
                Vector direction = eye.getDirection().normalize();

                world.playSound(eye, finalStrike ? Sound.ENTITY_LIGHTNING_BOLT_IMPACT : Sound.ENTITY_PLAYER_ATTACK_CRIT,
                        finalStrike ? 1.8f : 1.2f, finalStrike ? 0.8f : 1.4f);
                spawnStarPillar(world, eye, direction, range, finalStrike);

                for (LivingEntity target : SkillTargets.inCone(world, eye, direction, range, coneAngle, caster)) {
                    target.damage(damagePerStrike, caster);
                    if (finalStrike) {
                        curseManager.dealTrueDamage(target, caster, ultimateFinalDamage());
                        curseManager.applySilence(target, ultimateSilenceSeconds());
                        curseManager.applyStun(target, ultimateStunSeconds());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);

        return true;
    }

    // ----- 내부 -----

    private long remainingCooldown(Map<UUID, Long> cooldowns, UUID uuid, int cooldownSeconds) {
        Long last = cooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        return Math.max(0, cooldownSeconds - (System.currentTimeMillis() - last) / 1000);
    }

    private void spawnSlashArc(World world, Location eye, Vector direction, double range) {
        Vector forward = direction.clone().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            return;
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        for (double angle = -60; angle <= 60; angle += 5) {
            double radians = Math.toRadians(angle);
            Vector offset = forward.clone().multiply(Math.cos(radians) * range * 0.6)
                    .add(right.clone().multiply(Math.sin(radians) * range * 0.6));
            Location point = eye.clone().add(offset);
            world.spawnParticle(Particle.DUST, point, 2, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 30, 30), 1.4f));
        }
    }

    private void spawnStarPillar(World world, Location eye, Vector direction, double range, boolean finalStrike) {
        Location impact = eye.clone().add(direction.clone().multiply(range * 0.6));
        for (double height = 0; height <= 6; height += 0.4) {
            Location point = impact.clone().add(0, height, 0);
            world.spawnParticle(Particle.END_ROD, point, finalStrike ? 4 : 2, 0.15, 0.05, 0.15, 0.0);
        }
        if (finalStrike) {
            Particles.flash(world, impact, 1);
            world.spawnParticle(Particle.SONIC_BOOM, impact, 1);
        }
    }

    private String formatNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
