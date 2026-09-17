package com.tntbbp.myminecraft.command.social;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.social.TeamManager;
import com.tntbbp.myminecraft.model.Team;
import com.tntbbp.myminecraft.util.TabCompletions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 팀 생성/구성원 관리(관리자 전용) + 팀 정보 조회(누구나).
 * 팀을 생성하면 팀장에게 '코어' 1개가 자동 지급된다.
 */
public class TeamCommand implements CommandExecutor, TabCompleter {

    private final MyMinecraftPlugin plugin;

    public TeamCommand(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName()) {
            case "팀생성" -> handleCreate(sender, args);
            case "팀원추가" -> handleAddMember(sender, args);
            case "팀원삭제" -> handleRemoveMember(sender, args);
            case "팀삭제" -> handleDelete(sender, args);
            case "팀정보" -> handleInfo(sender, args);
            default -> {
            }
        }
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("myminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return false;
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /팀생성 <팀이름> <팀장닉네임>");
            return;
        }
        Player leader = Bukkit.getPlayerExact(args[1]);
        if (leader == null) {
            sender.sendMessage(ChatColor.RED + "온라인 상태인 플레이어를 찾을 수 없습니다.");
            return;
        }

        TeamManager teamManager = plugin.getTeamManager();
        if (!teamManager.createTeam(args[0], leader.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "팀 생성에 실패했습니다. (이미 있는 팀 이름이거나, 대상이 이미 다른 팀 소속입니다)");
            return;
        }

        ItemStack core = plugin.getCoreManager().createItem();
        leader.getInventory().addItem(core).values()
                .forEach(leftover -> leader.getWorld().dropItem(leader.getLocation(), leftover));

        sender.sendMessage(ChatColor.GREEN + "'" + args[0] + "' 팀을 생성했습니다. (팀장: " + leader.getName() + ")");
        leader.sendMessage(ChatColor.GREEN + "'" + args[0] + "' 팀의 팀장이 되었습니다! 코어를 받았습니다. 원하는 곳에 설치해 거점으로 삼으세요.");
    }

    private void handleAddMember(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /팀원추가 <팀이름> <플레이어>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "온라인 상태인 플레이어를 찾을 수 없습니다.");
            return;
        }
        if (!plugin.getTeamManager().addMember(args[0], target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "팀원 추가에 실패했습니다. (팀이 없거나, 대상이 이미 다른 팀 소속입니다)");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + target.getName() + "님을 '" + args[0] + "' 팀에 추가했습니다.");
        target.sendMessage(ChatColor.GREEN + "'" + args[0] + "' 팀에 합류했습니다.");
    }

    private void handleRemoveMember(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /팀원삭제 <팀이름> <플레이어>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!plugin.getTeamManager().removeMember(args[0], target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "팀원 삭제에 실패했습니다. (팀장은 삭제할 수 없고, 해당 팀원이 아니면 실패합니다)");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + args[1] + "님을 '" + args[0] + "' 팀에서 제외했습니다.");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /팀삭제 <팀이름>");
            return;
        }
        if (!plugin.getTeamManager().deleteTeam(args[0])) {
            sender.sendMessage(ChatColor.RED + "해당 이름의 팀을 찾을 수 없습니다.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "'" + args[0] + "' 팀을 삭제했습니다.");
    }

    private void handleInfo(CommandSender sender, String[] args) {
        TeamManager teamManager = plugin.getTeamManager();
        Team team;
        if (args.length >= 1) {
            team = teamManager.getTeam(args[0]);
        } else if (sender instanceof Player player) {
            team = teamManager.getTeamOfPlayer(player.getUniqueId());
        } else {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /팀정보 <팀이름>");
            return;
        }
        if (team == null) {
            sender.sendMessage(ChatColor.RED + "팀을 찾을 수 없습니다.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== '" + team.name() + "' 팀 정보 ===");
        sender.sendMessage(ChatColor.YELLOW + "팀장: " + ChatColor.WHITE + nameOf(team.leader()));
        String memberNames = team.members().stream().map(this::nameOf).collect(Collectors.joining(", "));
        sender.sendMessage(ChatColor.YELLOW + "팀원(" + team.members().size() + "명): " + ChatColor.WHITE + memberNames);
        boolean hasHome = teamManager.getHome(team.name()) != null;
        sender.sendMessage(ChatColor.YELLOW + "거점(코어): " + ChatColor.WHITE + (hasHome ? "설치됨" : "미설치"));
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name != null ? name : uuid.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName();
        if (name.equals("팀정보")) {
            if (args.length == 1) {
                return TabCompletions.filterPrefix(List.copyOf(plugin.getTeamManager().teamNames()), args[0]);
            }
            return List.of();
        }
        if (!sender.hasPermission("myminecraft.admin")) {
            return List.of();
        }
        if (args.length == 1 && (name.equals("팀원추가") || name.equals("팀원삭제") || name.equals("팀삭제"))) {
            return TabCompletions.filterPrefix(List.copyOf(plugin.getTeamManager().teamNames()), args[0]);
        }
        if (args.length == 2 && (name.equals("팀생성") || name.equals("팀원추가"))) {
            return TabCompletions.filterPrefix(TabCompletions.onlinePlayerNames(), args[1]);
        }
        return List.of();
    }
}
