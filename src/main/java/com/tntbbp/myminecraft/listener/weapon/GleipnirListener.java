package com.tntbbp.myminecraft.listener.weapon;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.weapon.GleipnirManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
        if (plugin.getCurseManager().blockSkill(player)) {
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
        if (plugin.getCurseManager().blockSkill(player)) {
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

    /**
     * 글레이프니르는 바닐라 '끈'을 재질로 쓰기 때문에, 그냥 두면 몹을 묶는 원래 기능이 그대로
     * 동작한다. 신화 무기가 소 끌고 다니는 데 쓰이면 곤란하므로 막는다.
     */
    @EventHandler(ignoreCancelled = true)
    public void onLeashEntity(PlayerLeashEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGleipnirManager().isGleipnir(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    /** 끈을 들고 몹을 우클릭하는 것 자체를 막는다 (묶기 시도의 시작점). */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGleipnirManager().isGleipnir(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(ChatColor.GRAY + "글레이프니르는 그런 용도로 쓰는 사슬이 아닙니다.");
    }

    /** 울타리에 끈을 매다는 것도 막는다. 상자나 문 같은 다른 상호작용은 그대로 둔다. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!plugin.getGleipnirManager().isGleipnir(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        String type = event.getClickedBlock().getType().name();
        if (type.endsWith("_FENCE") || type.endsWith("_FENCE_GATE") || type.endsWith("_WALL")) {
            event.setCancelled(true);
        }
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
