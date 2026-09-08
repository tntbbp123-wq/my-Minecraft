package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.RandomTeleportManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RtCommand implements CommandExecutor {

    private final RandomTeleportManager randomTeleportManager;

    public RtCommand(MyMinecraftPlugin plugin) {
        this.randomTeleportManager = plugin.getRandomTeleportManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        RandomTeleportManager.Result result = randomTeleportManager.teleport(player);
        switch (result) {
            case SUCCESS -> player.sendMessage(ChatColor.GREEN + "랜덤 위치로 이동했습니다.");
            case ON_COOLDOWN -> player.sendMessage(ChatColor.RED + "쿨다운이 남아있습니다. ("
                    + randomTeleportManager.remainingCooldownSeconds(player.getUniqueId()) + "초 후 다시 시도하세요)");
            case NO_SAFE_LOCATION -> player.sendMessage(ChatColor.RED + "안전한 위치를 찾지 못했습니다. 다시 시도해주세요.");
        }
        return true;
    }
}
