package com.tntbbp.myminecraft.raid;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.OpImmunity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 레이드 보스 '끝없는 기사'.
 *
 * <p>정면 승부로는 죽지 않는다. 체력이 0이 되면 사망 대신 <b>쓰러진 상태</b>로 들어가면서
 * 주변에 '영혼의 파편'을 떨어뜨리고, 정해진 시간 안에 파편을 전부 파괴해야만 진짜 사망 판정이
 * 난다. 시간 안에 못 부수면 검기를 방출하며 체력을 모두 회복하고 부활한다 (페이즈 반복).
 *
 * <p>패턴은 스펙에 이름이 명시된 3종(연속 참격 / 반격 태세 / 처형자의 돌진)을 구현했다.
 * 나머지 연계기는 수치가 정해지는 대로 추가한다.
 */
public class EternalKnight extends RaidBoss {

    public static final String ID = "eternal-knight";

    private final NamespacedKey fragmentKey;

    private final Set<UUID> fragments = new LinkedHashSet<>();
    private boolean knockedDown;
    private long knockdownEndsAtMillis;
    private int knockdownCount;

    private long counterStanceEndsAtMillis;
    private long lastComboMillis;
    private long lastCounterMillis;
    private long lastChargeMillis;

    public EternalKnight(MyMinecraftPlugin plugin) {
        super(plugin);
        this.fragmentKey = new NamespacedKey(plugin, "eternal_knight_fragment");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "§5§l끝없는 기사";
    }

    @Override
    protected BarColor barColor() {
        return BarColor.PURPLE;
    }

    @Override
    public double maxHealth() {
        return configDouble("max-health", 600.0);
    }

    @Override
    public boolean isInvulnerable() {
        return knockedDown;
    }

