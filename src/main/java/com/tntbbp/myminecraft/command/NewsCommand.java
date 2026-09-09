package com.tntbbp.myminecraft.command;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.GeminiNewsClient;
import com.tntbbp.myminecraft.manager.NewsManager;
import com.tntbbp.myminecraft.manager.StockManager;
import com.tntbbp.myminecraft.model.Stock;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * 관리자가 (진짜/가짜) 뉴스를 예약 작성하는 명령어.
 * 내용 대신 "AI:주제"를 넣으면 Gemini API로 기사를 자동 생성한다 (config.yml의 ai.enabled 필요).
 */
public class NewsCommand implements CommandExecutor {

    private final MyMinecraftPlugin plugin;
    private final StockManager stockManager;
    private final NewsManager newsManager;
    private final GeminiNewsClient geminiNewsClient;

    public NewsCommand(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.stockManager = plugin.getStockManager();
        this.newsManager = plugin.getNewsManager();
        this.geminiNewsClient = plugin.getGeminiNewsClient();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }

        boolean fake = label.equalsIgnoreCase("가짜뉴스작성");

        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "사용법: /" + label + " <종목명> <상승|하락> <변동폭%> <내용 또는 AI:주제>");
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

        int direction;
        if (args[1].equals("상승")) {
            direction = 1;
        } else if (args[1].equals("하락")) {
            direction = -1;
        } else {
            sender.sendMessage(ChatColor.RED + "방향은 상승 또는 하락 이어야 합니다.");
            return true;
        }

        double magnitude;
        try {
            magnitude = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "변동폭은 숫자(%)로 입력해주세요.");
            return true;
        }
        if (magnitude <= 0) {
            sender.sendMessage(ChatColor.RED + "변동폭은 0보다 커야 합니다.");
            return true;
        }

        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            if (i > 3) {
                contentBuilder.append(" ");
            }
            contentBuilder.append(args[i]);
        }
        String contentArg = contentBuilder.toString();

        Stock finalStock = stock;
        if (contentArg.startsWith("AI:")) {
            String topic = contentArg.substring("AI:".length()).trim();
            if (topic.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "AI: 뒤에 주제를 입력해주세요.");
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + "AI로 뉴스 기사를 생성하는 중...");
            geminiNewsClient.generateNewsArticle(topic, fake)
                    .thenAccept(generatedContent -> Bukkit.getScheduler().runTask(plugin, () -> {
                        newsManager.submit(finalStock, direction, magnitude, generatedContent, fake);
                        sender.sendMessage(ChatColor.GREEN + (fake ? "가짜 뉴스" : "뉴스") + "를 예약했습니다: "
                                + ChatColor.WHITE + generatedContent);
                    }))
                    .exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage(ChatColor.RED + "AI 뉴스 생성 실패: " + ex.getMessage()));
                        return null;
                    });
            return true;
        }

        newsManager.submit(stock, direction, magnitude, contentArg, fake);
        sender.sendMessage(ChatColor.GREEN + (fake ? "가짜 뉴스" : "뉴스") + "를 예약했습니다.");
        return true;
    }
}
