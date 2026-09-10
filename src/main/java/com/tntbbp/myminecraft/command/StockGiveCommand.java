package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.StockManager;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.util.TabCompletions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** 관리자가 대가 없이 플레이어에게 주식을 지급하는 명령어. */
public class StockGiveCommand implements CommandExecutor, TabCompleter {

    private final StockManager stockManager;

    public StockGiveCommand(MyMinecraftPlugin plugin) {
        this.stockManager = plugin.getStockManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /주식지급 <종목명> <유저> <수량>");
            return true;
        }

        Stock stock = stockManager.getStockByName(args[0]);
        if (stock == null) {
            stock = stockManager.getStock(args[0]);
        }
        if (stock == null) {
            sender.sendMessage(ChatColor.RED + "존재하지 않는 종목입니다: " + args[0]);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "온라인 상태인 플레이어를 찾을 수 없습니다.");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "수량은 숫자로 입력해주세요.");
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "수량은 1 이상이어야 합니다.");
            return true;
        }

        stockManager.giveHolding(target.getUniqueId(), stock.getId(), amount);
        sender.sendMessage(ChatColor.GREEN + target.getName() + "님에게 " + stock.getName() + " " + amount + "주를 지급했습니다.");
        target.sendMessage(ChatColor.GREEN + stock.getName() + " " + amount + "주를 받았습니다.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> names = stockManager.getStocks().stream().map(Stock::getName).toList();
            return TabCompletions.filterPrefix(names, args[0]);
        }
        if (args.length == 2) {
            return TabCompletions.filterPrefix(TabCompletions.onlinePlayerNames(), args[1]);
        }
        if (args.length == 3) {
            return TabCompletions.filterPrefix(List.of("1", "10", "100"), args[2]);
        }
        return List.of();
    }
}
