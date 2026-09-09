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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class StockManager {

    public enum TradeResult {
        SUCCESS,
        NOT_ENOUGH_BALANCE,
        NOT_ENOUGH_QUANTITY,
        INVALID_AMOUNT
    }

    public enum AddResult {
        SUCCESS,
        DUPLICATE_NAME,
        INVALID_RANGE
    }

    private static final Material[] CUSTOM_ICONS = {
            Material.EMERALD, Material.IRON_INGOT, Material.COAL, Material.REDSTONE,
            Material.LAPIS_LAZULI, Material.QUARTZ, Material.GLOWSTONE_DUST, Material.PRISMARINE_CRYSTALS,
            Material.COPPER_INGOT, Material.ECHO_SHARD
    };

    private final MyMinecraftPlugin plugin;
    private final EconomyManager economyManager;
    private final File holdingsFile;
    private final YamlConfiguration holdingsData;
    private final File dataFile;
    private final YamlConfiguration data;
    private final List<Stock> stocks = new ArrayList<>();
    private BukkitTask fluctuationTask;
    private long lastUpdateMillis;

    public StockManager(MyMinecraftPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.holdingsFile = new File(plugin.getDataFolder(), "stockholdings.yml");
        ensureExists(holdingsFile, "stockholdings.yml");
        this.holdingsData = YamlConfiguration.loadConfiguration(holdingsFile);

        this.dataFile = new File(plugin.getDataFolder(), "stockdata.yml");
        ensureExists(dataFile, "stockdata.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);

        loadStocksFromConfig();
        loadCustomStocks();
        restorePrices();
        this.lastUpdateMillis = data.getLong("last-update-millis", System.currentTimeMillis());
    }

    private void ensureExists(File file, String label) {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe(label + " 생성 실패: " + e.getMessage());
            }
        }
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
            String business = raw.get("business") != null ? String.valueOf(raw.get("business")) : "";
            stocks.add(new Stock(id, name, material, price, minPrice, maxChange, business));
        }
    }

    private void loadCustomStocks() {
        List<Map<?, ?>> list = data.getMapList("custom-stocks");
        int index = 0;
        for (Map<?, ?> raw : list) {
            String id = String.valueOf(raw.get("id"));
            String name = String.valueOf(raw.get("name"));
            double minPrice = raw.get("min-price") instanceof Number n ? n.doubleValue() : 1.0;
            double maxPrice = raw.get("max-price") instanceof Number n ? n.doubleValue() : 0.0;
            double changeUnit = raw.get("change-unit") instanceof Number n ? n.doubleValue() : 1.0;
            String business = raw.get("business") != null ? String.valueOf(raw.get("business")) : "";
            Material icon = CUSTOM_ICONS[Math.abs(id.hashCode() + index) % CUSTOM_ICONS.length];
            stocks.add(new Stock(id, name, icon, minPrice, minPrice, maxPrice, changeUnit, business));
            index++;
        }
    }

    private void restorePrices() {
        ConfigurationSection section = data.getConfigurationSection("prices");
        if (section == null) {
            return;
        }
        for (Stock stock : stocks) {
            ConfigurationSection stockSection = section.getConfigurationSection(stock.getId());
            if (stockSection == null) {
                continue;
            }
            double price = stockSection.getDouble("price", stock.getPrice());
            double previousPrice = stockSection.getDouble("previous-price", price);
            stock.restoreState(price, previousPrice);
        }
    }

    /** 관리자가 새 주식 종목을 추가한다. 아이디는 이름 기반으로 자동 생성되며, 시작 가격은 최소값으로 설정된다. */
    public AddResult addCustomStock(String name, double minPrice, double maxPrice, double changeUnit, String businessType) {
        if (minPrice < 0 || maxPrice <= minPrice || changeUnit <= 0) {
            return AddResult.INVALID_RANGE;
        }
        String id = "custom_" + name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        if (getStock(id) != null) {
            return AddResult.DUPLICATE_NAME;
        }

        Material icon = CUSTOM_ICONS[Math.abs(id.hashCode()) % CUSTOM_ICONS.length];
        Stock stock = new Stock(id, name, icon, minPrice, minPrice, maxPrice, changeUnit, businessType);
        stocks.add(stock);

        List<Map<?, ?>> list = new ArrayList<>(data.getMapList("custom-stocks"));
        Map<String, Object> entry = Map.of(
                "id", id,
                "name", name,
                "min-price", minPrice,
                "max-price", maxPrice,
                "change-unit", changeUnit,
                "business", businessType
        );
        list.add(entry);
        data.set("custom-stocks", list);
        saveState();
        return AddResult.SUCCESS;
    }

    public void startFluctuationTask() {
        long intervalMillis = plugin.getConfig().getLong("stock.update-interval-seconds", 3600) * 1000L;
        long elapsed = System.currentTimeMillis() - lastUpdateMillis;

        long initialDelayTicks;
        if (elapsed >= intervalMillis) {
            for (Stock stock : stocks) {
                stock.fluctuate();
            }
            lastUpdateMillis = System.currentTimeMillis();
            saveState();
            initialDelayTicks = intervalMillis / 50L;
        } else {
            initialDelayTicks = (intervalMillis - elapsed) / 50L;
        }

        long periodTicks = intervalMillis / 50L;
        fluctuationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Stock stock : stocks) {
                stock.fluctuate();
            }
            lastUpdateMillis = System.currentTimeMillis();
            saveState();
        }, initialDelayTicks, periodTicks);
    }

    public void stopFluctuationTask() {
        if (fluctuationTask != null) {
            fluctuationTask.cancel();
        }
        saveState();
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

    /** 이름(표시명)으로 종목을 찾는다. 관리자 명령어에서 사용. */
    public Stock getStockByName(String name) {
        for (Stock stock : stocks) {
            if (stock.getName().equalsIgnoreCase(name)) {
                return stock;
            }
        }
        return null;
    }

    public int getHolding(UUID uuid, String stockId) {
        ConfigurationSection section = holdingsData.getConfigurationSection(uuid.toString());
        if (section == null) {
            return 0;
        }
        return section.getInt(stockId, 0);
    }

    /** 관리자가 대가 없이 주식을 지급한다 (경제 시스템과 무관). */
    public boolean giveHolding(UUID uuid, String stockId, int amount) {
        if (amount <= 0 || getStock(stockId) == null) {
            return false;
        }
        setHolding(uuid, stockId, getHolding(uuid, stockId) + amount);
        return true;
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
        ConfigurationSection section = holdingsData.getConfigurationSection(uuid.toString());
        if (section == null) {
            section = holdingsData.createSection(uuid.toString());
        }
        section.set(stockId, amount);
        saveHoldings();
    }

    private void saveHoldings() {
        try {
            holdingsData.save(holdingsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("stockholdings.yml 저장 실패: " + e.getMessage());
        }
    }

    /** 현재 가격/직전 가격/마지막 갱신 시각을 저장해 서버 재시작 후에도 유지되게 한다. */
    public void saveState() {
        data.set("last-update-millis", lastUpdateMillis);
        for (Stock stock : stocks) {
            data.set("prices." + stock.getId() + ".price", stock.getPrice());
            data.set("prices." + stock.getId() + ".previous-price", stock.getPreviousPrice());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("stockdata.yml 저장 실패: " + e.getMessage());
        }
    }
}
