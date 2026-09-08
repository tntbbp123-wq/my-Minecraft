package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 관리자가 레바테인을 지급하는 명령어. */
public class LaevateinnCommand implements CommandExecutor {

    private final LaevateinnManager laevateinnManager;

    public LaevateinnCommand(MyMinecraftPlugin plugin) {
        this.laevateinnManager = plugin.getLaevateinnManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /laevateinn <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "온라인 상태인 플레이어를 찾을 수 없습니다.");
            return true;
        }

        ItemStack item = laevateinnManager.createItem();
        target.getInventory().addItem(item).values()
                .forEach(leftover -> target.getWorld().dropItem(target.getLocation(), leftover));

        sender.sendMessage(ChatColor.GREEN + target.getName() + "님에게 레바테인을 지급했습니다.");
        target.sendMessage(ChatColor.LIGHT_PURPLE + "레바테인을 받았습니다! F키로 라그나로크의 숨결을 사용할 수 있습니다.");
        return true;
    }
}