    @Override
    protected LivingEntity createEntity(Location location) {
        WitherSkeleton knight = location.getWorld().spawn(location, WitherSkeleton.class, spawned -> {
            spawned.setSilent(false);
            EntityEquipment equipment = spawned.getEquipment();
            if (equipment != null) {
                equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                equipment.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                equipment.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                equipment.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                equipment.setItemInMainHandDropChance(0.0f);
                equipment.setHelmetDropChance(0.0f);
                equipment.setChestplateDropChance(0.0f);
                equipment.setLeggingsDropChance(0.0f);
                equipment.setBootsDropChance(0.0f);
            }
        });

        AttributeInstance followRange = knight.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(engageRange());
        }
        AttributeInstance knockback = knight.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(1.0);
        }
        applyWillOfImmortality(knight, 0.0);
        return knight;
    }

    // ----- 주기 처리 -----

    @Override
    protected void tick() {
        if (knockedDown) {
            tickKnockdown();
            return;
        }

        applyWillOfImmortality(entity(), 1.0 - healthRatio());

        long now = System.currentTimeMillis();
        if (now < counterStanceEndsAtMillis) {
            entity().getWorld().spawnParticle(Particle.ENCHANTED_HIT, entity().getEyeLocation(), 8, 0.4, 0.6, 0.4, 0.02);
            return;
        }

        Player target = nearestPlayer(engageRange());
        if (target == null) {
            return;
        }

        if (now - lastChargeMillis >= configInt("charge.cooldown-seconds", 12) * 1000L) {
            lastChargeMillis = now;
            executionerCharge(target);
            return;
        }
        if (now - lastCounterMillis >= configInt("counter.cooldown-seconds", 18) * 1000L) {
            lastCounterMillis = now;
            enterCounterStance();
            return;
        }
        if (now - lastComboMillis >= configInt("combo.cooldown-seconds", 8) * 1000L) {
            lastComboMillis = now;
            comboSlash();
        }
    }

    /**
     * 불멸의 의지 — 잃은 체력 비율과 부활 횟수에 비례해 이동속도/공격력이 올라간다.
     * 부활할 때마다 누적되므로 페이즈가 반복될수록 난타전이 된다.
     */
    private void applyWillOfImmortality(LivingEntity knight, double lostRatio) {
        double baseSpeed = configDouble("will.base-movement-speed", 0.28);
        double speedPerRatio = configDouble("will.movement-speed-per-lost-ratio", 0.12);
        double speedPerKnockdown = configDouble("will.movement-speed-per-knockdown", 0.03);

        double baseDamage = configDouble("will.base-attack-damage", 9.0);
        double damagePerRatio = configDouble("will.attack-damage-per-lost-ratio", 6.0);
        double damagePerKnockdown = configDouble("will.attack-damage-per-knockdown", 2.0);

        AttributeInstance speed = knight.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(baseSpeed + speedPerRatio * lostRatio + speedPerKnockdown * knockdownCount);
        }
        AttributeInstance damage = knight.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(baseDamage + damagePerRatio * lostRatio + damagePerKnockdown * knockdownCount);
        }
    }

    // ----- 패턴 1: 연속 참격 -----

    private void comboSlash() {
        int strikes = configInt("combo.strike-count", 5);
        int intervalTicks = configInt("combo.strike-interval-ticks", 5);
        double range = configDouble("combo.range", 4.5);
        double angle = configDouble("combo.cone-angle-degrees", 100.0);
        double damage = configDouble("combo.damage-per-strike", 5.0);

        broadcastNearby("§5[끝없는 기사] §f연속 참격!", engageRange());

        new BukkitRunnable() {
            int done;

            @Override
            public void run() {
                if (done >= strikes || !isAlive() || knockedDown) {
                    cancel();
                    return;
                }
                done++;

                Location eye = entity().getEyeLocation();
                entity().getWorld().playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
                entity().getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                        eye.clone().add(eye.getDirection().multiply(1.5)), 3, 0.5, 0.3, 0.5, 0.0);

                for (Player player : playersInCone(range, angle)) {
                    if (OpImmunity.isImmune(player)) {
                        continue;
                    }
                    player.damage(damage, entity());
                }
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);
    }

    // ----- 패턴 2: 반격 태세 -----

    private void enterCounterStance() {
        counterStanceEndsAtMillis = System.currentTimeMillis() + configInt("counter.duration-seconds", 4) * 1000L;
        entity().getWorld().playSound(entity().getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.5f, 0.6f);
        broadcastNearby("§5[끝없는 기사] §c반격 태세 — 공격을 멈추십시오!", engageRange());
    }

    private boolean isCountering() {
        return System.currentTimeMillis() < counterStanceEndsAtMillis;
    }

    /** 반격 태세 중 근접 공격을 받으면 피해를 무효화하고 공격자에게 카운터 + 스턴을 건다. */
    private void counterAttack(Player attacker) {
        entity().getWorld().playSound(entity().getLocation(), Sound.ITEM_SHIELD_BREAK, 1.5f, 0.8f);

        if (OpImmunity.isImmune(attacker)) {
            attacker.sendMessage("§7(OP 면역) 반격이 무효화되었습니다.");
            return;
        }

        attacker.damage(configDouble("counter.damage", 14.0), entity());

        int stunTicks = configInt("counter.stun-seconds", 2) * 20;
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stunTicks, 255, false, true, true));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, stunTicks, 0, false, true, true));
        attacker.sendMessage("§c반격당했습니다! 잠시 움직일 수 없습니다.");
    }

    // ----- 패턴 3: 처형자의 돌진 -----

    private void executionerCharge(Player target) {
        double speed = configDouble("charge.speed", 1.6);
        double hitRadius = configDouble("charge.hit-radius", 2.5);
        double damage = configDouble("charge.damage", 12.0);
        int durationTicks = configInt("charge.duration-ticks", 20);
        int shieldDisableTicks = configInt("charge.shield-disable-seconds", 5) * 20;

        broadcastNearby("§5[끝없는 기사] §4처형자의 돌진!", engageRange());
        entity().getWorld().playSound(entity().getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 1.2f);

        Vector direction = target.getLocation().toVector()
                .subtract(entity().getLocation().toVector())
                .setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            return;
        }
        entity().setVelocity(direction.normalize().multiply(speed));

        Set<UUID> alreadyHit = new LinkedHashSet<>();
        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (elapsed >= durationTicks || !isAlive() || knockedDown) {
                    cancel();
                    return;
                }
                elapsed += 2;

                Location location = entity().getLocation();
                entity().getWorld().spawnParticle(Particle.CRIT, location, 10, 0.4, 0.4, 0.4, 0.1);

                for (Player player : nearbyPlayers(hitRadius)) {
                    if (!alreadyHit.add(player.getUniqueId()) || OpImmunity.isImmune(player)) {
                        continue;
                    }
                    player.damage(damage, entity());
                    // "경로상의 모든 방어막을 무력화" — 방패를 일정 시간 사용 불가로 만든다.
                    player.setCooldown(Material.SHIELD, shieldDisableTicks);
                    player.sendMessage("§4돌진에 휩쓸려 방패가 부서졌습니다!");
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ----- 불사의 육체 / 처치 기믹 -----

    @Override
    public void onDamage(EntityDamageEvent event) {
        if (!isCountering()) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player attacker) {
            event.setCancelled(true);
            counterAttack(attacker);
        }
    }

    @Override
    public boolean onLethalDamage(EntityDamageEvent event) {
        beginKnockdown();
        return false;
    }

    /** 체력이 0이 되는 순간 사망 대신 쓰러진 상태로 들어가고, 영혼의 파편을 떨어뜨린다. */
    private void beginKnockdown() {
        knockedDown = true;
        knockdownEndsAtMillis = System.currentTimeMillis() + configInt("knockdown.duration-seconds", 6) * 1000L;

        LivingEntity knight = entity();
        knight.setHealth(1.0);
        knight.setAI(false);
        knight.getWorld().playSound(knight.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.5f, 1.6f);

        spawnSoulFragments();

        setBossBarTitle(displayName() + " §7— §e영혼의 파편을 파괴하십시오!");
        setBossBarColor(BarColor.YELLOW);
        broadcastNearby("§e[끝없는 기사] §f쓰러졌습니다! §6영혼의 파편 " + fragments.size() + "개§f를 "
                + configInt("knockdown.duration-seconds", 6) + "초 안에 파괴하십시오!", engageRange());
    }

    private void spawnSoulFragments() {
        int count = configInt("knockdown.fragment-count", 4);
        double radius = configDouble("knockdown.fragment-radius", 4.0);
        Location center = entity().getLocation();

        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            Location spot = center.clone().add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);

            ArmorStand fragment = center.getWorld().spawn(spot, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setSmall(true);
                stand.setGravity(false);
                stand.setInvulnerable(false);
                stand.setGlowing(true);
                stand.setCustomName("§b영혼의 파편");
                stand.setCustomNameVisible(true);
                if (stand.getEquipment() != null) {
                    stand.getEquipment().setHelmet(new ItemStack(Material.SOUL_LANTERN));
                }
                stand.getPersistentDataContainer().set(fragmentKey, PersistentDataType.BYTE, (byte) 1);
            });

            fragments.add(fragment.getUniqueId());
            plugin.getRaidBossManager().registerAuxEntity(this, fragment);
        }
    }

    private void tickKnockdown() {
        LivingEntity knight = entity();
        knight.getWorld().spawnParticle(Particle.DUST, knight.getLocation().add(0, 1, 0), 12, 0.5, 0.8, 0.5,
                new Particle.DustOptions(Color.fromRGB(120, 60, 200), 1.4f));

        for (UUID fragmentId : new ArrayList<>(fragments)) {
            Entity fragment = plugin.getServer().getEntity(fragmentId);
            if (fragment == null || fragment.isDead()) {
                fragments.remove(fragmentId);
                continue;
            }
            fragment.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, fragment.getLocation().add(0, 0.5, 0),
                    4, 0.15, 0.2, 0.15, 0.01);
        }

        if (fragments.isEmpty()) {
            finishOff();
            return;
        }
        if (System.currentTimeMillis() >= knockdownEndsAtMillis) {
            revive();
        }
    }

    @Override
    public void onAuxEntityHit(Entity aux, Player attacker) {
        if (!aux.getPersistentDataContainer().has(fragmentKey, PersistentDataType.BYTE)) {
            return;
        }
        if (!fragments.remove(aux.getUniqueId())) {
            return;
        }

        plugin.getRaidBossManager().unregisterAuxEntity(aux);
        aux.getWorld().playSound(aux.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.4f, 1.3f);
        aux.getWorld().spawnParticle(Particle.SOUL, aux.getLocation().add(0, 0.5, 0), 25, 0.3, 0.4, 0.3, 0.05);
        aux.remove();

        broadcastNearby("§b영혼의 파편을 파괴했습니다! §7(남은 파편: " + fragments.size() + "개)", engageRange());

        if (fragments.isEmpty() && knockedDown) {
            finishOff();
        }
    }

    /** 파편을 모두 부순 경우 — 진짜 사망 판정. */
    private void finishOff() {
        knockedDown = false;
        clearFragments();

        LivingEntity knight = entity();
        knight.setAI(true);
        knight.getWorld().playSound(knight.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.5f, 1.0f);
        broadcastNearby("§6[끝없는 기사] §f영혼이 흩어졌습니다. 기사는 다시 일어서지 못합니다.", engageRange());

        knight.setHealth(0.0);
    }

    /** 제한 시간 안에 파편을 못 부순 경우 — 검기를 방출하며 완전 회복 후 부활. */
    private void revive() {
        knockedDown = false;
        knockdownCount++;
        clearFragments();

        LivingEntity knight = entity();
        knight.setAI(true);
        knight.setHealth(maxHealth());
        applyWillOfImmortality(knight, 0.0);

        setBossBarTitle(displayName() + " §7— §c부활 " + knockdownCount + "회");
        setBossBarColor(BarColor.PURPLE);

        Location location = knight.getLocation();
        knight.getWorld().playSound(location, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.8f);
        knight.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location.add(0, 1, 0), 30, 2.5, 1.0, 2.5, 0.0);

        double waveRadius = configDouble("knockdown.revive-wave-radius", 6.0);
        double waveDamage = configDouble("knockdown.revive-wave-damage", 16.0);
        for (Player player : nearbyPlayers(waveRadius)) {
            if (OpImmunity.isImmune(player)) {
                continue;
            }
            player.damage(waveDamage, knight);
            player.setVelocity(player.getLocation().toVector()
                    .subtract(knight.getLocation().toVector())
                    .setY(0.5)
                    .normalize()
                    .multiply(1.1));
        }

        broadcastNearby("§c[끝없는 기사] §f검기를 방출하며 다시 일어섭니다! §7(부활 " + knockdownCount + "회)", engageRange());
    }

    private void clearFragments() {
        for (UUID fragmentId : fragments) {
            Entity fragment = plugin.getServer().getEntity(fragmentId);
            if (fragment != null) {
                plugin.getRaidBossManager().unregisterAuxEntity(fragment);
                fragment.remove();
            }
        }
        fragments.clear();
    }

    @Override
    public void onDeath() {
        clearFragments();
    }

    @Override
    public void remove(boolean removeEntity) {
        clearFragments();
        super.remove(removeEntity);
    }

    /** 다른 클래스(리스너)에서 파편 여부를 판단할 때 쓴다. */
    public NamespacedKey fragmentKey() {
        return fragmentKey;
    }

    public int knockdownCount() {
        return knockdownCount;
    }

    /** 패턴 목록 안내용. */
    public static List<String> patternSummary() {
        return List.of(
                "§7- §f불사의 육체 §7(체력 0 → 쓰러짐, 영혼의 파편을 시간 내 파괴해야 진짜 사망)",
                "§7- §f연속 참격 §7(전방 부채꼴 5연타)",
                "§7- §f반격 태세 §7(자세 중 근접 공격 시 카운터 + 스턴)",
                "§7- §f처형자의 돌진 §7(직선 돌진, 방패 사용 불가)",
                "§7- §f불멸의 의지 §7(체력이 닳고 부활할수록 이동속도·공격력 상승)"
        );
    }
}
