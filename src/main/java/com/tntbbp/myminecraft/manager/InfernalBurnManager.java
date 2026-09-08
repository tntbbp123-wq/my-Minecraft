package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.model.BurnState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * '지옥의 화상' 지속 효과 관리자.
 * 일반 화염과 달리 방어력을 무시하는 고정 피해를 매초 직접 적용하고, 시간/블록 설치로는 꺼지지 않으며
 * 다량의 물(연속으로 물에 젖음, 또는 근처에 물 양동이 사용)로만 해제된다.
 */
public class InfernalBurnManager {

    private static final int TICK_INTERVAL = 20; // 1초마다 판정

    private final MyMinecraftPlugin plugin;
    private final Map<UUID, BurnState> burning = new ConcurrentHashMap<>();
    private BukkitTask task;

    public InfernalBurnManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    public int waterExtinguishTicks() {
        return plugin.getConfig().getInt("laevateinn.water-extinguish-ticks", 60);
    }

    public double healReductionPercent() {
        return plugin.getConfig().getDouble("laevateinn.heal-reduction-percent", 50.0);
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        burning.clear();
    }

    public boolean isBurning(UUID uuid) {
        return burning.containsKey(uuid);
    }

    /** durationSeconds 동안, 초당 damagePerSecond 만큼 방어력 무시 피해를 입히는 저주를 건다. 이미 걸려 있다면 갱신한다. */
    public void applyBurn(LivingEntity target, UUID sourceId, int durationSeconds, double damagePerSecond) {
        burning.put(target.getUniqueId(), new BurnState(sourceId, durationSeconds * TICK_INTERVAL, damagePerSecond));
        target.setFireTicks(Math.max(target.getFireTicks(), TICK_INTERVAL * 2));
    }

    public void extinguish(LivingEntity entity) {
        burning.remove(entity.getUniqueId());
        entity.setFireTicks(0);
    }

    private void tick() {
        if (burning.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, BurnState> entry : burning.entrySet()) {
            UUID uuid = entry.getKey();
            BurnState state = entry.getValue();

            LivingEntity entity = (LivingEntity) plugin.getServer().getEntity(uuid);
            if (entity == null || entity.isDead() || !entity.isValid()) {
                burning.remove(uuid);
                continue;
            }

            if (entity.isInWater()) {
                state.addWetTicks(TICK_INTERVAL);
                if (state.getWetTicks() >= waterExtinguishTicks()) {
                    extinguish(entity);
                    continue;
                }
            } else {
                state.resetWetTicks();
            }

            double newHealth = Math.max(0.0, entity.getHealth() - state.getDamagePerSecond());
            entity.setHealth(newHealth);
            entity.setFireTicks(Math.max(entity.getFireTicks(), TICK_INTERVAL * 2));

            state.reduceTicks(TICK_INTERVAL);
            if (state.isExpired() || newHealth <= 0.0) {
                burning.remove(uuid);
                if (newHealth > 0.0) {
                    entity.setFireTicks(0);
                }
            }
        }
    }
}
