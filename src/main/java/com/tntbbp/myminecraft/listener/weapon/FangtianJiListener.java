package com.tntbbp.myminecraft.listener.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.weapon.FangtianJiManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** 방천화극의 투신의 분노 패시브와 [F] 패왕의 일격. */
public class FangtianJiListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public FangtianJiListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F = 패왕의 일격. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        FangtianJiManager manager = plugin.getFangtianJiManager();
        if (!manager.isFangtianJi(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        if (manager.useOverlordStrike(player)) {
            player.sendMessage(ChatColor.GREEN + "패왕의 일격!");
        } else {
            player.sendMessage(ChatColor.RED + "패왕의 일격 재사용 대기 중입니다. ("
                    + manager.remainingStrikeCooldown(player.getUniqueId()) + "초)");
        }
    }

    /**
     * 치명타 피해 고정 + 광역 쓸어버리기.
     *
     * <p>바닐라 쓸어버리기는 범위를 건드릴 수 없어서, 평타가 들어갈 때 주변 적을 직접 한 번 더
     * 때리는 방식으로 구현했다. 분노 상태(체력 절반 이하)면 그 반경이 두 배가 된다.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity hit)) {
            return;
        }
        FangtianJiManager manager = plugin.getFangtianJiManager();
        if (!manager.isFangtianJi(attacker.getInventory().getItemInMainHand())) {
            return;
        }
        // 쓸어버리기·패왕의 일격이 입힌 피해는 평타가 아니다. 여기서 또 쓸어버리면 연쇄로 번진다.
        if (manager.isDealingNonBasic(attacker.getUniqueId())) {
            return;
        }

        if (event.isCritical()) {
            event.setDamage(manager.critDamage());
        }

        double sweepDamage = event.getDamage() * manager.sweepDamagePercent() / 100.0;
        if (sweepDamage <= 0) {
            return;
        }
        double radius = manager.sweepRadius() * (manager.isEnraged(attacker) ? 2.0 : 1.0);
        manager.spawnSweepArc(attacker, hit, radius);
        for (LivingEntity nearby : manager.sweepTargets(attacker, hit)) {
            manager.dealNonBasic(attacker, nearby, sweepDamage);
        }
    }
}
