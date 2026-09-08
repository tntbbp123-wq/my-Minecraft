package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.InfernalBurnManager;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class LaevateinnListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public LaevateinnListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F키(기본 손 바꾸기 키)를 레바테인의 액티브 스킬 발동 키로 사용한다. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        LaevateinnManager laevateinnManager = plugin.getLaevateinnManager();
        if (!laevateinnManager.isLaevateinn(mainHand)) {
            return;
        }

        event.setCancelled(true);
        long remaining = laevateinnManager.remainingCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "라그나로크의 숨결 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        laevateinnManager.useBreathOfRagnarok(player);
        player.sendMessage(ChatColor.GOLD + "라그나로크의 숨결을 발동했습니다!");
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        LaevateinnManager laevateinnManager = plugin.getLaevateinnManager();
        if (!laevateinnManager.isLaevateinn(player.getInventory().getItemInMainHand())) {
            return;
        }

        InfernalBurnManager burnManager = plugin.getInfernalBurnManager();
        burnManager.applyBurn(target, player.getUniqueId(),
                laevateinnManager.onHitBurnDurationSeconds(), laevateinnManager.onHitBurnDamagePerSecond());
    }

    /** 지옥의 화상 저주가 걸린 대상에게는 바닐라 화염 피해를 적용하지 않는다 (전용 매니저가 직접 처리). */
    @EventHandler
    public void onVanillaFireDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FIRE
                && event.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (plugin.getInfernalBurnManager().isBurning(entity.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** 불타는 동안 치유 효과 감소. */
    @EventHandler
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        InfernalBurnManager burnManager = plugin.getInfernalBurnManager();
        if (!burnManager.isBurning(entity.getUniqueId())) {
            return;
        }
        double reduced = event.getAmount() * (1.0 - burnManager.healReductionPercent() / 100.0);
        event.setAmount(Math.max(0.0, reduced));
    }

    /** 물 양동이를 근처에 뿌리면 '지옥의 화상'을 즉시 해제한다. */
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }
        InfernalBurnManager burnManager = plugin.getInfernalBurnManager();
        event.getBlock().getLocation().getWorld().getNearbyEntities(
                event.getBlock().getLocation().add(0.5, 0.5, 0.5), 2.5, 2.5, 2.5
        ).forEach(entity -> {
            if (entity instanceof LivingEntity livingEntity && burnManager.isBurning(livingEntity.getUniqueId())) {
                burnManager.extinguish(livingEntity);
            }
        });
    }
}
