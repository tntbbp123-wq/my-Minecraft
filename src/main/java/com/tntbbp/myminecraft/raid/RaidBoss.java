package com.tntbbp.myminecraft.raid;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 레이드 보스 공용 뼈대. 스폰/보스바/체력 비율/주변 플레이어 조회처럼 보스마다 똑같이 필요한
 * 부분을 여기서 처리하고, 실제 패턴은 하위 클래스가 {@link #tick()}에서 구현한다.
 * 인스턴스 하나가 소환된 보스 하나에 대응하며, RaidBossManager가 생명주기를 관리한다.
 */
public abstract class RaidBoss {

    private static final double BOSS_BAR_RADIUS = 60.0;

    protected final MyMinecraftPlugin plugin;

    private LivingEntity entity;
    private BossBar bossBar;

    protected RaidBoss(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** config.yml의 raid-boss.&lt;id&gt; 키이자 소환 명령어에서 쓰는 식별자. */
    public abstract String id();

    public abstract String displayName();

    /** 보스의 실제 엔티티를 스폰한다. 체력/이름/보스바는 공용 로직이 뒤이어 설정한다. */
    protected abstract LivingEntity createEntity(Location location);

    /** RaidBossManager가 주기적으로 호출한다 (기본 10틱). */
    protected abstract void tick();

    protected BarColor barColor() {
        return BarColor.RED;
    }

    public double maxHealth() {
        return configDouble("max-health", 800.0);
    }

    /** 패턴이 대상을 탐색하는 기본 반경. */
    public double engageRange() {
        return configDouble("engage-range", 24.0);
    }

    public LivingEntity entity() {
        return entity;
    }

    public boolean isAlive() {
        return entity != null && !entity.isDead() && entity.isValid();
    }

    /** 부활 연출 등 무적 구간에서는 모든 피해가 무시된다. */
    public boolean isInvulnerable() {
        return false;
    }

    public final void spawn(Location location) {
        this.entity = createEntity(location);
        entity.setCustomName(displayName());
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        AttributeInstance maxHealthAttribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHealth());
        }
        entity.setHealth(maxHealth());

        this.bossBar = Bukkit.createBossBar(displayName(), barColor(), BarStyle.SEGMENTED_10);
        bossBar.setProgress(1.0);
    }

    /** 매니저가 호출하는 진입점. 보스바를 갱신하고 하위 클래스의 패턴 로직을 돌린다. */
    public final void update() {
        refreshBossBar();
        tick();
    }

    public double healthRatio() {
        if (!isAlive()) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, entity.getHealth() / maxHealth()));
    }

    private void refreshBossBar() {
        if (bossBar == null || !isAlive()) {
            return;
        }
        bossBar.setProgress(healthRatio());

        List<Player> inRange = nearbyPlayers(BOSS_BAR_RADIUS);
        for (Player player : inRange) {
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
        for (Player shown : new ArrayList<>(bossBar.getPlayers())) {
            if (!inRange.contains(shown)) {
                bossBar.removePlayer(shown);
            }
        }
    }

    /** 보스바 제목을 페이즈 안내 등으로 바꾼다. */
    protected void setBossBarTitle(String title) {
        if (bossBar != null) {
            bossBar.setTitle(title);
        }
    }

    protected void setBossBarColor(BarColor color) {
        if (bossBar != null) {
            bossBar.setColor(color);
        }
    }

    /** 보스를 정리한다 (처치/디스폰 공통). 엔티티 제거 여부는 호출부가 정한다. */
    public void remove(boolean removeEntity) {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (removeEntity && entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    public List<Player> nearbyPlayers(double radius) {
        List<Player> players = new ArrayList<>();
        if (!isAlive()) {
            return players;
        }
        for (Entity nearby : entity.getWorld().getNearbyEntities(entity.getLocation(), radius, radius, radius)) {
            if (nearby instanceof Player player && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                players.add(player);
            }
        }
        return players;
    }

    /** 가장 가까운 플레이어. 없으면 null. */
    protected Player nearestPlayer(double radius) {
        Player closest = null;
        double best = Double.MAX_VALUE;
        for (Player player : nearbyPlayers(radius)) {
            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance < best) {
                best = distance;
                closest = player;
            }
        }
        return closest;
    }

    /** 보스 전방 부채꼴 안에 있는 플레이어들. DrakenPierceManager와 같은 내적 판정을 쓴다. */
    protected List<Player> playersInCone(double range, double coneAngleDegrees) {
        List<Player> result = new ArrayList<>();
        if (!isAlive()) {
            return result;
        }
        Location eye = entity.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double halfAngleCos = Math.cos(Math.toRadians(coneAngleDegrees / 2.0));

        for (Player player : nearbyPlayers(range)) {
            Vector toTarget = player.getLocation().toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance < 0.001 || distance > range) {
                continue;
            }
            if (direction.dot(toTarget.normalize()) < halfAngleCos) {
                continue;
            }
            result.add(player);
        }
        return result;
    }

    /** 보스가 피해를 입을 때 호출된다. 이벤트를 취소하면 피해가 무효화된다. */
    public void onDamage(EntityDamageEvent event) {
    }

    /**
     * 이번 피해로 체력이 0 이하가 될 때 호출된다.
     * @return true면 그대로 사망, false면 피해가 취소되고 보스가 살아남는다 (부활 기믹 등)
     */
    public boolean onLethalDamage(EntityDamageEvent event) {
        return true;
    }

    /** 보스가 스폰한 보조 엔티티(파편/분신 등)를 플레이어가 때렸을 때 호출된다. */
    public void onAuxEntityHit(Entity aux, Player attacker) {
    }

    /** true면 이 플레이어의 회복이 차단된다 (공간 분할 등). */
    public boolean blocksHealing(Player player) {
        return false;
    }

    /** 보스가 실제로 사망했을 때 호출된다. */
    public void onDeath() {
    }

    protected void broadcastNearby(String message, double radius) {
        for (Player player : nearbyPlayers(radius)) {
            player.sendMessage(message);
        }
    }

    protected String configPath(String path) {
        return "raid-boss." + id() + "." + path;
    }

    protected double configDouble(String path, double fallback) {
        return plugin.getConfig().getDouble(configPath(path), fallback);
    }

    protected int configInt(String path, int fallback) {
        return plugin.getConfig().getInt(configPath(path), fallback);
    }
}
