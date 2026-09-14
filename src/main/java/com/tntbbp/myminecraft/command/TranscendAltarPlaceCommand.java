package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.TranscendAltarBlockManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** 관리자가 바라보고 있는 블록 위에 <초월의 제단> 커스텀 블록을 세우는 명령어. */
public class TranscendAltarPlaceCommand implements CommandExecutor {

    private final TranscendAltarBlockManager altarBlockManager;

    public TranscendAltarPlaceCommand(MyMinecraftPlugin plugin) {
        this.altarBlockManager = plugin.getTranscendAltarBlockManager();
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

        Block target = player.getTargetBlockExact(10);
        if (target == null || target.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "10블록 이내의 블록을 바라보고 사용해주세요.");
            return true;
        }

        Location location = target.getLocation().add(0.5, 1.05, 0.5);
        altarBlockManager.place(location);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "초월의 제단을 세웠습니다. 오른쪽 클릭하면 열립니다.");
        return true;
    }
}
