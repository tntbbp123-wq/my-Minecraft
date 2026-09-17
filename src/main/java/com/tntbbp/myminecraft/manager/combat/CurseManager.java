package com.tntbbp.myminecraft.manager.combat;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.weapon.GleipnirManager;
import com.tntbbp.myminecraft.util.OpImmunity;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 신화 등급 무기들이 공통으로 쓰는 저주/상태이상 관리자.
 *
 * <p>다루는 상태는 네 가지다.
 * <ul>
 *   <li><b>저주</b> — 방어력과 치유 효과를 깎는다 (벽사의 저주, 파프니르의 저주 등)</li>
 *   <li><b>침묵</b> — 무기 스킬을 쓸 수 없다</li>
 *   <li><b>제압</b> — 움직이지도 때리지도 못한다</li>
 *   <li><b>면역</b> — 궁극기 시전 중 위 세 가지와 나쁜 포션 효과를 전부 무시한다</li>
 * </ul>
 *
 * <p>실제 피해/치유 보정과 이동 차단은 {@link com.tntbbp.myminecraft.listener.CurseListener}가 맡는다.
 */
public class CurseManager {

    private static final int TICK_INTERVAL = 10; // 0.5초마다 만료 정리 및 둔화 갱신

    /** 대상 하나에게 걸린 저주. slowAmplifier가 음수면 둔화 없음. */
    public record CurseState(String displayName, double defenseReducePercent, double healReducePercent,
                             int slowAmplifier, long expiresAtMillis) {
    }

