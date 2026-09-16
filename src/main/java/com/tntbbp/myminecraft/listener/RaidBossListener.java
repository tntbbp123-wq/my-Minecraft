package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.RaidBossManager;
import com.tntbbp.myminecraft.raid.RaidBoss;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** 레이드 보스 본체와 보조 엔티티(영혼의 파편 등)에 대한 피해/사망을 해당 보스에게 전달한다. */
public class RaidBossListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public RaidBossListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 보스가 받는 피해를 가로챈다. 무적 페이즈면 전부 무효화하고, 이번 피해로 체력이 0 이하가
     * 되면 보스에게 "진짜 죽을지"를 물어본다 (불사 기믹이 false를 반환하면 피해가 취소된다).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        RaidBossManager manager = plugin.getRaidBossManager();
        RaidBoss boss = manager.byEntity(event.getEntity());
        if (boss == null) {
            return;
        }

        if (boss.isInvulnerable()) {
            event.setCancelled(true);
            return;
        }

        boss.onDamage(event);
        if (event.isCancelled()) {
            return;
        }

        if (boss.entity().getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        if (!boss.onLethalDamage(event)) {
            event.setCancelled(true);
        }
    }

    /** 영혼의 파편 같은 보조 엔티티는 플레이어가 때리면 주인 보스가 처리한다. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAuxEntityDamage(EntityDamageByEntityEvent event) {
        RaidBoss boss = plugin.getRaidBossManager().byAuxEntity(event.getEntity());
        if (boss == null) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        boss.onAuxEntityHit(event.getEntity(), attacker);
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        RaidBossManager manager = plugin.getRaidBossManager();
        RaidBoss boss = manager.byEntity(event.getEntity());
        if (boss == null) {
            return;
        }
        boss.onDeath();
        manager.cleanUp(boss);
    }
}
