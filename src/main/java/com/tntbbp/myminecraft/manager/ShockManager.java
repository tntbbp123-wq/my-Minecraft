package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.model.ShockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 드라켄피어스의 '감전' 지속 효과 관리자.
 * 방어력을 무시하는 고정 피해를 매초 직접 적용하면서, 동시에 둔화(Slowness) 효과를 계속 갱신한다.
 */
public class ShockManager {

    private static final int TICK_INTERVAL = 20; // 1초마다 판정

    private final MyMinecraftPlugin plugin;
    private final Map<UUID, ShockState> shocked = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ShockManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        shocked.clear();
    }

    public boolean isShocked(UUID uuid) {
        return shocked.containsKey(uuid);
    }

    /** durationSeconds 동안, 초당 damagePerSecond 만큼 방어력 무시 피해를 입히며 둔화를 유지시키는 감전을 건다. */
    public void applyShock(LivingEntity target, UUID sourceId, int durationSeconds, double damagePerSecond, int slowAmplifier) {
        shocked.put(target.getUniqueId(), new ShockState(sourceId, durationSeconds * TICK_INTERVAL, damagePerSecond, slowAmplifier));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, TICK_INTERVAL * 2, Math.max(0, slowAmplifier), false, true, true));
    }

    private void tick() {
        if (shocked.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, ShockState> entry : shocked.entrySet()) {
            UUID uuid = entry.getKey();
            ShockState state = entry.getValue();

            LivingEntity entity = (LivingEntity) plugin.getServer().getEntity(uuid);
            if (entity == null || entity.isDead() || !entity.isValid()) {
                shocked.remove(uuid);
                continue;
            }

            double newHealth = Math.max(0.0, entity.getHealth() - state.getDamagePerSecond());
            entity.setHealth(newHealth);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, TICK_INTERVAL * 2,
                    Math.max(0, state.getSlowAmplifier()), false, true, true));

            state.reduceTicks(TICK_INTERVAL);
            if (state.isExpired() || newHealth <= 0.0) {
                shocked.remove(uuid);
            }
        }
    }
}