    private final MyMinecraftPlugin plugin;
    private final Map<UUID, CurseState> curses = new ConcurrentHashMap<>();
    private final Map<UUID, Long> silencedUntilMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stunnedUntilMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Location> stunAnchors = new ConcurrentHashMap<>();
    private final Map<UUID, Long> immuneUntilMillis = new ConcurrentHashMap<>();
    private final Set<UUID> trueDamagePending = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public CurseManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        // 제압당한 채로 서버가 꺼지면 몹의 AI가 영영 꺼진 채 남으므로 전부 되돌려준다.
        for (UUID uuid : stunnedUntilMillis.keySet()) {
            restoreAi(uuid);
        }
        curses.clear();
        silencedUntilMillis.clear();
        stunnedUntilMillis.clear();
        stunAnchors.clear();
        immuneUntilMillis.clear();
    }

    // ----- 저주 -----

    /**
     * 대상에게 저주를 건다. 이미 걸려 있으면 더 강한 쪽/더 긴 쪽으로 갱신된다.
     *
     * @param slowAmplifier 0 이상이면 지속시간 동안 둔화를 계속 걸어둔다. 음수면 둔화 없음.
     */
    public void applyCurse(LivingEntity target, String displayName, double defenseReducePercent,
                           double healReducePercent, int slowAmplifier, int durationSeconds) {
        if (OpImmunity.isImmune(target) || isImmune(target.getUniqueId())) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + durationSeconds * 1000L;
        CurseState existing = curses.get(target.getUniqueId());
        if (existing != null) {
            expiresAt = Math.max(expiresAt, existing.expiresAtMillis());
            defenseReducePercent = Math.max(defenseReducePercent, existing.defenseReducePercent());
            healReducePercent = Math.max(healReducePercent, existing.healReducePercent());
            slowAmplifier = Math.max(slowAmplifier, existing.slowAmplifier());
        }
        curses.put(target.getUniqueId(),
                new CurseState(displayName, defenseReducePercent, healReducePercent, slowAmplifier, expiresAt));
        if (slowAmplifier >= 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, TICK_INTERVAL * 4, slowAmplifier,
                    false, true, true));
        }
        if (target instanceof Player player) {
            player.sendMessage(ChatColor.DARK_PURPLE + displayName + "에 걸렸습니다! (" + durationSeconds + "초)");
        }
    }

    public boolean isCursed(UUID uuid) {
        return activeCurse(uuid) != null;
    }

    public double defenseReducePercent(UUID uuid) {
        CurseState state = activeCurse(uuid);
        return state == null ? 0.0 : state.defenseReducePercent();
    }

    public double healReducePercent(UUID uuid) {
        CurseState state = activeCurse(uuid);
        return state == null ? 0.0 : state.healReducePercent();
    }

    private CurseState activeCurse(UUID uuid) {
        CurseState state = curses.get(uuid);
        if (state == null) {
            return null;
        }
        if (state.expiresAtMillis() <= System.currentTimeMillis()) {
            curses.remove(uuid);
            return null;
        }
        return state;
    }

    // ----- 침묵 (스킬 봉인) -----

    public void applySilence(LivingEntity target, int durationSeconds) {
        if (OpImmunity.isImmune(target) || isImmune(target.getUniqueId())) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + durationSeconds * 1000L;
        silencedUntilMillis.merge(target.getUniqueId(), expiresAt, Math::max);
        if (target instanceof Player player) {
            player.sendMessage(ChatColor.DARK_GRAY + "침묵! " + durationSeconds + "초 동안 스킬을 쓸 수 없습니다.");
        }
    }

    public boolean isSilenced(UUID uuid) {
        return remainingSeconds(silencedUntilMillis, uuid) > 0;
    }

    public long remainingSilenceSeconds(UUID uuid) {
        return remainingSeconds(silencedUntilMillis, uuid);
    }

    // ----- 제압 (행동 불능) -----

    public void applyStun(LivingEntity target, int durationSeconds) {
        if (OpImmunity.isImmune(target) || isImmune(target.getUniqueId())) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + durationSeconds * 1000L;
        stunnedUntilMillis.merge(target.getUniqueId(), expiresAt, Math::max);
        stunAnchors.putIfAbsent(target.getUniqueId(), target.getLocation().clone());
        if (target instanceof Mob mob) {
            mob.setAI(false);
        }
        if (target instanceof Player player) {
            player.sendMessage(ChatColor.DARK_RED + "제압당했습니다! " + durationSeconds + "초 동안 움직일 수 없습니다.");
        }
    }

    public boolean isStunned(UUID uuid) {
        return remainingSeconds(stunnedUntilMillis, uuid) > 0;
    }

    /** 제압당한 순간의 위치. 이동을 되돌릴 때 쓴다. */
    public Location stunAnchor(UUID uuid) {
        return stunAnchors.get(uuid);
    }

    // ----- 면역 (궁극기 시전 중) -----

    /** 시전 중 모든 상태 이상 면역. 이미 걸려 있던 저주/침묵/제압과 나쁜 포션 효과도 함께 걷어낸다. */
    public void grantImmunity(Player player, int durationTicks) {
        immuneUntilMillis.merge(player.getUniqueId(), System.currentTimeMillis() + durationTicks * 50L, Math::max);
        curses.remove(player.getUniqueId());
        silencedUntilMillis.remove(player.getUniqueId());
        clearStun(player.getUniqueId());
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (isHarmful(effect.getType())) {
                player.removePotionEffect(effect.getType());
            }
        }
    }

    public boolean isImmune(UUID uuid) {
        return remainingMillis(immuneUntilMillis, uuid) > 0;
    }

    public static boolean isHarmful(PotionEffectType type) {
        return type.equals(PotionEffectType.SLOWNESS)
                || type.equals(PotionEffectType.MINING_FATIGUE)
                || type.equals(PotionEffectType.NAUSEA)
                || type.equals(PotionEffectType.BLINDNESS)
                || type.equals(PotionEffectType.HUNGER)
                || type.equals(PotionEffectType.WEAKNESS)
                || type.equals(PotionEffectType.POISON)
                || type.equals(PotionEffectType.WITHER)
                || type.equals(PotionEffectType.LEVITATION)
                || type.equals(PotionEffectType.UNLUCK)
                || type.equals(PotionEffectType.DARKNESS)
                || type.equals(PotionEffectType.GLOWING);
    }

    // ----- 고정 피해 (방어력 무시) -----

    /**
     * 방어력·저항·보호 마법을 전부 무시하는 고정 피해를 입힌다.
     *
     * <p>체력을 직접 깎지 않고 정상적인 피해 이벤트로 처리하기 때문에 넉백, 전투 태그, 사망 기여자
     * 기록이 모두 그대로 남는다. 감면만 {@link com.tntbbp.myminecraft.listener.CurseListener}에서
     * 0으로 만든다.
     */
    public void dealTrueDamage(LivingEntity target, Player source, double amount) {
        if (OpImmunity.isImmune(target)) {
            return;
        }
        trueDamagePending.add(target.getUniqueId());
        try {
            target.damage(amount, source);
        } finally {
            trueDamagePending.remove(target.getUniqueId());
        }
    }

    public boolean isTrueDamagePending(UUID uuid) {
        return trueDamagePending.contains(uuid);
    }

    // ----- 스킬 사용 가능 여부 (모든 무기 리스너 공용) -----

    /**
     * 글레이프니르의 봉인이나 침묵에 걸려 스킬을 쓸 수 없는 상태면 안내 메시지를 보내고 true를 돌려준다.
     * 각 무기 리스너는 스킬을 발동하기 전에 이걸 먼저 확인한다.
     */
    public boolean blockSkill(Player player) {
        GleipnirManager gleipnirManager = plugin.getGleipnirManager();
        if (gleipnirManager.isSealed(player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "봉인되어 스킬을 쓸 수 없습니다. ("
                    + gleipnirManager.remainingSealSeconds(player.getUniqueId()) + "초)");
            return true;
        }
        if (isSilenced(player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "침묵 상태라 스킬을 쓸 수 없습니다. ("
                    + remainingSilenceSeconds(player.getUniqueId()) + "초)");
            return true;
        }
        if (isStunned(player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "제압당해 아무것도 할 수 없습니다.");
            return true;
        }
        return false;
    }

    // ----- 내부 -----

    private void tick() {
        long now = System.currentTimeMillis();
        curses.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
        silencedUntilMillis.entrySet().removeIf(entry -> entry.getValue() <= now);
        immuneUntilMillis.entrySet().removeIf(entry -> entry.getValue() <= now);

        for (Map.Entry<UUID, Long> entry : stunnedUntilMillis.entrySet()) {
            if (entry.getValue() <= now) {
                clearStun(entry.getKey());
            }
        }

        // 둔화를 동반한 저주는 상대가 우유를 마셔도 풀리지 않도록 계속 다시 걸어준다.
        for (Map.Entry<UUID, CurseState> entry : curses.entrySet()) {
            CurseState state = entry.getValue();
            if (state.slowAmplifier() < 0) {
                continue;
            }
            if (plugin.getServer().getEntity(entry.getKey()) instanceof LivingEntity entity) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, TICK_INTERVAL * 4,
                        state.slowAmplifier(), false, true, true));
            }
        }
    }

    private void clearStun(UUID uuid) {
        stunnedUntilMillis.remove(uuid);
        stunAnchors.remove(uuid);
        restoreAi(uuid);
    }

    private void restoreAi(UUID uuid) {
        if (plugin.getServer().getEntity(uuid) instanceof Mob mob) {
            mob.setAI(true);
        }
    }

    private long remainingSeconds(Map<UUID, Long> map, UUID uuid) {
        return (remainingMillis(map, uuid) + 999) / 1000;
    }

    private long remainingMillis(Map<UUID, Long> map, UUID uuid) {
        Long until = map.get(uuid);
        if (until == null) {
            return 0;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            map.remove(uuid);
            return 0;
        }
        return remaining;
    }
}
