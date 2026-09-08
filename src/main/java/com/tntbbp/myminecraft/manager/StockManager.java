package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.model.Stock;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StockManager {

    public enum TradeResult {
        SUCCESS,
        NOT_ENOUGH_BALANCE,
        NOT_ENOUGH_QUANTITY,
        INVALID_AMOUNT
    }

    private final MyMinecraftPlugin plugin;
    private final EconomyManager economyManager;
    private final File file;
    private final YamlConfiguration data;
    private final List<Stock> stocks = new ArrayList<>();
    private BukkitTask fluctuationTask;

    public StockManager(MyMinecraftPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.file = new File(plugin.getDataFolder(), "stockholdings.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("stockholdings.yml 생성 실패: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        loadStocksFromConfig();
    }

    private void loadStocksFromConfig() {
        List<Map<?, ?>> list = plugin.getConfig().getMapList("stock.list");
        for (Map<?, ?> raw : list) {
            String id = String.valueOf(raw.get("id"));
            String name = String.valueOf(raw.get("name"));
            Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (material == null) {
                material = Material.PAPER;
            }
            double price = raw.get("price") instanceof Number n ? n.doubleValue() : 100.0;
            double minPrice = raw.get("min-price") instanceof Number n ? n.doubleValue() : 1.0;
            double maxChange = raw.get("max-change-percent") instanceof Number n ? n.doubleValue() : 10.0;
            stocks.add(new Stock(id, name, material, price, minPrice, maxChange));
        }
    }

    public void startFluctuationTask() {
        long intervalTicks = plugin.getConfig().getLong("stock.update-interval-seconds", 300) * 20L;
        fluctuationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Stock stock : stocks) {
                stock.fluctuate();
            }
        }, intervalTicks, intervalTicks);
    }

    public void stopFluctuationTask() {
        if (fluctuationTask != null) {
            fluctuationTask.cancel();
        }
    }

    public List<Stock> getStocks() {
        return stocks;
    }

    public Stock getStock(String id) {
        for (Stock stock : stocks) {
            if (stock.getId().equalsIgnoreCase(id)) {
                return stock;
            }
        }
        return null;
    }

    public int getHolding(UUID uuid, String stockId) {
        ConfigurationSection section = data.getConfigurationSection(uuid.toString());
        if (section == null) {
            return 0;
        }
        return section.getInt(stockId, 0);
    }

    public TradeResult buy(UUID uuid, String stockId, int amount) {
        if (amount <= 0) {
            return TradeResult.INVALID_AMOUNT;
        }
        Stock stock = getStock(stockId);
        if (stock == null) {
            return TradeResult.INVALID_AMOUNT;
        }
        double cost = stock.getPrice() * amount;
        if (!economyManager.subtract(uuid, cost)) {
            return TradeResult.NOT_ENOUGH_BALANCE;
        }
        setHolding(uuid, stockId, getHolding(uuid, stockId) + amount);
        return TradeResult.SUCCESS;
    }

    public TradeResult sell(UUID uuid, String stockId, int amount) {
        if (amount <= 0) {
            return TradeResult.INVALID_AMOUNT;
        }
        Stock stock = getStock(stockId);
        if (stock == null) {
            return TradeResult.INVALID_AMOUNT;
        }
        int owned = getHolding(uuid, stockId);
        if (owned < amount) {
            return TradeResult.NOT_ENOUGH_QUANTITY;
        }
        setHolding(uuid, stockId, owned - amount);
        economyManager.add(uuid, stock.getPrice() * amount);
        return TradeResult.SUCCESS;
    }

    private void setHolding(UUID uuid, String stockId, int amount) {
        ConfigurationSection section = data.getConfigurationSection(uuid.toString());
        if (section == null) {
            section = data.createSection(uuid.toString());
        }
        section.set(stockId, amount);
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("stockholdings.yml 저장 실패: " + e.getMessage());
        }
    }
}
