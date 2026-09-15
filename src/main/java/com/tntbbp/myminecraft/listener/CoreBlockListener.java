package com.tntbbp.myminecraft.listener;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.gui.CoreGUI;
import com.tntbbp.myminecraft.manager.CoreManager;
import com.tntbbp.myminecraft.manager.TeamManager;
import com.tntbbp.myminecraft.model.Team;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Beacon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** '코어' 아이템을 설치/파괴/우클릭할 때의 동작을 처리한다 (신호기 블록을 그대로 활용). */
public class CoreBlockListener implements Listener {

    private final MyMinecraftPlugin plugin;

    public CoreBlockListener(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        CoreManager coreManager = plugin.getCoreManager();
        if (!coreManager.isCoreItem(event.getItemInHand())) {
            return;
        }

        Player player = event.getPlayer();
        TeamManager teamManager = plugin.getTeamManager();
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team == null) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "팀 소속이 아니면 코어를 설치할 수 없습니다.");
            return;
        }

        if (!(event.getBlock().getState() instanceof Beacon beacon)) {
            return;
        }
        coreManager.markBlock(beacon, team.name());
        teamManager.setHome(team.name(), event.getBlock().getLocation().add(0.5, 0, 0.5));

        player.sendMessage(ChatColor.GREEN + "코어를 설치했습니다. 이 위치가 '" + team.name() + "' 팀의 거점(홈)이 되었습니다.");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BEACON
                || !(event.getBlock().getState() instanceof Beacon beacon)) {
            return;
        }
        CoreManager coreManager = plugin.getCoreManager();
        String teamName = coreManager.getBlockTeam(beacon);
        if (teamName == null) {
            return;
        }

        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), coreManager.createItem());
        plugin.getTeamManager().clearHome(teamName);
        event.getPlayer().sendMessage(ChatColor.YELLOW + "'" + teamName + "' 팀의 코어를 파괴했습니다.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Beacon beacon)) {
            return;
        }
        CoreManager coreManager = plugin.getCoreManager();
        String teamName = coreManager.getBlockTeam(beacon);
        if (teamName == null) {
            return;
        }

        event.setCancelled(true);
        new CoreGUI(plugin, event.getPlayer(), teamName).open();
    }
}
