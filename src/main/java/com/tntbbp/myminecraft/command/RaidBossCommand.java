package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.RaidBossManager;
import com.tntbbp.myminecraft.raid.EternalKnight;
import com.tntbbp.myminecraft.raid.RaidBoss;
import com.tntbbp.myminecraft.util.TabCompletions;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** 관리자가 레이드 보스를 소환/제거하고 현황을 확인하는 명령어. */
public class RaidBossCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("소환", "제거", "목록");

    private final MyMinecraftPlugin plugin;

    public RaidBossCommand(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        switch (args[0]) {
            case "소환" -> handleSpawn(sender, args);
            case "제거" -> handleDespawn(sender);
            case "목록" -> handleList(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "보스 소환은 플레이어만 사용할 수 있습니다.");
            return;
        }

        String bossId = args.length >= 2 ? args[1] : EternalKnight.ID;
        if (plugin.getRaidBossManager().displayNameOf(bossId) == null) {
            sender.sendMessage(ChatColor.RED + "알 수 없는 보스입니다. 사용 가능: "
                    + String.join(", ", RaidBossManager.BOSS_IDS));
            return;
        }

        Location spawnLocation = player.getLocation();
        Block target = player.getTargetBlockExact(30);
        if (target != null && !target.getType().isAir()) {
            spawnLocation = target.getLocation().add(0.5, 1.0, 0.5);
        }

        RaidBoss boss = plugin.getRaidBossManager().spawn(bossId, spawnLocation);
        if (boss == null) {
            sender.sendMessage(ChatColor.RED + "보스 소환에 실패했습니다.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "보스를 소환했습니다: " + boss.displayName()
                + ChatColor.GRAY + " (체력 " + (int) boss.maxHealth() + ")");
        if (bossId.equalsIgnoreCase(EternalKnight.ID)) {
            EternalKnight.patternSummary().forEach(player::sendMessage);
        }
    }

    private void handleDespawn(CommandSender sender) {
        int removed = plugin.getRaidBossManager().despawnAll();
        if (removed == 0) {
            sender.sendMessage(ChatColor.YELLOW + "소환된 레이드 보스가 없습니다.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "레이드 보스 " + removed + "기를 제거했습니다.");
    }

    private void handleList(CommandSender sender) {
        RaidBossManager manager = plugin.getRaidBossManager();
        sender.sendMessage(ChatColor.GOLD + "=== 레이드 보스 ===");
        for (String bossId : RaidBossManager.BOSS_IDS) {
            sender.sendMessage(ChatColor.YELLOW + bossId + ChatColor.GRAY + " - " + manager.displayNameOf(bossId));
        }

        List<RaidBoss> active = manager.activeBosses();
        sender.sendMessage(ChatColor.GOLD + "현재 소환된 보스: " + ChatColor.WHITE + active.size() + "기");
        for (RaidBoss boss : active) {
            Location location = boss.entity().getLocation();
            sender.sendMessage(ChatColor.GRAY + " - " + boss.displayName() + ChatColor.GRAY
                    + " (체력 " + String.format("%.0f%%", boss.healthRatio() * 100)
                    + ", " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")");
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "사용법:");
        sender.sendMessage(ChatColor.YELLOW + "/레이드보스 소환 [보스ID] "
                + ChatColor.GRAY + "- 바라보는 위치에 소환 (기본: " + EternalKnight.ID + ")");
        sender.sendMessage(ChatColor.YELLOW + "/레이드보스 제거 " + ChatColor.GRAY + "- 소환된 보스를 전부 제거");
        sender.sendMessage(ChatColor.YELLOW + "/레이드보스 목록 " + ChatColor.GRAY + "- 보스 종류와 현황 확인");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return TabCompletions.filterPrefix(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equals("소환")) {
            return TabCompletions.filterPrefix(RaidBossManager.BOSS_IDS, args[1]);
        }
        return List.of();
    }
}
