package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.CurrencyManager;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import com.tntbbp.myminecraft.util.TabCompletions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 관리자가 강화석/등급별 확률 강화 두루마리/레바테인을 지급하는 통합 명령어.
 * 바닐라 /give 명령어와 같은 순서(대상 → 아이템 → 수량)를 사용한다.
 */
public class SpecialItemSummonCommand implements CommandExecutor, TabCompleter {

    private final EnhanceManager enhanceManager;
    private final LaevateinnManager laevateinnManager;
    private final CurrencyManager currencyManager;

    public SpecialItemSummonCommand(MyMinecraftPlugin plugin) {
        this.enhanceManager = plugin.getEnhanceManager();
        this.laevateinnManager = plugin.getLaevateinnManager();
        this.currencyManager = plugin.getCurrencyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /특수아이템소환 <유저> <아이템명> <수량>");
            sender.sendMessage(ChatColor.YELLOW + "아이템명: "
                    + String.join(", ", SpecialItemCatalog.allItemNames(enhanceManager, currencyManager)));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "온라인 상태인 플레이어를 찾을 수 없습니다.");
            return true;
        }

        String itemName = args[1];

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "수량은 숫자로 입력해주세요.");
                return true;
            }
        }

        SpecialItemCatalog.Resolved resolved = SpecialItemCatalog.resolve(
                enhanceManager, laevateinnManager, currencyManager, itemName, amount);
        if (resolved == null) {
            sender.sendMessage(ChatColor.RED + "알 수 없는 아이템명입니다. 사용 가능: "
                    + String.join(", ", SpecialItemCatalog.allItemNames(enhanceManager, currencyManager)));
            return true;
        }

        target.getInventory().addItem(resolved.item()).values()
                .forEach(leftover -> target.getWorld().dropItem(target.getLocation(), leftover));

        sender.sendMessage(ChatColor.GREEN + target.getName() + "님에게 " + resolved.displayName() + " " + amount + "개를 지급했습니다.");
        target.sendMessage(ChatColor.GREEN + resolved.displayName() + " " + amount + "개를 받았습니다.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return TabCompletions.filterPrefix(TabCompletions.onlinePlayerNames(), args[0]);
        }
        if (args.length == 2) {
            return TabCompletions.filterPrefix(SpecialItemCatalog.allItemNames(enhanceManager, currencyManager), args[1]);
        }
        if (args.length == 3) {
            return TabCompletions.filterPrefix(List.of("1", "5", "10", "64"), args[2]);
        }
        return List.of();
    }
}
