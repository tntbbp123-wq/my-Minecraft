package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.TeleportRequestManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpaCommand implements CommandExecutor {

    private final MyMinecraftPlugin plugin;
    private final TeleportRequestManager requests;

    public TpaCommand(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.requests = plugin.getTeleportRequestManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        switch (label.toLowerCase()) {
            case "텔레포트요청" -> handleTpa(player, args);
            case "텔레포트수락" -> handleAccept(player);
            case "텔레포트거절" -> handleDeny(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleTpa(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.YELLOW + "사용법: /텔레포트요청 <플레이어>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "온라인 상태인 플레이어를 찾을 수 없습니다.");
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "자기 자신에게는 요청할 수 없습니다.");
            return;
        }

        requests.createRequest(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + target.getName() + "님에게 텔레포트 요청을 보냈습니다.");

        TextComponent message = new TextComponent(ChatColor.GREEN + player.getName() + "님이 텔레포트를 요청했습니다. ("
                + requests.timeoutSeconds() + "초 후 만료) ");

        TextComponent acceptButton = new TextComponent(ChatColor.BOLD + "" + ChatColor.GREEN + "[수락]");
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/텔레포트수락"));
        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("클릭하면 바로 수락됩니다").create()));

        TextComponent denyButton = new TextComponent(ChatColor.BOLD + "" + ChatColor.RED + "[거절]");
        denyButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/텔레포트거절"));
        denyButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("클릭하면 바로 거절됩니다").create()));

        message.addExtra(acceptButton);
        message.addExtra(" ");
        message.addExtra(denyButton);

        target.spigot().sendMessage(message);
    }

    private void handleAccept(Player player) {
        TeleportRequestManager.Request request = requests.getValidRequest(player.getUniqueId());
        if (request == null) {
            player.sendMessage(ChatColor.RED + "대기 중인 텔레포트 요청이 없습니다.");
            return;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        requests.clearRequest(player.getUniqueId());
        if (requester == null || !requester.isOnline()) {
            player.sendMessage(ChatColor.RED + "요청을 보낸 플레이어가 오프라인입니다.");
            return;
        }
        requester.teleport(player.getLocation());
        requester.sendMessage(ChatColor.GREEN + player.getName() + "님이 요청을 수락했습니다.");
        player.sendMessage(ChatColor.GREEN + requester.getName() + "님을 이동시켰습니다.");
    }

    private void handleDeny(Player player) {
        TeleportRequestManager.Request request = requests.getValidRequest(player.getUniqueId());
        if (request == null) {
            player.sendMessage(ChatColor.RED + "대기 중인 텔레포트 요청이 없습니다.");
            return;
        }
        requests.clearRequest(player.getUniqueId());
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) {
            requester.sendMessage(ChatColor.RED + player.getName() + "님이 요청을 거절했습니다.");
        }
        player.sendMessage(ChatColor.YELLOW + "요청을 거절했습니다.");
    }
}
