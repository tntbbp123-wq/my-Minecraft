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
 *
 * <ul>
 *   <li>/뉴스작성 &lt;종목명&gt; &lt;상승|하락&gt; &lt;변동폭%&gt; &lt;내용&gt; - 완전 수동</li>
 *   <li>/뉴스작성 &lt;종목명&gt; &lt;상승|하락&gt; &lt;변동폭%&gt; AI:&lt;주제&gt; - 등락/변동폭은 관리자가,
 *       기사 본문만 AI가 작성</li>
 *   <li>/뉴스작성 &lt;종목명&gt; AI:&lt;주제&gt; (주제 생략 가능) - 관리자가 방향/변동폭을 정하지 않으면
 *       종목의 업종에 맞춰 AI가 기사 본문과 등락 방향/변동폭을 모두 직접 정함</li>
 * </ul>
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

        if (args.length < 2) {
            sendUsage(sender, label);
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
        Stock finalStock = stock;

        // 관리자가 방향/변동폭을 정하지 않고 바로 AI: 를 쓰면, AI가 종목의 업종에 맞춰
        // 기사 내용은 물론 등락 방향과 변동폭까지 모두 직접 정한다.
        if (args[1].startsWith("AI:")) {
            String topic = joinFrom(args, 1).substring("AI:".length()).trim();
            sender.sendMessage(ChatColor.YELLOW + "AI가 " + stock.getName() + "의 업종에 맞는 뉴스와 등락을 정하는 중...");
            geminiNewsClient.generateNewsWithImpact(finalStock, topic, fake)
                    .thenAccept(draft -> Bukkit.getScheduler().runTask(plugin, () -> {
                        String sanitized = NewsManager.sanitizeContent(draft.content());
                        newsManager.submit(finalStock, draft.direction(), draft.magnitudePercent(), sanitized, fake);
                        sender.sendMessage(ChatColor.GREEN + (fake ? "가짜 뉴스" : "뉴스") + "를 예약했습니다: "
                                + ChatColor.WHITE + sanitized);
                        sender.sendMessage(ChatColor.GRAY + "(AI 판단: " + (draft.direction() > 0 ? "상승" : "하락")
                                + " " + draft.magnitudePercent() + "% - 이 정보는 공개되지 않습니다)");
                    }))
                    .exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage(ChatColor.RED + "AI 뉴스 생성 실패: " + ex.getMessage()));
                        return null;
                    });
            return true;
        }

        if (args.length < 4) {
            sendUsage(sender, label);
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

        String contentArg = joinFrom(args, 3);

        int finalDirection = direction;
        double finalMagnitude = magnitude;
        if (contentArg.startsWith("AI:")) {
            String topic = contentArg.substring("AI:".length()).trim();
            if (topic.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "AI: 뒤에 주제를 입력해주세요.");
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + "AI로 뉴스 기사를 생성하는 중...");
            geminiNewsClient.generateNewsArticle(topic, fake)
                    .thenAccept(generatedContent -> Bukkit.getScheduler().runTask(plugin, () -> {
                        String sanitized = NewsManager.sanitizeContent(generatedContent);
                        newsManager.submit(finalStock, finalDirection, finalMagnitude, sanitized, fake);
                        sender.sendMessage(ChatColor.GREEN + (fake ? "가짜 뉴스" : "뉴스") + "를 예약했습니다: "
                                + ChatColor.WHITE + sanitized);
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

    private String joinFrom(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                builder.append(" ");
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "사용법: /" + label + " <종목명> <상승|하락> <변동폭%> <내용 또는 AI:주제>");
        sender.sendMessage(ChatColor.YELLOW + "또는: /" + label + " <종목명> AI:[주제] "
                + ChatColor.GRAY + "(등락 방향/변동폭까지 종목 업종에 맞게 AI가 직접 정함)");
        sender.sendMessage(ChatColor.GRAY + "내용에는 뉴스 소식(사건)만 적어주세요. 등락 방향/퍼센트는 "
                + "기사 본문에 자동으로 들어가지 않습니다.");
    }
}
