package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.LocationsManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final LocationsManager locationsManager;

    public SpawnCommand(MyMinecraftPlugin plugin) {
        this.locationsManager = plugin.getLocationsManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            if (!player.hasPermission("myminecraft.admin")) {
                player.sendMessage(ChatColor.RED + "권한이 없습니다.");
                return true;
            }
            locationsManager.setSpawn(player.getLocation());
            player.sendMessage(ChatColor.GREEN + "스폰 위치를 현재 위치로 설정했습니다.");
            return true;
        }

        Location spawn = locationsManager.getSpawn();
        if (spawn == null) {
            spawn = player.getWorld().getSpawnLocation();
        }
        player.teleport(spawn);
        player.sendMessage(ChatColor.GREEN + "스폰으로 이동했습니다.");
        return true;
    }
}
