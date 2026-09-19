package com.tntbbp.myminecraft.listener.combat;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.combat.BountyManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 현상금 표식의 피해 보정.
 *
 * <p>표식을 받은 쪽은 <b>받는 피해</b>가 늘고, 동시에 <b>주는 피해</b>도 는다. 두 배율은 곱해서
 * 한 번에 적용한다 (때린 사람의 표식 × 맞은 사람의 표식).
 *
 * <p>{@code HIGH}로 받는 이유는 방어관통 같은 다른 보정이 {@code NORMAL}에서 피해 값을 손본 뒤에
 * 마지막으로 비율을 곱하기 위해서다. 취소된 피해는 건드리지 않는다.
 */
public class BountyListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public BountyListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        BountyManager manager = plugin.getBountyManager();
        if (!manager.enabled()) {
            return;
        }

        double multiplier = 1.0;

        Player attacker = attacker(event);
        if (attacker != null) {
            multiplier *= manager.damageDealtMultiplier(attacker);
        }
        if (event.getEntity() instanceof Player victim) {
            multiplier *= manager.damageTakenMultiplier(victim);
        }

        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    /** 때린 사람. 화살·삼지창처럼 쏜 것이면 쏜 플레이어를 찾는다. */
    private Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
