package com.tntbbp.myminecraft.raid;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.OpImmunity;
import com.tntbbp.myminecraft.util.Particles;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 레이드 보스 '종말룡 엔더드래곤'.
 *
 * <p>바닐라 엔더드래곤의 AI는 엔드의 출구 포탈과 엔드 크리스탈을 찾아다니도록 만들어져 있어,
 * 다른 월드에 소환하면 원점(0,0) 쪽으로 날아가 버린다. 그래서 AI를 끄고 이 클래스가 위치와
 * 패턴을 직접 조종한다.
 *
 * <p>낙하 지점의 지형은 일부러 파괴하지 않는다. 서버 월드를 되돌릴 수 없게 망가뜨리는 것보다
 * 피해와 연출로 표현하는 쪽이 안전하다.
 */
public class ApocalypseDragon extends RaidBoss {

    public static final String ID = "apocalypse-dragon";

    /** 천공의 추락이 발동하는 체력 구간. 각 구간은 한 번씩만 쓴다. */
    private static final double[] DIVE_THRESHOLDS = {0.75, 0.5, 0.25};

    private Location anchor;
    private int nextDiveIndex;
    private boolean diving;
    private boolean riftActive;

    private long lastBreathMillis;
    private final List<Location> rifts = new ArrayList<>();

    public ApocalypseDragon(MyMinecraftPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "§5§l종말룡 엔더드래곤";
    }

    @Override
    protected BarColor barColor() {
        return BarColor.PURPLE;
    }

    @Override
    public double maxHealth() {
        return configDouble("max-health", 1200.0);
    }

    @Override
    public double engageRange() {
        return configDouble("engage-range", 40.0);
    }

