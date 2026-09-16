package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.GleipnirManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** 글레이프니르의 F(봉인)·Q(속박) 발동과, 속박된 플레이어의 이동 차단을 처리한다. */
public class GleipnirListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public GleipnirListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** F키(기본 손 바꾸기)를 봉인 발동 키로 쓴다. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        GleipnirManager manager = plugin.getGleipnirManager();
        if (!manager.isGleipnir(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (manager.isSealed(player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "봉인되어 스킬을 쓸 수 없습니다.");
            return;
        }

        long remaining = manager.remainingSealCooldown(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "봉인 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        Player target = manager.useSeal(player);
        if (target == null) {
            player.sendMessage(ChatColor.GRAY + "바라보는 방향에 대상이 없습니다.");
            return;
        }
        manager.drawChain(player, target);
        player.sendMessage(ChatColor.WHITE + target.getName() + "님의 스킬을 봉인했습니다.");
    }

    /** Q키(기본 아이템 버리기)를 속박 발동 키로 쓴다. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        GleipnirManager manager = plugin.getGleipnirManager();
        if (!manager.isGleipnir(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (manager.isSealed(player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "봉인되어 스킬을 쓸 수 없습니다.");
            return;
        }

        long remaining = manager.remainingBindCooldown(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "속박 재사용 대기 중입니다. (" + remaining + "초)");
            return;
        }

        Player target = manager.useBind(player);
        if (target == null) {
            player.sendMessage(ChatColor.GRAY + "바라보는 방향에 대상이 없습니다.");
            return;
        }
        manager.drawChain(player, target);
        player.sendMessage(ChatColor.WHITE + target.getName() + "님을 "
                + manager.bindSecondsFor(target) + "초간 속박했습니다.");
    }

    /** 속박 중에는 제자리에 묶인다. 시점 회전은 그대로 둔다. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getGleipnirManager().isBound(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                to.getYaw(), to.getPitch()));
    }
}
