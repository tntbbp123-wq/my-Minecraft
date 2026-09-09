package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.gui.AdminMenuGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminMenuCommand implements CommandExecutor {

    private final MyMinecraftPlugin plugin;

    public AdminMenuCommand(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        if (!player.hasPermission("myminecraft.admin")) {
            player.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        new AdminMenuGUI(plugin, player).open();
        return true;
    }
}
