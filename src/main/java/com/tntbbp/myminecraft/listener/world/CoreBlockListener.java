package com.tntbbp.myminecraft.listener.world;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.gui.world.CoreGUI;
import com.tntbbp.myminecraft.manager.social.TeamManager;
import com.tntbbp.myminecraft.manager.world.CoreManager;
import com.tntbbp.myminecraft.model.Team;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    /**
     * 다른 플러그인(보호 구역 등)이 설치를 막았으면 아무것도 하지 않는다. 예전에는 막힌 설치에도 팀 거점을
     * 옮겨서, 코어가 없는 자리가 거점이 됐다.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

    /**
     * 다른 플러그인(보호 구역 등)이 부수기를 막았으면 아무것도 하지 않는다. 예전에는 막힌 부수기에도 새 코어
     * 아이템을 떨어뜨려서, 보호된 코어를 계속 두드리면 코어가 끝없이 복제됐다.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
        // 코어를 새로 설치하면 거점이 옮겨지지만 예전 코어 블록은 그대로 남는다. 그 예전 코어를 부쉈다고
        // 지금 거점까지 지우면 안 되므로, 이 블록이 지금 거점일 때만 지운다.
        TeamManager teamManager = plugin.getTeamManager();
        if (isAt(teamManager.getHome(teamName), event.getBlock())) {
            teamManager.clearHome(teamName);
        }
        event.getPlayer().sendMessage(ChatColor.YELLOW + "'" + teamName + "' 팀의 코어를 파괴했습니다.");
    }

    /** 거점 좌표(블록 가운데 +0.5)가 이 블록인지. */
    private static boolean isAt(Location home, Block block) {
        return home != null && home.getWorld() != null && home.getWorld().equals(block.getWorld())
                && home.getBlockX() == block.getX() && home.getBlockY() == block.getY()
                && home.getBlockZ() == block.getZ();
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
