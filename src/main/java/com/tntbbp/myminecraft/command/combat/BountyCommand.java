package com.tntbbp.myminecraft.command.combat;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.combat.BountyManager;
import com.tntbbp.myminecraft.util.TabCompletions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /현상금} — 현상금 표식을 찍고, 거두고, 현황을 본다.
 *
 * <ul>
 *   <li>{@code /현상금} — 내 표식 상태와 표식을 많이 받은 사람 목록</li>
 *   <li>{@code /현상금 <플레이어>} — 그 사람에게 내 표식을 찍는다 (이미 찍어뒀다면 옮긴다)</li>
 *   <li>{@code /현상금 해제} — 찍어둔 표식을 거둔다</li>
 * </ul>
 */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private static final int RANKING_LIMIT = 10;

    private final MyMinecraftPlugin plugin;

    public BountyCommand(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        BountyManager manager = plugin.getBountyManager();
        if (!manager.enabled()) {
            player.sendMessage(ChatColor.RED + "현상금 표식 기능이 꺼져 있습니다.");
            return true;
        }

        if (args.length == 0) {
            showStatus(player, manager);
            return true;
        }
        if (args[0].equals("해제")) {
            if (manager.unmark(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + "표식을 거뒀습니다.");
            } else {
                player.sendMessage(ChatColor.GRAY + "찍어둔 표식이 없습니다.");
            }
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "접속 중인 플레이어가 아닙니다: " + args[0]);
            return true;
        }

        switch (manager.mark(player.getUniqueId(), target.getUniqueId())) {
            case SUCCESS -> {
                player.sendMessage(ChatColor.GOLD + target.getName() + ChatColor.WHITE
                        + "님에게 현상금 표식을 찍었습니다.");
                int count = manager.markCount(target.getUniqueId());
                target.sendMessage(ChatColor.DARK_RED + "누군가 당신에게 현상금 표식을 찍었습니다. "
                        + ChatColor.GRAY + "(현재 " + count + "개)");
                // 표식은 누가 찍었는지 밝히지 않는다. 밝히면 보복이 강제되어 신중하게 쓸 이유가 사라진다.
            }
            case SELF -> player.sendMessage(ChatColor.RED + "자기 자신에게는 찍을 수 없습니다.");
            case SAME_TARGET -> player.sendMessage(ChatColor.GRAY + "이미 그 사람을 찍어뒀습니다.");
            case COOLDOWN -> player.sendMessage(ChatColor.RED + "아직 표식을 옮길 수 없습니다. ("
                    + formatDuration(manager.remainingChangeCooldown(player.getUniqueId())) + " 남음)");
            case DISABLED -> player.sendMessage(ChatColor.RED + "현상금 표식 기능이 꺼져 있습니다.");
        }
        return true;
    }

    private void showStatus(Player player, BountyManager manager) {
        player.sendMessage(ChatColor.GOLD + "===== 현상금 표식 =====");

        UUID marked = manager.markedBy(player.getUniqueId());
        if (marked == null) {
            player.sendMessage(ChatColor.GRAY + "내 표식: " + ChatColor.WHITE + "아직 아무에게도 안 찍음");
        } else {
            player.sendMessage(ChatColor.GRAY + "내 표식: " + ChatColor.WHITE + nameOf(marked));
        }

        long cooldown = manager.remainingChangeCooldown(player.getUniqueId());
        if (cooldown > 0) {
            player.sendMessage(ChatColor.GRAY + "옮기기 가능까지: " + ChatColor.WHITE + formatDuration(cooldown));
        }

        int mine = manager.markCount(player.getUniqueId());
        player.sendMessage(ChatColor.GRAY + "내가 받은 표식: " + ChatColor.WHITE + mine + "개 "
                + ChatColor.DARK_GRAY + "(받는 피해 +"
                + trim(mine * manager.damageTakenPercentPerMark()) + "% · 주는 피해 +"
                + trim(mine * manager.damageDealtPercentPerMark()) + "%)");

        List<Map.Entry<UUID, Integer>> ranking = manager.ranking(RANKING_LIMIT);
        if (ranking.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "아직 표식을 받은 사람이 없습니다.");
            return;
        }
        player.sendMessage(ChatColor.GRAY + "표식을 많이 받은 사람");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : ranking) {
            player.sendMessage(ChatColor.DARK_GRAY + " " + rank + ". " + ChatColor.WHITE
                    + nameOf(entry.getKey()) + ChatColor.GRAY + " — " + entry.getValue() + "개");
            rank++;
        }
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    private String formatDuration(long seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "시간 " + (seconds % 3600) / 60 + "분";
        }
        if (seconds >= 60) {
            return (seconds / 60) + "분 " + (seconds % 60) + "초";
        }
        return seconds + "초";
    }

    private String trim(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.floor(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(TabCompletions.onlinePlayerNames());
        options.add("해제");
        return TabCompletions.filterPrefix(options, args[0]);
    }
}
