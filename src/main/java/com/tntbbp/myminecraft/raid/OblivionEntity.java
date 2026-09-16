package com.tntbbp.myminecraft.raid;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.util.OpImmunity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 레이드 보스 '기억할 수 없는 자'.
 *
 * <p>인지를 오염시키는 보스다. 정면에서 눈을 마주보고 있으면 사고가 정지되므로, 화면을 돌린 채
 * 측면이나 후방에서 공격해야 한다.
 *
 * <p>원안의 "미니맵/UI 지우기"는 서버 플러그인이 클라이언트 화면을 직접 건드릴 수 없어 그대로는
 * 구현할 수 없다. 대신 암전(실명+어둠)과 닉네임 숨김으로 같은 혼란을 만든다.
 */
public class OblivionEntity extends RaidBoss {

    public static final String ID = "oblivion";

    private static final String NAMETAG_TEAM = "mm_oblivion";

    /** 플레이어별로 연속해서 보스를 직시한 틱 수 (RaidBossManager 기준 0.5초 단위). */
    private final Map<UUID, Integer> gazeTicks = new HashMap<>();

    /** 궁극기로 닉네임을 숨긴 플레이어와, 그 전에 속해 있던 팀 이름 (복구용). */
    private final Map<UUID, String> hiddenNameTags = new LinkedHashMap<>();

    private long lastWaveMillis;
    private boolean ultimateActive;

    public OblivionEntity(MyMinecraftPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "§8§l기억할 수 없는 자";
    }

    @Override
    protected BarColor barColor() {
        return BarColor.WHITE;
    }

    @Override
    public double maxHealth() {
        return configDouble("max-health", 700.0);
    }

