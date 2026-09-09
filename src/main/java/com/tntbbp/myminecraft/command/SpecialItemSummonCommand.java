package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** 관리자가 강화석/등급별 확률 강화 두루마리/레바테인을 지급하는 통합 명령어. */
public class SpecialItemSummonCommand implements CommandExecutor {

    private final EnhanceManager enhanceManager;
    private final LaevateinnManager laevateinnManager;

    public SpecialItemSummonCommand(MyMinecraftPlugin plugin) {
        this.enhanceManager = plugin.getEnhanceManager();
        this.laevateinnManager = plugin.getLaevateinnManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /특수아이템소환 <아이템명> <유저> <수량>");
            sender.sendMessage(ChatColor.YELLOW + "아이템명: " + availableItemNames());
            return true;
        }

        String itemName = args[0];
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
        String displayName;

        if (itemName.equals("강화석")) {
            item = enhanceManager.createEnhanceStone(amount);
            displayName = "강화석";
        } else if (itemName.equals("레바테인")) {
            item = laevateinnManager.createItem();
            displayName = "레바테인";
        } else {
            EnhanceManager.ScrollGrade grade = findScrollGrade(itemName);
            if (grade == null) {
                sender.sendMessage(ChatColor.RED + "알 수 없는 아이템명입니다. 사용 가능: " + availableItemNames());
                return true;
            }
            item = enhanceManager.createProbabilityScroll(grade, amount);
            displayName = grade.displayName();
        }

        target.getInventory().addItem(item).values()
                .forEach(leftover -> target.getWorld().dropItem(target.getLocation(), leftover));

        sender.sendMessage(ChatColor.GREEN + target.getName() + "님에게 " + displayName + " " + amount + "개를 지급했습니다.");
        target.sendMessage(ChatColor.GREEN + displayName + " " + amount + "개를 받았습니다.");
        return true;
    }

    private EnhanceManager.ScrollGrade findScrollGrade(String itemName) {
        for (EnhanceManager.ScrollGrade grade : enhanceManager.scrollGrades()) {
            if (itemName.equalsIgnoreCase(grade.id()) || itemName.equals(scrollShortName(grade))) {
                return grade;
            }
        }
        return null;
    }

    private String scrollShortName(EnhanceManager.ScrollGrade grade) {
        return grade.displayName().replace("등급", "").replace(" ", "");
    }

    private String availableItemNames() {
        List<String> names = new ArrayList<>();
        names.add("강화석");
        names.add("레바테인");
        names.addAll(enhanceManager.scrollGrades().stream()
                .map(this::scrollShortName)
                .collect(Collectors.toList()));
        return String.join(", ", names);
    }
}
