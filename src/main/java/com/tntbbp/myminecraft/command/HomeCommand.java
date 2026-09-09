package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.gui.HomeGUI;
import com.tntbbp.myminecraft.manager.HomeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HomeCommand implements CommandExecutor {

    private final MyMinecraftPlugin plugin;
    private final HomeManager homeManager;

    public HomeCommand(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.homeManager = plugin.getHomeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        switch (label.toLowerCase()) {
            case "홈" -> new HomeGUI(plugin, player).open();
            case "홈설정" -> handleSetHome(player, args);
            case "홈삭제" -> handleDelHome(player, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleSetHome(Player player, String[] args) {
        String name = args.length > 0 ? args[0] : "home";
        boolean saved = homeManager.setHome(player.getUniqueId(), name, player.getLocation());
        if (saved) {
            player.sendMessage(ChatColor.GREEN + "'" + name + "' 홈을 저장했습니다.");
        } else {
            player.sendMessage(ChatColor.RED + "홈은 최대 " + homeManager.maxHomes() + "개까지 저장할 수 있습니다.");
        }
    }

    private void handleDelHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.YELLOW + "사용법: /홈삭제 <이름>");
            return;
        }
        boolean deleted = homeManager.delHome(player.getUniqueId(), args[0]);
        if (deleted) {
            player.sendMessage(ChatColor.GREEN + "'" + args[0] + "' 홈을 삭제했습니다.");
        } else {
            player.sendMessage(ChatColor.RED + "해당 이름의 홈을 찾을 수 없습니다.");
        }
    }
}
