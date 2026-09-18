package com.tntbbp.myminecraft.listener.raid;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.raid.RaidBossManager;
import com.tntbbp.myminecraft.raid.RaidBoss;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

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

    /** 회복을 막는 보스가 하나라도 있으면 회복을 취소한다 ('종말룡'의 공간 분할). */
    @EventHandler(ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        for (RaidBoss boss : plugin.getRaidBossManager().activeBosses()) {
            if (boss.blocksHealing(player)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ----- 사고 정지 중 행동 차단 ('기억할 수 없는 자'의 응시) -----

    /** 이동은 포션 효과로도 거의 막히지만, 순간이동성 이동까지 확실히 묶기 위해 좌표 이동만 되돌린다. */
    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenMove(PlayerMoveEvent event) {
        if (!plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()
                && from.getBlockY() == to.getBlockY())) {
            return;
        }
        // 시점 회전은 그대로 두고 위치만 고정한다.
        event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                to.getYaw(), to.getPitch()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§8사고가 정지되어 아무것도 할 수 없습니다.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenChat(AsyncPlayerChatEvent event) {
        if (plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** 우클릭 스킬과 F·Q 키 발동(레바테인·드라켄피어스·글레이프니르 등)을 모두 막는다. */
    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenInteract(PlayerInteractEvent event) {
        if (plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMindBrokenDrop(PlayerDropItemEvent event) {
        if (plugin.getRaidBossManager().isMindBroken(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
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
