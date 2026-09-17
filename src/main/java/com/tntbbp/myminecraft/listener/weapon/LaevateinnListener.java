package com.tntbbp.myminecraft.listener.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.combat.InfernalBurnManager;
import com.tntbbp.myminecraft.manager.weapon.LaevateinnManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class LaevateinnListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public LaevateinnListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F키(기본 손 바꾸기 키) = 수르트의 불길. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        LaevateinnManager laevateinnManager = plugin.getLaevateinnManager();
        if (!laevateinnManager.isLaevateinn(mainHand)) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        long remaining = laevateinnManager.remainingCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "수르트의 불길 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        laevateinnManager.useSurtrFlame(player);
        player.sendMessage(ChatColor.GOLD + "수르트의 불길을 발동했습니다!");
    }

    /** 웅크리기 + 우클릭 = 라그나로크. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        LaevateinnManager laevateinnManager = plugin.getLaevateinnManager();
        if (!laevateinnManager.isLaevateinn(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        long remaining = laevateinnManager.remainingRagnarokCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "라그나로크 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        laevateinnManager.useRagnarok(player);
        player.sendMessage(ChatColor.DARK_RED + "라그나로크! " + ChatColor.GRAY + "아홉 세계를 태우는 불길이 열립니다.");
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

        int stacks = plugin.getInfernalBurnManager().applyKarmaStack(target, player.getUniqueId(),
                laevateinnManager.karmaDurationSeconds(), laevateinnManager.karmaDamagePerSecondPerStack(),
                laevateinnManager.karmaMaxStacks());
        if (stacks > 0) {
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    "§6업화 §f" + stacks + "§7/" + laevateinnManager.karmaMaxStacks() + " 중첩"));
        }
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
