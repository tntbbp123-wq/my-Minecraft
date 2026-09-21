package com.tntbbp.myminecraft.manager.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.util.ItemBuilder;
import com.tntbbp.myminecraft.util.OpImmunity;
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
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 고대 등급 모닝스타 '타이탄'. 느리지만 한 방이 무겁고, 맞은 쪽을 계속 띄워 올린다.
 *
 * <ul>
 *   <li>패시브 <b>갑옷 분쇄</b> — 방어력과 방패 가드를 일정 비율 무시하고, 적중 시 둔화를 남긴다</li>
 *   <li>[F] <b>지진타</b> — 내리꽂아 반경 안의 적을 띄우고 물리 피해</li>
 *   <li>[웅크리기+F] <b>타이탄 크래시</b> — 높이 도약했다가 낙하. 마법진과 암석 기둥이 솟고,
 *       띄운 뒤 지면으로 처박는다</li>
 * </ul>
 */
public class TitanManager {

    private final MyMinecraftPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> quakeCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> crashCooldowns = new ConcurrentHashMap<>();

    public TitanManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "titan");
    }

    public ItemStack createItem() {
        ItemStack item = new ItemBuilder(Material.MACE)
                .name("§6§l타이탄 §7(Titan)")
                .lore(List.of(
                        "§6§l고대 (Ancient) §8| §7모닝스타 §8| §7공격력 §c" + num(attackDamage())
                                + " §8| §7공격속도 §e" + num(attackSpeed()),
                        "§7치명타 §c" + num(critDamage()) + " §8| §7묵직한 철퇴 헤드에 가시가 촘촘히 박혀 있다",
                        "",
                        "§c[패시브] §f갑옷 분쇄",
                        "§7 방어력과 방패 가드를 §f" + num(armorIgnorePercent()) + "% §7무시",
                        "§7 적중 시 " + slowSeconds() + "초간 이동속도 §f" + num(slowPercent()) + "% §7감소",
                        "",
                        "§6[F] §f지진타 §7(재사용 " + quakeCooldownSeconds() + "초)",
                        "§7 내리꽂아 반경 §f" + num(quakeRadius()) + "블록 §7의 적을 띄우고",
                        "§7 §c" + num(quakeDamage()) + " §7물리 충격 피해",
                        "",
                        "§6[웅크리기+F] §f타이탄 크래시 §7(대지의 심판 · 재사용 "
                                + crashCooldownSeconds() + "초)",
                        "§7 높이 도약했다가 낙하. 붉은 마법진과 암석 기둥이 솟고",
                        "§7 반경 §f" + num(crashRadius()) + "블록 §7의 적을 띄운 뒤 지면으로 처박는다",
                        "§7 §c" + num(crashDamage()) + " §7폭발 피해",
                        "",
                        "§7\"대지가 무엇을 짊어지고 있었는지 알게 될 것이다\""
                ))
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        WeaponAttributes.applyBase(meta, Material.MACE, attackDamage(), attackSpeed(),
                new NamespacedKey(plugin, "titan_attack_damage"));
        int modelData = plugin.getConfig().getInt("titan.model-data", 0);
        if (modelData != 0) {
            meta.setCustomModelData(modelData);
        }
        item.setItemMeta(meta);

        plugin.getGradeManager().applyGrade(item, GradeManager.Grade.ANCIENT);
        return item;
    }

    public boolean isTitan(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // ----- 설정 -----

    public double attackDamage() {
        return plugin.getConfig().getDouble("titan.attack-damage", 17.5);
    }

    public double attackSpeed() {
        return plugin.getConfig().getDouble("titan.attack-speed", 0.7);
    }

    /** 치명타로 들어갈 때의 최종 피해. 바닐라 1.5배 대신 이 값으로 고정한다. */
    public double critDamage() {
        return plugin.getConfig().getDouble("titan.crit-damage", 25.0);
    }

    public double armorIgnorePercent() {
        return plugin.getConfig().getDouble("titan.passive.armor-ignore-percent", 29.0);
    }

    public int slowSeconds() {
        return plugin.getConfig().getInt("titan.passive.slow-seconds", 3);
    }

    public double slowPercent() {
        return plugin.getConfig().getDouble("titan.passive.slow-percent", 40.0);
    }

    public int quakeCooldownSeconds() {
        return plugin.getConfig().getInt("titan.quake.cooldown-seconds", 16);
    }

    public double quakeRadius() {
        return plugin.getConfig().getDouble("titan.quake.radius", 5.0);
    }

    public double quakeDamage() {
        return plugin.getConfig().getDouble("titan.quake.damage", 12.0);
    }

    public int crashCooldownSeconds() {
        return plugin.getConfig().getInt("titan.crash.cooldown-seconds", 20);
    }

    public double crashRadius() {
        return plugin.getConfig().getDouble("titan.crash.radius", 7.0);
    }

    public double crashDamage() {
        return plugin.getConfig().getDouble("titan.crash.damage", 20.0);
    }

    // ----- 패시브: 갑옷 분쇄 -----

    /**
     * 둔화 단계. 바닐라 둔화는 단계당 15%씩 깎이므로 설정한 감소율에 가장 가까운 단계를 고른다.
     * (40% → 15%×3 = 45%에 해당하는 2단계)
     */
    public int slowAmplifier() {
        return Math.max(0, (int) Math.round(slowPercent() / 15.0) - 1);
    }

    public void applyCrushSlow(LivingEntity target) {
        if (OpImmunity.isImmune(target)) {
            return;
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                slowSeconds() * 20, slowAmplifier(), false, true, true));
    }

    // ----- [F] 지진타 -----

    public long remainingQuakeCooldown(UUID uuid) {
        return remaining(quakeCooldowns, uuid, quakeCooldownSeconds());
    }

    public boolean useQuake(Player caster) {
        if (remainingQuakeCooldown(caster.getUniqueId()) > 0) {
            return false;
        }
        quakeCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        World world = caster.getWorld();
        Location center = caster.getLocation();
        // 먼저 살짝 띄웠다가 내리꽂는 모양새. 실제 판정은 착지 연출과 함께 바로 낸다.
        caster.setVelocity(new Vector(0, 0.45, 0));
        world.playSound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.6f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!caster.isOnline()) {
                    return;
                }
                Location impact = caster.getLocation();
                World w = caster.getWorld();
                w.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.7f);
                w.playSound(impact, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.6f);
                shockRing(w, impact, quakeRadius());

                for (LivingEntity target : SkillTargets.inCone(w, impact, impact.getDirection(),
                        quakeRadius(), 360.0, caster)) {
                    target.damage(quakeDamage(), caster);
                    if (!OpImmunity.isImmune(target)) {
                        target.setVelocity(target.getVelocity().setY(0.9));
                    }
                    applyCrushSlow(target);
                }
            }
        }.runTaskLater(plugin, 8L);
        return true;
    }

    // ----- [웅크리기+F] 타이탄 크래시 -----

    public long remainingCrashCooldown(UUID uuid) {
        return remaining(crashCooldowns, uuid, crashCooldownSeconds());
    }

    public boolean useCrash(Player caster) {
        if (remainingCrashCooldown(caster.getUniqueId()) > 0) {
            return false;
        }
        crashCooldowns.put(caster.getUniqueId(), System.currentTimeMillis());

        World world = caster.getWorld();
        world.playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, 0.6f);
        caster.setVelocity(new Vector(0, 1.35, 0));
        // 낙하 중에는 떨어지는 피해를 받지 않게 해준다. 자기 기술에 자기가 다치면 곤란하다.
        caster.setFallDistance(0);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 2;
                if (!caster.isOnline()) {
                    cancel();
                    return;
                }
                caster.setFallDistance(0);
                caster.getWorld().spawnParticle(Particle.CRIT, caster.getLocation(), 6, 0.3, 0.3, 0.3, 0.0);

                // 정점을 찍고 내려와 땅에 닿거나, 너무 오래 걸리면 강제로 터뜨린다.
                boolean landed = ticks > 12 && caster.isOnGround();
                if (landed || ticks > 60) {
                    crashImpact(caster);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
        return true;
    }

    private void crashImpact(Player caster) {
        World world = caster.getWorld();
        Location impact = caster.getLocation();
        double radius = crashRadius();

        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        world.playSound(impact, Sound.BLOCK_DEEPSLATE_BREAK, 1.8f, 0.5f);
        magicCircle(world, impact, radius);
        world.spawnParticle(Particle.FLASH, impact, 3);

        List<LivingEntity> targets = SkillTargets.inCone(world, impact, impact.getDirection(),
                radius, 360.0, caster);
        for (LivingEntity target : targets) {
            target.damage(crashDamage(), caster);
            applyCrushSlow(target);
            if (!OpImmunity.isImmune(target)) {
                target.setVelocity(target.getVelocity().setY(1.2));
            }
        }

        // 띄워 올린 뒤 지면으로 처박는다. 암석 기둥이 순서대로 솟는 연출과 함께.
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                rockPillars(world, impact, radius, step);
                if (step < 8) {
                    return;
                }
                for (LivingEntity target : targets) {
                    if (target.isDead() || OpImmunity.isImmune(target)) {
                        continue;
                    }
                    target.setVelocity(new Vector(0, -2.0, 0));
                }
                world.playSound(impact, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.8f, 0.5f);
                cancel();
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    // ----- 내부 -----

    private long remaining(Map<UUID, Long> cooldowns, UUID uuid, int seconds) {
        Long last = cooldowns.get(uuid);
        if (last == null) {
            return 0;
        }
        return Math.max(0, seconds - (System.currentTimeMillis() - last) / 1000);
    }

    private void shockRing(World world, Location center, double radius) {
        for (double angle = 0; angle < 360; angle += 8) {
            double radians = Math.toRadians(angle);
            Location point = center.clone().add(Math.cos(radians) * radius, 0.2, Math.sin(radians) * radius);
            world.spawnParticle(Particle.BLOCK, point, 6, 0.2, 0.1, 0.2, 0,
                    org.bukkit.Material.DEEPSLATE.createBlockData());
        }
    }

    /** 붉은 빛 고대 마법진. */
    private void magicCircle(World world, Location center, double radius) {
        for (double r = radius; r > 0; r -= radius / 3.0) {
            for (double angle = 0; angle < 360; angle += 6) {
                double radians = Math.toRadians(angle);
                Location point = center.clone().add(Math.cos(radians) * r, 0.15, Math.sin(radians) * r);
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(190, 30, 30), 1.6f));
            }
        }
    }

    /** 사방에서 순서대로 솟아오르는 암석 기둥. */
    private void rockPillars(World world, Location center, double radius, int step) {
        double r = radius * step / 8.0;
        for (double angle = 0; angle < 360; angle += 45) {
            double radians = Math.toRadians(angle + step * 10);
            Location base = center.clone().add(Math.cos(radians) * r, 0, Math.sin(radians) * r);
            for (double h = 0; h <= 2.5; h += 0.4) {
                world.spawnParticle(Particle.BLOCK, base.clone().add(0, h, 0), 4, 0.15, 0.1, 0.15, 0,
                        org.bukkit.Material.DEEPSLATE.createBlockData());
            }
        }
    }

    private String num(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