    @Override
    protected LivingEntity createEntity(Location location) {
        Enderman entity = location.getWorld().spawn(location, Enderman.class, spawned -> {
            spawned.setCustomNameVisible(true);
            spawned.setCarriedBlock(null);
        });

        AttributeInstance followRange = entity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(engageRange());
        }
        AttributeInstance knockback = entity.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(0.8);
        }
        AttributeInstance damage = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(configDouble("attack-damage", 10.0));
        }
        return entity;
    }

    // ----- 주기 처리 -----

    @Override
    protected void tick() {
        tickGaze();

        if (!ultimateActive && healthRatio() <= configDouble("ultimate.health-threshold", 0.5)) {
            activateUltimate();
        }
        if (ultimateActive) {
            tickUltimate();
        }

        long now = System.currentTimeMillis();
        if (now - lastWaveMillis >= configInt("wave.cooldown-seconds", 20) * 1000L) {
            lastWaveMillis = now;
            memoryErasureWave();
        }
    }

    // ----- 패시브: 정신 붕괴의 응시 -----

    /**
     * 보스의 정면에 있으면서 보스를 바라보고 있는 플레이어를 찾아, 일정 시간 이상 직시하면
     * 사고 정지에 빠뜨린다. 옆이나 뒤에 있으면 아무리 봐도 걸리지 않으므로, 화면을 돌린 채
     * 측후방에서 때리는 것이 공략법이 된다.
     */
    private void tickGaze() {
        int requiredTicks = requiredGazeTicks();

        for (Player player : nearbyPlayers(gazeRange())) {
            UUID uuid = player.getUniqueId();

            if (plugin.getRaidBossManager().isMindBroken(uuid) || OpImmunity.isImmune(player)) {
                gazeTicks.remove(uuid);
                continue;
            }
            if (!isGazing(player)) {
                gazeTicks.remove(uuid);
                continue;
            }

            int ticks = gazeTicks.merge(uuid, 1, Integer::sum);
            if (ticks < requiredTicks) {
                // 아직 임계치 전 — 경고 신호를 준다.
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.6f, 2.0f);
                player.spawnParticle(Particle.ELDER_GUARDIAN, player.getLocation(), 1);
                continue;
            }

            gazeTicks.remove(uuid);
            breakMind(player);
        }
    }

    private boolean isGazing(Player player) {
        Location bossEye = entity().getEyeLocation();
        Location playerEye = player.getEyeLocation();

        Vector toBoss = bossEye.toVector().subtract(playerEye.toVector());
        double distance = toBoss.length();
        if (distance < 0.001 || distance > gazeRange()) {
            return false;
        }
        Vector toBossDirection = toBoss.clone().normalize();

        // 1) 플레이어가 보스 쪽을 보고 있는가
        double lookingAtBoss = playerEye.getDirection().normalize().dot(toBossDirection);
        if (lookingAtBoss < Math.cos(Math.toRadians(configDouble("gaze.player-angle-degrees", 40.0) / 2.0))) {
            return false;
        }

        // 2) 플레이어가 보스의 정면 범위에 있는가 (측면/후방은 안전)
        double inFrontOfBoss = bossEye.getDirection().normalize().dot(toBossDirection.multiply(-1));
        return inFrontOfBoss >= Math.cos(Math.toRadians(configDouble("gaze.boss-front-angle-degrees", 120.0) / 2.0));
    }

    /** 사고 정지 — 암전 + 이동/스킬/채팅 차단 + 지속 피해. */
    private void breakMind(Player player) {
        int seconds = configInt("gaze.mind-break-seconds", 3);
        plugin.getRaidBossManager().applyMindBreak(player, seconds);

        int ticks = seconds * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 255, false, false, false));

        player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.4f, 0.6f);
        player.sendTitle("§8§l사고 정지", "§7눈을 마주쳤습니다", 0, ticks, 10);
        player.sendMessage("§8[기억할 수 없는 자] §7이자를 쳐다봐서는 안 됩니다.");

        double damagePerSecond = configDouble("gaze.damage-per-second", 4.0);
        new org.bukkit.scheduler.BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (elapsed >= seconds || !player.isOnline() || player.isDead() || !isAlive()) {
                    cancel();
                    return;
                }
                elapsed++;
                player.damage(damagePerSecond, entity());
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private double gazeRange() {
        return configDouble("gaze.range", 8.0);
    }

    /** RaidBossManager가 0.5초마다 tick()을 호출하므로 초를 틱 수로 환산한다. */
    private int requiredGazeTicks() {
        double seconds = configDouble("gaze.required-seconds", 1.5);
        return Math.max(1, (int) Math.round(seconds * 2));
    }

    // ----- 액티브: 기억 소거의 파동 -----

    /**
     * 주변 플레이어의 단축키 슬롯을 일시적으로 봉인한다. 핫바에 있는 아이템 종류마다 바닐라
     * 아이템 쿨다운을 걸어, 그 시간 동안 해당 아이템을 쓸 수 없게 만든다.
     */
    private void memoryErasureWave() {
        double radius = configDouble("wave.radius", 15.0);
        int sealSeconds = configInt("wave.seal-seconds", 5);

        Location center = entity().getLocation();
        center.getWorld().playSound(center, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 2.0f, 0.5f);
        center.getWorld().spawnParticle(Particle.SCULK_SOUL, center.clone().add(0, 1, 0),
                60, radius / 3, 1.5, radius / 3, 0.02);

        boolean affectedAnyone = false;
        for (Player player : nearbyPlayers(radius)) {
            if (OpImmunity.isImmune(player)) {
                continue;
            }
            for (int slot = 0; slot < 9; slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                player.setCooldown(item.getType(), sealSeconds * 20);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0f, 1.4f);
            player.sendMessage("§8[기억할 수 없는 자] §7무엇을 들고 있었는지 잊었습니다. §8(" + sealSeconds + "초)");
            affectedAnyone = true;
        }

        if (affectedAnyone) {
            broadcastNearby("§8§l기억 소거의 파동§7이 퍼집니다!", radius);
        }
    }

    // ----- 궁극기: 존재의 망각 -----

    private void activateUltimate() {
        ultimateActive = true;
        setBossBarTitle(displayName() + " §7— §8존재의 망각");
        setBossBarColor(BarColor.PURPLE);

        Location center = entity().getLocation();
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_NEARBY_CLOSEST, 2.0f, 0.5f);
        broadcastNearby("§8§l존재의 망각 §7— 서로가 누구인지 알 수 없게 됩니다.", engageRange());
    }

    /** 암전을 유지하고 주변 플레이어의 닉네임을 숨긴다. */
    private void tickUltimate() {
        int ticks = configInt("ultimate.darkness-refresh-seconds", 3) * 20;

        for (Player player : nearbyPlayers(configDouble("ultimate.radius", 20.0))) {
            if (OpImmunity.isImmune(player)) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, false, false, false));
            hideNameTag(player);
        }

        entity().getWorld().spawnParticle(Particle.SCULK_CHARGE_POP,
                entity().getLocation().add(0, 1, 0), 10, 1.0, 1.0, 1.0, 0.0);
    }

    private void hideNameTag(Player player) {
        if (hiddenNameTags.containsKey(player.getUniqueId())) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();

        Team team = board.getTeam(NAMETAG_TEAM);
        if (team == null) {
            team = board.registerNewTeam(NAMETAG_TEAM);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }

        // 원래 속해 있던 팀을 기억해뒀다가 나중에 되돌려준다 (다른 플러그인의 팀을 망치지 않도록).
        Team previous = board.getEntryTeam(player.getName());
        hiddenNameTags.put(player.getUniqueId(), previous == null ? "" : previous.getName());
        team.addEntry(player.getName());
    }

    private void restoreNameTags() {
        if (hiddenNameTags.isEmpty()) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(NAMETAG_TEAM);

        for (Map.Entry<UUID, String> entry : hiddenNameTags.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            if (team != null) {
                team.removeEntry(player.getName());
            }
            String previousName = entry.getValue();
            if (!previousName.isEmpty()) {
                Team previous = board.getTeam(previousName);
                if (previous != null) {
                    previous.addEntry(player.getName());
                }
            }
        }
        hiddenNameTags.clear();

        if (team != null && team.getEntries().isEmpty()) {
            team.unregister();
        }
    }

    // ----- 정리 -----

    @Override
    public void onDamage(EntityDamageEvent event) {
        // 엔더맨은 물/비에 피해를 입는데, 보스가 날씨로 죽는 건 의도가 아니다.
        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDeath() {
        cleanUpEffects();
    }

    @Override
    public void remove(boolean removeEntity) {
        cleanUpEffects();
        super.remove(removeEntity);
    }

    private void cleanUpEffects() {
        restoreNameTags();
        gazeTicks.clear();
        ultimateActive = false;
    }

    /** 패턴 목록 안내용. */
    public static java.util.List<String> patternSummary() {
        return java.util.List.of(
                "§7- §f정신 붕괴의 응시 §7(정면에서 눈을 마주보면 사고 정지 — 측후방에서 공격할 것)",
                "§7- §f기억 소거의 파동 §7(주변 플레이어의 핫바 아이템을 일시 봉인)",
                "§7- §f존재의 망각 §7(체력 50% 이하: 암전 + 닉네임 숨김으로 아군 식별 불가)"
        );
    }
}