    @Override
    protected LivingEntity createEntity(Location location) {
        this.anchor = location.clone();

        EnderDragon dragon = location.getWorld().spawn(location.clone().add(0, hoverHeight(), 0),
                EnderDragon.class, spawned -> {
                    spawned.setPhase(EnderDragon.Phase.HOVER);
                    // 바닐라 AI는 엔드 포탈을 찾아다니므로 끄고 직접 조종한다.
                    spawned.setAI(false);
                });

        AttributeInstance damage = dragon.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(configDouble("attack-damage", 15.0));
        }
        return dragon;
    }

    private double hoverHeight() {
        return configDouble("hover-height", 7.0);
    }

    // ----- 주기 처리 -----

    @Override
    protected void tick() {
        if (diving) {
            return;
        }

        Player target = nearestPlayer(engageRange());
        steerToward(target);
        apocalypticRoar();

        if (riftActive) {
            tickRifts();
        } else if (healthRatio() <= configDouble("rift.health-threshold", 0.3)) {
            activateRifts();
        }

        if (nextDiveIndex < DIVE_THRESHOLDS.length && healthRatio() <= DIVE_THRESHOLDS[nextDiveIndex]) {
            nextDiveIndex++;
            if (target != null) {
                skyfallDive(target);
                return;
            }
        }

        long now = System.currentTimeMillis();
        if (target != null && now - lastBreathMillis >= configInt("breath.cooldown-seconds", 15) * 1000L) {
            lastBreathMillis = now;
            doomBreath();
        }
    }

    /** 대상 쪽으로 천천히 떠서 이동하고, 대상을 바라보게 한다. */
    private void steerToward(Player target) {
        Location current = entity().getLocation();
        Location desired = (target == null ? anchor : target.getLocation()).clone();
        desired.setY(groundHeightAt(desired) + hoverHeight());

        Vector delta = desired.toVector().subtract(current.toVector());
        double distance = delta.length();
        double step = configDouble("move-speed", 1.2);

        Location next = distance <= step
                ? desired
                : current.clone().add(delta.normalize().multiply(step));

        if (target != null) {
            Vector look = target.getLocation().toVector().subtract(next.toVector());
            next.setDirection(look);
        }
        entity().teleport(next);
    }

    private double groundHeightAt(Location location) {
        return location.getWorld().getHighestBlockYAt(location) + 1.0;
    }

    // ----- 패시브: 종말의 포효 -----

    /** 주변 플레이어에게 방어력을 무시하는 지속 피해를 주고 위로 밀어올린다. */
    private void apocalypticRoar() {
        double radius = configDouble("roar.radius", 20.0);
        double damage = configDouble("roar.damage-per-tick", 1.5);
        double lift = configDouble("roar.lift", 0.25);

        for (Player player : nearbyPlayers(radius)) {
            if (OpImmunity.isImmune(player)) {
                continue;
            }
            // 방어력을 무시해야 하므로 damage()가 아니라 체력을 직접 깎는다.
            double newHealth = Math.max(0.0, player.getHealth() - damage);
            player.setHealth(newHealth);
            player.setVelocity(player.getVelocity().clone().setY(lift));
        }
    }

    // ----- 패턴: 멸망의 브레스 -----

    /** 전방 부채꼴에 종말의 화염을 내뿜는다. 맞으면 방어구 내구도가 크게 깎이고 불이 붙는다. */
    private void doomBreath() {
        double range = configDouble("breath.range", 18.0);
        double angle = configDouble("breath.cone-angle-degrees", 70.0);
        double damage = configDouble("breath.damage", 18.0);
        int fireTicks = configInt("breath.fire-ticks", 120);
        int armorDamage = configInt("breath.armor-damage", 200);

        broadcastNearby("§5[종말룡] §d멸망의 브레스!", engageRange());

        Location eye = entity().getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        entity().getWorld().playSound(eye, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.6f);

        // 브레스가 뻗어나가는 연출
        new BukkitRunnable() {
            int step = 1;

            @Override
            public void run() {
                if (step > range || !isAlive()) {
                    cancel();
                    return;
                }
                Location point = eye.clone().add(direction.clone().multiply(step));
                Particles.spawn(point.getWorld(), Particle.DRAGON_BREATH, point, 12, 0.6, 0.6, 0.6, 0.02);
                point.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 4, 0.4, 0.4, 0.4, 0.01);
                step += 2;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        for (Player player : playersInCone(range, angle)) {
            if (OpImmunity.isImmune(player)) {
                continue;
            }
            player.damage(damage, entity());
            player.setFireTicks(Math.max(player.getFireTicks(), fireTicks));
            shatterArmor(player, armorDamage);
            player.sendMessage("§5[종말룡] §d방어구가 종말의 화염에 녹아내립니다!");
        }
    }

    /**
     * 방어구 내구도를 크게 깎는다. 다만 완전히 파괴하지는 않고 1만 남겨둔다 — 레이드 도중
     * 장비가 통째로 사라지면 복구할 방법이 없기 때문이다.
     */
    private void shatterArmor(Player player, int amount) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) {
                continue;
            }
            short maxDurability = piece.getType().getMaxDurability();
            if (maxDurability <= 0) {
                continue;
            }
            ItemMeta meta = piece.getItemMeta();
            if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) {
                continue;
            }
            int newDamage = Math.min(maxDurability - 1, damageable.getDamage() + amount);
            damageable.setDamage(newDamage);
            piece.setItemMeta(meta);
            changed = true;
        }

        if (changed) {
            player.getInventory().setArmorContents(armor);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.2f, 0.8f);
        }
    }

    // ----- 패턴: 천공의 추락 -----

    /** 높이 솟아올랐다가 대상 위치로 급강하해 착지 지점에 충격파를 일으킨다. */
    private void skyfallDive(Player target) {
        diving = true;

        double riseHeight = configDouble("dive.rise-height", 25.0);
        int telegraphTicks = configInt("dive.telegraph-ticks", 40);
        double radius = configDouble("dive.shockwave-radius", 8.0);
        double damage = configDouble("dive.shockwave-damage", 30.0);
        double knockUp = configDouble("dive.knock-up", 1.4);

        Location landing = target.getLocation().clone();
        landing.setY(groundHeightAt(landing));

        entity().teleport(entity().getLocation().clone().add(0, riseHeight, 0));
        entity().getWorld().playSound(landing, Sound.ENTITY_ENDER_DRAGON_FLAP, 3.0f, 0.5f);
        broadcastNearby("§5[종말룡] §c§l천공의 추락 §7— 낙하 지점에서 벗어나십시오!", engageRange());

        // 착지 예정 지점을 미리 보여줘서 피할 시간을 준다.
        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (elapsed >= telegraphTicks || !isAlive()) {
                    cancel();
                    if (isAlive()) {
                        impact(landing, radius, damage, knockUp);
                    }
                    diving = false;
                    return;
                }
                elapsed += 4;
                Particles.spawn(landing.getWorld(), Particle.DRAGON_BREATH, landing.clone().add(0, 0.2, 0),
                        40, radius / 2, 0.1, radius / 2, 0.0);
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void impact(Location landing, double radius, double damage, double knockUp) {
        entity().teleport(landing.clone().add(0, 2, 0));

        landing.getWorld().playSound(landing, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
        landing.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, landing, 6, radius / 3, 0.5, radius / 3, 0.0);

        for (Player player : nearbyPlayers(radius)) {
            if (OpImmunity.isImmune(player)) {
                continue;
            }
            double distance = player.getLocation().distance(landing);
            double falloff = Math.max(0.3, 1.0 - distance / radius);
            player.damage(damage * falloff, entity());
            player.setVelocity(player.getLocation().toVector()
                    .subtract(landing.toVector())
                    .setY(knockUp)
                    .normalize()
                    .multiply(0.8));
        }

        // 다시 떠오른다.
        entity().teleport(landing.clone().add(0, hoverHeight(), 0));
    }

    // ----- 궁극기: 공간 분할 -----

    private void activateRifts() {
        riftActive = true;
        int count = configInt("rift.count", 8);
        double spread = configDouble("rift.spread", 25.0);

        Location center = anchor == null ? entity().getLocation() : anchor;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location rift = center.clone().add(
                    random.nextDouble(-spread, spread), 0, random.nextDouble(-spread, spread));
            rift.setY(groundHeightAt(rift));
            rifts.add(rift);
        }

        setBossBarTitle(displayName() + " §7— §5공간 분할");
        broadcastNearby("§5§l공간 분할 §7— 균열이 열리고 회복이 막힙니다!", engageRange());
        entity().getWorld().playSound(entity().getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 1.4f);
    }

    /** 균열을 표시하고, 균열에 닿은 플레이어를 밀어내며 피해를 준다. */
    private void tickRifts() {
        double radius = configDouble("rift.radius", 3.0);
        double damage = configDouble("rift.damage", 3.0);

        for (Location rift : rifts) {
            rift.getWorld().spawnParticle(Particle.REVERSE_PORTAL, rift.clone().add(0, 1.5, 0),
                    30, 0.4, 1.5, 0.4, 0.05);

            for (Player player : rift.getWorld().getNearbyEntities(rift, radius, 3.0, radius).stream()
                    .filter(entity -> entity instanceof Player)
                    .map(entity -> (Player) entity)
                    .toList()) {
                if (OpImmunity.isImmune(player)) {
                    continue;
                }
                player.damage(damage, entity());
                player.setVelocity(player.getLocation().toVector()
                        .subtract(rift.toVector())
                        .setY(0.3)
                        .normalize()
                        .multiply(0.7));
            }
        }
    }

    /** 공간 분할이 열려 있는 동안 전장 안에서는 회복이 통하지 않는다. */
    @Override
    public boolean blocksHealing(Player player) {
        if (!riftActive || OpImmunity.isImmune(player)) {
            return false;
        }
        return nearbyPlayers(configDouble("rift.heal-block-radius", 30.0)).contains(player);
    }

    // ----- 정리 -----

    @Override
    public void onDeath() {
        rifts.clear();
        riftActive = false;
    }

    @Override
    public void remove(boolean removeEntity) {
        rifts.clear();
        riftActive = false;
        super.remove(removeEntity);
    }

    /** 패턴 목록 안내용. */
    public static List<String> patternSummary() {
        return List.of(
                "§7- §f종말의 포효 §7(주변 20블록 상시 방어무시 피해 + 공중으로 밀어냄)",
                "§7- §f멸망의 브레스 §7(전방 부채꼴 화염, 방어구 내구도 파괴)",
                "§7- §f천공의 추락 §7(체력 75/50/25%에서 급강하 충격파 — 표시된 지점에서 벗어날 것)",
                "§7- §f공간 분할 §7(체력 30% 이하: 균열이 열리고 전장 안 회복이 차단됨)"
        );
    }
}
