package com.tntbbp.myminecraft.listener.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.weapon.VoltSaberManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** 볼트 세이버의 일렉트릭 필드 패시브와 [F] 소닉 랜스 / [웅크리기+F] 라이트닝 오라. */
public class VoltSaberListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public VoltSaberListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F = 소닉 랜스, 웅크리기+F = 라이트닝 오라(두른 상태면 방출). */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        VoltSaberManager manager = plugin.getVoltSaberManager();
        if (!manager.isVoltSaber(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        if (player.isSneaking()) {
            // 이미 둘렀다면 두 번째 입력은 방출이다. 쿨타임은 두를 때 이미 돌기 시작했다.
            if (manager.isAuraActive(player.getUniqueId())) {
                manager.discharge(player);
                return;
            }
            if (!manager.startAura(player)) {
                player.sendMessage(ChatColor.RED + "라이트닝 오라 재사용 대기 중입니다. ("
                        + manager.remainingAuraCooldown(player.getUniqueId()) + "초)");
            }
            return;
        }

        if (manager.useSonicLance(player)) {
            player.sendMessage(ChatColor.AQUA + "소닉 랜스!");
        } else {
            player.sendMessage(ChatColor.RED + "소닉 랜스 재사용 대기 중입니다. ("
                    + manager.remainingLanceCooldown(player.getUniqueId()) + "초)");
        }
    }

    /** 치명타 고정 + 라이트닝 오라 증폭 + 모인 전하 소모. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        VoltSaberManager manager = plugin.getVoltSaberManager();
        if (!manager.isVoltSaber(attacker.getInventory().getItemInMainHand())) {
            return;
        }

        if (event.isCritical()) {
            event.setDamage(manager.critDamage());
        }
        double multiplier = manager.auraMultiplier(attacker.getUniqueId());
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
        manager.consumeCharge(attacker, target);
    }
}
