package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.SainchamsagumManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** 사인참사검의 [F] 인시의 참격과 [웅크리기+우클릭] 칠성강림 발동 처리. */
public class SainchamsagumListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public SainchamsagumListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F키(기본 손 바꾸기 키) = 인시의 참격. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        SainchamsagumManager manager = plugin.getSainchamsagumManager();
        if (!manager.isSainchamsagum(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        long remaining = manager.remainingSlashCooldown(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "인시의 참격 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        manager.useTigerHourSlash(player);
        player.sendMessage(ChatColor.DARK_RED + "인시의 참격! " + ChatColor.GRAY + "벽사의 저주를 새깁니다.");
    }

    /** 웅크리기 + 우클릭 = 칠성강림. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        SainchamsagumManager manager = plugin.getSainchamsagumManager();
        if (!manager.isSainchamsagum(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (plugin.getCurseManager().blockSkill(player)) {
            return;
        }

        long remaining = manager.remainingUltimateCooldown(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "칠성강림 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        manager.useSevenStarsDescent(player);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "칠성강림! " + ChatColor.GRAY + "북두칠성의 기운이 내려옵니다.");
    }
}
