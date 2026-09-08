package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.stream.Collectors;

/** 관리자가 강화석/등급별 확률 강화 두루마리를 지급하는 명령어. */
public class EnhanceItemCommand implements CommandExecutor {

    private final EnhanceManager enhanceManager;

    public EnhanceItemCommand(MyMinecraftPlugin plugin) {
        this.enhanceManager = plugin.getEnhanceManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        if (args.length < 2) {
            String grades = enhanceManager.scrollGrades().stream()
                    .map(EnhanceManager.ScrollGrade::id)
                    .collect(Collectors.joining("|"));
            sender.sendMessage(ChatColor.YELLOW + "사용법: /enhanceitem <stone|scroll:<" + grades + ">> <player> [amount]");
            return true;
        }

        String[] typeParts = args[0].toLowerCase().split(":", 2);
        String type = typeParts[0];
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "온라인 상태인 플레이어를 찾을 수 없습니다.");
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "수량은 숫자로 입력해주세요.");
                return true;
            }
        }

        ItemStack item;
        String itemName;
        switch (type) {
            case "stone" -> {
                item = enhanceManager.createEnhanceStone(amount);
                itemName = "강화석";
            }
            case "scroll" -> {
                String gradeId = typeParts.length > 1 ? typeParts[1] : enhanceManager.defaultScrollGrade().id();
                EnhanceManager.ScrollGrade grade = enhanceManager.getScrollGrade(gradeId);
                if (grade == null) {
                    String grades = enhanceManager.scrollGrades().stream()
                            .map(EnhanceManager.ScrollGrade::id)
                            .collect(Collectors.joining(", "));
                    sender.sendMessage(ChatColor.RED + "알 수 없는 두루마리 등급입니다. 사용 가능: " + grades);
                    return true;
                }
                item = enhanceManager.createProbabilityScroll(grade, amount);
                itemName = grade.displayName();
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "종류는 stone 또는 scroll[:등급] 이어야 합니다.");
                return true;
            }
        }

        target.getInventory().addItem(item).values()
                .forEach(leftover -> target.getWorld().dropItem(target.getLocation(), leftover));

        sender.sendMessage(ChatColor.GREEN + target.getName() + "님에게 " + itemName + " " + amount + "개를 지급했습니다.");
        target.sendMessage(ChatColor.GREEN + itemName + " " + amount + "개를 받았습니다.");
        return true;
    }
}
