package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.StockManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** 관리자가 새 주식 종목을 추가하는 명령어. */
public class StockAddCommand implements CommandExecutor {

    private final StockManager stockManager;

    public StockAddCommand(MyMinecraftPlugin plugin) {
        this.stockManager = plugin.getStockManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /주식종류추가 <이름> <최소값> <최대값> <변동단위>");
            return true;
        }

        String name = args[0];
        double minPrice;
        double maxPrice;
        double changeUnit;
        try {
            minPrice = Double.parseDouble(args[1]);
            maxPrice = Double.parseDouble(args[2]);
            changeUnit = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "최소값/최대값/변동단위는 숫자로 입력해주세요.");
            return true;
        }

        StockManager.AddResult result = stockManager.addCustomStock(name, minPrice, maxPrice, changeUnit);
        switch (result) {
            case SUCCESS -> sender.sendMessage(ChatColor.GREEN + "'" + name + "' 종목을 추가했습니다. "
                    + "(최소 " + minPrice + " ~ 최대 " + maxPrice + ", 변동단위 " + changeUnit + ")");
            case DUPLICATE_NAME -> sender.sendMessage(ChatColor.RED + "이미 존재하는 이름입니다.");
            case INVALID_RANGE -> sender.sendMessage(ChatColor.RED
                    + "최소값은 0 이상, 최대값은 최소값보다 커야 하며, 변동단위는 0보다 커야 합니다.");
        }
        return true;
    }
}
