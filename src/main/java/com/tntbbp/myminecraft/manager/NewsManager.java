package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.model.Stock;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자가 작성한 (진짜/가짜) 뉴스를 예약 발행한다.
 * 작성 후 {@code reveal-delay-seconds}가 지나면 전체 공지되고,
 * 작성 후 {@code apply-delay-seconds}가 지나면 (가짜 뉴스가 아닌 경우에만) 해당 종목 가격에 실제로 반영된다.
 */
public class NewsManager {

    public static final class NewsItem {
        int id;
        String stockId;
        String stockName;
        double impactPercent;
        String content;
        boolean fake;
        long createdAtMillis;
        long revealAtMillis;
        long applyAtMillis;
        boolean revealed;
        boolean applied;
    }

    private final MyMinecraftPlugin plugin;
    private final StockManager stockManager;
    private final File file;
    private final YamlConfiguration data;
    private final List<NewsItem> items = new ArrayList<>();
    private int nextId = 1;
    private BukkitTask task;

    public NewsManager(MyMinecraftPlugin plugin, StockManager stockManager) {
        this.plugin = plugin;
        this.stockManager = stockManager;
        this.file = new File(plugin.getDataFolder(), "news.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("news.yml 생성 실패: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        List<Map<?, ?>> list = data.getMapList("pending");
        for (Map<?, ?> raw : list) {
            NewsItem item = new NewsItem();
            item.id = raw.get("id") instanceof Number n ? n.intValue() : nextId;
            item.stockId = String.valueOf(raw.get("stock-id"));
            item.stockName = String.valueOf(raw.get("stock-name"));
            item.impactPercent = raw.get("impact-percent") instanceof Number n ? n.doubleValue() : 0.0;
            item.content = String.valueOf(raw.get("content"));
            item.fake = Boolean.TRUE.equals(raw.get("fake"));
            item.createdAtMillis = raw.get("created-at") instanceof Number n ? n.longValue() : System.currentTimeMillis();
            item.revealAtMillis = raw.get("reveal-at") instanceof Number n ? n.longValue() : item.createdAtMillis;
            item.applyAtMillis = raw.get("apply-at") instanceof Number n ? n.longValue() : item.createdAtMillis;
            item.revealed = Boolean.TRUE.equals(raw.get("revealed"));
            item.applied = Boolean.TRUE.equals(raw.get("applied"));
            items.add(item);
            nextId = Math.max(nextId, item.id + 1);
        }
    }

    private void save() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (NewsItem item : items) {
            if (item.applied) {
                continue;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.id);
            map.put("stock-id", item.stockId);
            map.put("stock-name", item.stockName);
            map.put("impact-percent", item.impactPercent);
            map.put("content", item.content);
            map.put("fake", item.fake);
            map.put("created-at", item.createdAtMillis);
            map.put("reveal-at", item.revealAtMillis);
            map.put("apply-at", item.applyAtMillis);
            map.put("revealed", item.revealed);
            map.put("applied", item.applied);
            list.add(map);
        }
        data.set("pending", list);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("news.yml 저장 실패: " + e.getMessage());
        }
    }

    /** 새 뉴스를 예약한다. direction은 +1(상승) 또는 -1(하락), magnitudePercent는 0 이상의 변동폭(%). */
    public NewsItem submit(Stock stock, int direction, double magnitudePercent, String content, boolean fake) {
        long now = System.currentTimeMillis();
        long revealDelay = plugin.getConfig().getLong("news.reveal-delay-seconds", 3600) * 1000L;
        long applyDelay = plugin.getConfig().getLong("news.apply-delay-seconds", 7200) * 1000L;

        NewsItem item = new NewsItem();
        item.id = nextId++;
        item.stockId = stock.getId();
        item.stockName = stock.getName();
        item.impactPercent = Math.abs(magnitudePercent) * Math.signum(direction);
        item.content = content;
        item.fake = fake;
        item.createdAtMillis = now;
        item.revealAtMillis = now + revealDelay;
        item.applyAtMillis = now + applyDelay;
        items.add(item);
        save();
        return item;
    }

    public void startTask() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkDue, 20L * 10, 20L * 30);
    }

    public void stopTask() {
        if (task != null) {
            task.cancel();
        }
    }

    private void checkDue() {
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (NewsItem item : items) {
            if (!item.revealed && item.revealAtMillis <= now) {
                item.revealed = true;
                changed = true;
                plugin.getServer().broadcastMessage(ChatColor.GOLD + "§l[속보] " + ChatColor.RESET
                        + ChatColor.YELLOW + "(" + item.stockName + ") " + ChatColor.WHITE + item.content);
            }
        }

        List<NewsItem> toRemove = new ArrayList<>();
        for (NewsItem item : items) {
            if (item.revealed && !item.applied && item.applyAtMillis <= now) {
                item.applied = true;
                changed = true;
                if (!item.fake) {
                    Stock stock = stockManager.getStock(item.stockId);
                    if (stock != null) {
                        stock.applyNewsImpact(item.impactPercent);
                        stockManager.saveState();
                    }
                }
                toRemove.add(item);
            }
        }
        items.removeAll(toRemove);

        if (changed) {
            save();
        }
    }

    public List<NewsItem> getPendingItems() {
        return items;
    }
}
