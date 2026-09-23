package com.tntbbp.myminecraft.manager.economy;

import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.log.AdminLog;
import com.tntbbp.myminecraft.log.TradeLogger;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.util.AtomicYaml;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 주식 종목·가격·보유량 관리.
 *
 * <p><b>저장 방식(하나로 정리됨)</b>: 모든 종목의 정의는 {@code stockdata.yml}의 {@code custom-stocks} 목록에,
 * 가격은 {@code prices.<id>}에 저장한다. 종목마다 아이콘 재질({@code icon}), 거래 중지({@code halted}),
 * 수정 번호({@code revision})를 명시적으로 저장하므로 재시작해도 바뀌지 않는다.
 * config.yml의 {@code stock.list}(퍼센트 기반 기본 종목)는 처음 한 번만 {@code custom-stocks}로 옮겨 담고
 * ({@code custom: false}), 그 뒤로는 stockdata.yml만 읽는다.
 *
 * <p>모든 메서드는 메인 스레드에서 호출한다(웹 통로도 메인 스레드로 넘겨서 부른다).
 */
public class StockManager {

    public enum TradeResult {
        SUCCESS,
        NOT_ENOUGH_BALANCE,
        NOT_ENOUGH_QUANTITY,
        INVALID_AMOUNT,
        /** 거래 중지된 종목. */
        HALTED
    }

    public enum AddResult {
        SUCCESS,
        DUPLICATE_NAME,
        INVALID_RANGE,
        INVALID_NAME
    }

    /** 종목 추가 결과. 성공이면 {@code stock}이 새 종목, 실패면 {@code message}에 이유. */
    public record AddOutcome(AddResult result, Stock stock, String message) {
    }

    public enum UpdateStatus {
        OK,
        NOT_FOUND,
        REVISION_CONFLICT,
        INVALID,
        DUPLICATE_NAME
    }

    /** 종목 수정 결과. {@code message}는 실패 이유 또는 성공 시 알림(현재가 조정 등, 없으면 null). */
    public record UpdateOutcome(UpdateStatus status, Stock stock, String message) {
    }

    /** 가격 직접 설정 결과. {@code clamped}면 요청 가격이 범위를 벗어나 범위 끝값으로 맞춰졌다. */
    public record PriceOutcome(Stock stock, double requestedPrice, boolean clamped) {
    }

    /** 한 종목의 보유자. */
    public record Holder(UUID uuid, int quantity) {
    }

    /** 종목 수정 내용. null인 필드는 바꾸지 않는다. */
    public static final class StockPatch {
        public String name;
        public Material icon;
        public Double minPrice;
        public Double maxPrice;
        public Double changeUnit;
        public String business;

        public boolean isEmpty() {
            return name == null && icon == null && minPrice == null && maxPrice == null && changeUnit == null
                    && business == null;
        }
    }

    private static final Material[] CUSTOM_ICONS = {
            Material.EMERALD, Material.IRON_INGOT, Material.COAL, Material.REDSTONE,
            Material.LAPIS_LAZULI, Material.QUARTZ, Material.GLOWSTONE_DUST, Material.PRISMARINE_CRYSTALS,
            Material.COPPER_INGOT, Material.ECHO_SHARD
    };

    static final String REASON_FLUCTUATION = "정기 변동";
    static final String REASON_RANGE_CLAMP = "가격 범위 변경에 따른 조정";

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

        boolean migrated = loadStocks();
        restorePrices();
        this.lastUpdateMillis = data.getLong("last-update-millis", System.currentTimeMillis());
        if (migrated) {
            // 아이콘·중지 상태·revision을 처음 명시 저장한다(이후 재시작해도 그대로).
            saveState();
        }
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

    // ---------------------------------------------------------------- 불러오기 · 이전(migration)

    /** stockdata.yml의 종목 + (처음 한 번) config stock.list를 불러온다. 저장 형식을 새로 채웠으면 true. */
    private boolean loadStocks() {
        boolean migrated = false;
        Set<String> seen = new HashSet<>();
        for (Map<?, ?> raw : data.getMapList("custom-stocks")) {
            String id = text(raw.get("id"));
            if (id == null || id.isBlank()) {
                plugin.getLogger().warning("stockdata.yml custom-stocks에 id가 없는 항목이 있어 건너뜁니다.");
                continue;
            }
            if (!seen.add(id.toLowerCase(Locale.ROOT))) {
                plugin.getLogger().warning("stockdata.yml custom-stocks에 같은 id(" + id + ")가 두 번 있어 뒤의 것을 건너뜁니다.");
                continue;
            }
            String name = text(raw.get("name"));
            if (name == null || name.isBlank()) {
                name = id;
            }
            String business = raw.get("business") != null ? String.valueOf(raw.get("business")) : "";
            double minPrice = number(raw.get("min-price"), 1.0);
            double maxPrice = number(raw.get("max-price"), 0.0);

            Material icon = parseIcon(text(raw.get("icon")));
            if (icon == null) {
                if (raw.get("icon") != null) {
                    plugin.getLogger().warning("종목 '" + name + "'의 아이콘(" + raw.get("icon")
                            + ")을 알 수 없어 기본 아이콘으로 바꿉니다.");
                }
                icon = defaultIcon(id);
                migrated = true;
            }

            Stock stock;
            if (Boolean.FALSE.equals(raw.get("custom"))) {
                double maxChange = number(raw.get("max-change-percent"), 10.0);
                stock = new Stock(id, name, icon, minPrice, minPrice, maxChange, business);
                if (maxPrice > 0) {
                    stock.setPriceRange(minPrice, maxPrice);
                }
            } else {
                double changeUnit = number(raw.get("change-unit"), 1.0);
                stock = new Stock(id, name, icon, minPrice, minPrice, maxPrice, changeUnit, business);
            }

            if (raw.get("halted") instanceof Boolean halted) {
                stock.setHalted(halted);
            } else {
                migrated = true;
            }
            if (raw.get("revision") instanceof Number revision) {
                stock.restoreRevision(revision.intValue());
            } else {
                migrated = true;
            }
            stocks.add(stock);
        }

        // config.yml stock.list(퍼센트 기반 기본 종목)는 처음 한 번만 옮겨 담는다. 예전처럼 목록 앞쪽에 둔다.
        List<Stock> seeded = new ArrayList<>();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("stock.list")) {
            String id = text(raw.get("id"));
            if (id == null || id.isBlank() || !seen.add(id.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String name = text(raw.get("name"));
            if (name == null || name.isBlank()) {
                name = id;
            }
            Material material = parseIcon(text(raw.get("material")));
            if (material == null) {
                material = Material.PAPER;
            }
            double price = number(raw.get("price"), 100.0);
            double minPrice = number(raw.get("min-price"), 1.0);
            double maxChange = number(raw.get("max-change-percent"), 10.0);
            String business = raw.get("business") != null ? String.valueOf(raw.get("business")) : "";
            seeded.add(new Stock(id, name, material, price, minPrice, maxChange, business));
            plugin.getLogger().info("config.yml stock.list의 종목 '" + name + "'을(를) stockdata.yml로 옮겼습니다.");
        }
        if (!seeded.isEmpty()) {
            stocks.addAll(0, seeded);
            migrated = true;
        }
        return migrated;
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

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    /** 재질 이름 → 아이템으로 쓸 수 있는 재질. 모르거나 아이템이 아니면 null. */
    public static Material parseIcon(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(name.strip());
        if (material == null || material.isAir() || !material.isItem()) {
            return null;
        }
        return material;
    }

    private static Material defaultIcon(String id) {
        return CUSTOM_ICONS[StockRules.paletteIndex(id, CUSTOM_ICONS.length)];
    }

    // ---------------------------------------------------------------- 종목 추가 · 수정

    /**
     * 관리자가 새 주식 종목을 추가한다. 아이디는 이름 기반으로 자동 생성되며, 시작 가격은 최소값으로 설정된다.
     *
     * @param icon  아이콘 재질. null이면 기본 팔레트에서 하나 골라 <b>저장</b>한다.
     * @param actor 기록용 추가한 사람(웹 사용자 이름, 플레이어 이름, "console" 등)
     */
    public AddOutcome addCustomStock(String name, double minPrice, double maxPrice, double changeUnit,
                                     String businessType, Material icon, String actor) {
        String nameError = StockRules.validateName(name);
        if (nameError != null) {
            return new AddOutcome(AddResult.INVALID_NAME, null, nameError);
        }
        String businessError = StockRules.validateBusiness(businessType);
        if (businessError != null) {
            return new AddOutcome(AddResult.INVALID_NAME, null, businessError);
        }
        String rangeError = StockRules.validateDefinition(true, minPrice, maxPrice, changeUnit);
        if (rangeError != null) {
            return new AddOutcome(AddResult.INVALID_RANGE, null, rangeError);
        }
        String trimmedName = name.strip();
        String id = StockRules.idForName(trimmedName);
        if (getStock(id) != null || getStockByName(trimmedName) != null) {
            return new AddOutcome(AddResult.DUPLICATE_NAME, null, "같은 이름(또는 id " + id + ")의 종목이 이미 있습니다.");
        }

        Material chosenIcon = icon != null ? icon : defaultIcon(id);
        Stock stock = new Stock(id, trimmedName, chosenIcon, minPrice, minPrice, maxPrice, changeUnit,
                businessType == null ? "" : businessType.strip());
        stocks.add(stock);
        saveState();

        AdminLog adminLog = plugin.getAdminLog();
        if (adminLog != null) {
            adminLog.stockCreate(actor, id, trimmedName);
        }
        return new AddOutcome(AddResult.SUCCESS, stock, null);
    }

    /**
     * 종목 정의를 바꾼다(이름·아이콘·가격 범위·변동 단위·업종). 바뀐 것이 있으면 revision+1.
     * 현재가가 새 범위를 벗어나면 범위 안으로 맞추고 {@code stock_price}(manual)로 기록한다.
     *
     * @param expectedRevision 웹이 알고 있는 revision(If-Match). null이면 비교하지 않는다.
     */
    public UpdateOutcome updateStock(String id, StockPatch patch, Integer expectedRevision, String actor) {
        Stock stock = getStock(id);
        if (stock == null) {
            return new UpdateOutcome(UpdateStatus.NOT_FOUND, null, "없는 종목입니다: " + id);
        }
        if (expectedRevision != null && expectedRevision != stock.getRevision()) {
            return new UpdateOutcome(UpdateStatus.REVISION_CONFLICT, stock,
                    "다른 곳에서 먼저 수정되었습니다 (현재 revision " + stock.getRevision() + ").");
        }

        String newName = stock.getName();
        if (patch.name != null) {
            String error = StockRules.validateName(patch.name);
            if (error != null) {
                return new UpdateOutcome(UpdateStatus.INVALID, stock, error);
            }
            newName = patch.name.strip();
            Stock sameName = getStockByName(newName);
            if (sameName != null && sameName != stock) {
                return new UpdateOutcome(UpdateStatus.DUPLICATE_NAME, stock, "같은 이름의 종목이 이미 있습니다: " + newName);
            }
        }
        String newBusiness = stock.getBusinessType();
        if (patch.business != null) {
            String error = StockRules.validateBusiness(patch.business);
            if (error != null) {
                return new UpdateOutcome(UpdateStatus.INVALID, stock, error);
            }
            newBusiness = patch.business.strip();
        }
        if (!stock.isCustom() && patch.changeUnit != null) {
            return new UpdateOutcome(UpdateStatus.INVALID, stock, "퍼센트 기반 종목은 change_unit 을 바꿀 수 없습니다.");
        }
        double newMin = patch.minPrice != null ? patch.minPrice : stock.getMinPrice();
        double newMax = patch.maxPrice != null ? patch.maxPrice : stock.getMaxPrice();
        double newUnit = patch.changeUnit != null ? patch.changeUnit : stock.getChangeUnit();
        String rangeError = StockRules.validateDefinition(stock.isCustom(), newMin, newMax, newUnit);
        if (rangeError != null) {
            return new UpdateOutcome(UpdateStatus.INVALID, stock, rangeError);
        }
        Material newIcon = patch.icon != null ? patch.icon : stock.getMaterial();

        JsonObject fields = new JsonObject();
        if (!newName.equals(stock.getName())) {
            fields.addProperty("name", newName);
        }
        if (newIcon != stock.getMaterial()) {
            fields.addProperty("icon_material", newIcon.name());
        }
        if (newMin != stock.getMinPrice()) {
            fields.addProperty("min_price", newMin);
        }
        if (newMax != stock.getMaxPrice()) {
            fields.addProperty("max_price", newMax);
        }
        if (newUnit != stock.getChangeUnit()) {
            fields.addProperty("change_unit", newUnit);
        }
        if (!newBusiness.equals(stock.getBusinessType())) {
            fields.addProperty("business", newBusiness);
        }
        if (fields.size() == 0) {
            return new UpdateOutcome(UpdateStatus.OK, stock, null);
        }

        stock.setName(newName);
        stock.setMaterial(newIcon);
        stock.setPriceRange(newMin, newMax);
        stock.setChangeUnit(newUnit);
        stock.setBusinessType(newBusiness);
        String message = null;
        double before = stock.getPrice();
        if (stock.clampPriceToRange()) {
            message = "현재가 " + before + " 이(가) 새 가격 범위를 벗어나 " + stock.getPrice() + " (으)로 조정했습니다.";
            logPrice(stock, REASON_RANGE_CLAMP, AdminLog.SOURCE_MANUAL, actor);
        }
        stock.bumpRevision();
        saveState();

        AdminLog adminLog = plugin.getAdminLog();
        if (adminLog != null) {
            adminLog.stockUpdate(actor, stock.getId(), fields);
        }
        return new UpdateOutcome(UpdateStatus.OK, stock, message);
    }

    /**
     * 관리자가 가격을 직접 정한다(가격 범위로 맞춤). 직전 가격은 바뀌기 전 가격이 되고 revision+1,
     * {@code stock_price}(manual)로 기록한다. 없는 종목이면 null.
     */
    public PriceOutcome setPrice(String id, double price, String reason, String actor) {
        Stock stock = getStock(id);
        if (stock == null) {
            return null;
        }
        double applied = stock.applyManualPrice(price);
        stock.bumpRevision();
        saveState();
        logPrice(stock, reason, AdminLog.SOURCE_MANUAL, actor);
        return new PriceOutcome(stock, price, applied != StockRules.round2(price));
    }

    /** 거래 중지. 없는 종목이면 null. 이미 중지돼 있으면 아무것도 바꾸지 않는다. */
    public Stock halt(String id, String reason, String actor) {
        return setHalted(id, true, reason, actor);
    }

    /** 거래 재개. 없는 종목이면 null. 이미 거래 중이면 아무것도 바꾸지 않는다. */
    public Stock resume(String id, String reason, String actor) {
        return setHalted(id, false, reason, actor);
    }

    private Stock setHalted(String id, boolean halted, String reason, String actor) {
        Stock stock = getStock(id);
        if (stock == null) {
            return null;
        }
        if (stock.isHalted() == halted) {
            return stock;
        }
        stock.setHalted(halted);
        stock.bumpRevision();
        saveState();
        AdminLog adminLog = plugin.getAdminLog();
        if (adminLog != null) {
            if (halted) {
                adminLog.stockHalt(actor, stock.getId(), reason);
            } else {
                adminLog.stockResume(actor, stock.getId(), reason);
            }
        }
        return stock;
    }

    /** 뉴스를 가격에 반영하고 {@code stock_price}(news)로 기록한다. 거래 중지 종목에도 반영한다. */
    public void applyNewsImpact(Stock stock, double impactPercent, int newsId) {
        stock.applyNewsImpact(impactPercent);
        saveState();
        logPrice(stock, "뉴스 #" + newsId + " 반영", AdminLog.SOURCE_NEWS, AdminLog.SYSTEM);
    }

    private void logPrice(Stock stock, String reason, String source, String actor) {
        AdminLog adminLog = plugin.getAdminLog();
        if (adminLog != null) {
            adminLog.stockPrice(stock.getId(), stock.getPrice(), stock.getPreviousPrice(), reason, source, actor);
        }
    }

    // ---------------------------------------------------------------- 정기 변동

    public void startFluctuationTask() {
        long intervalMillis = plugin.getConfig().getLong("stock.update-interval-seconds", 3600) * 1000L;
        long elapsed = System.currentTimeMillis() - lastUpdateMillis;

        long initialDelayTicks;
        if (elapsed >= intervalMillis) {
            fluctuateAll();
            initialDelayTicks = intervalMillis / 50L;
        } else {
            initialDelayTicks = (intervalMillis - elapsed) / 50L;
        }

        long periodTicks = intervalMillis / 50L;
        fluctuationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::fluctuateAll,
                initialDelayTicks, periodTicks);
    }

    /** 거래 중지가 아닌 종목을 모두 변동시키고, 종목마다 {@code stock_price}(fluctuation)를 남긴다. */
    private void fluctuateAll() {
        List<Stock> changed = new ArrayList<>();
        for (Stock stock : stocks) {
            if (stock.isHalted()) {
                continue;
            }
            stock.fluctuate();
            changed.add(stock);
        }
        lastUpdateMillis = System.currentTimeMillis();
        saveState();
        for (Stock stock : changed) {
            logPrice(stock, REASON_FLUCTUATION, AdminLog.SOURCE_FLUCTUATION, AdminLog.SYSTEM);
        }
    }

    public void stopFluctuationTask() {
        if (fluctuationTask != null) {
            fluctuationTask.cancel();
        }
        saveState();
    }

    // ---------------------------------------------------------------- 조회

    /** 모든 종목(표시 순서). 읽기 전용. */
    public List<Stock> getStocks() {
        return Collections.unmodifiableList(stocks);
    }

    public Stock getStock(String id) {
        if (id == null) {
            return null;
        }
        for (Stock stock : stocks) {
            if (stock.getId().equalsIgnoreCase(id)) {
                return stock;
            }
        }
        return null;
    }

    /** 이름(표시명)으로 종목을 찾는다. 관리자 명령어에서 사용. */
    public Stock getStockByName(String name) {
        if (name == null) {
            return null;
        }
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
        Stock stock = getStock(stockId);
        return section.getInt(stock != null ? stock.getId() : stockId, 0);
    }

    /** 한 플레이어의 보유 종목(수량 1 이상, 종목 표시 순서). 오프라인이어도 된다. */
    public Map<String, Integer> getHoldings(UUID uuid) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ConfigurationSection section = holdingsData.getConfigurationSection(uuid.toString());
        if (section == null) {
            return result;
        }
        for (Stock stock : stocks) {
            int quantity = section.getInt(stock.getId(), 0);
            if (quantity > 0) {
                result.put(stock.getId(), quantity);
            }
        }
        return result;
    }

    /** 한 종목의 보유자 목록(수량 많은 순). stockholdings.yml 전체를 훑는다. 없는 종목이면 빈 목록. */
    public List<Holder> holdersOf(String stockId) {
        Stock stock = getStock(stockId);
        List<Holder> holders = new ArrayList<>();
        if (stock == null) {
            return holders;
        }
        for (String key : holdingsData.getKeys(false)) {
            ConfigurationSection section = holdingsData.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            int quantity = section.getInt(stock.getId(), 0);
            if (quantity <= 0) {
                continue;
            }
            try {
                holders.add(new Holder(UUID.fromString(key), quantity));
            } catch (IllegalArgumentException ignored) {
                // UUID가 아닌 키는 무시
            }
        }
        holders.sort(Comparator.comparingInt(Holder::quantity).reversed()
                .thenComparing(holder -> holder.uuid().toString()));
        return holders;
    }

    // ---------------------------------------------------------------- 지급 · 매매

    /**
     * 관리자가 대가 없이 주식을 지급한다 (경제 시스템과 무관). 오프라인 플레이어도 된다.
     * 거래 기록 {@code admin_stock_grant}와 관리 기록 {@code stock_grant}를 남긴다.
     *
     * @return 지급 후 보유량. 수량이 1 미만이거나 없는 종목이면 -1.
     */
    public int giveHolding(UUID uuid, String stockId, int amount, String actor) {
        Stock stock = getStock(stockId);
        if (amount <= 0 || stock == null) {
            return -1;
        }
        int holding = getHolding(uuid, stock.getId()) + amount;
        setHolding(uuid, stock.getId(), holding);
        TradeLogger tradeLogger = plugin.getTradeLogger();
        if (tradeLogger != null) {
            tradeLogger.adminStockGrant(actor, uuid, playerName(uuid), stock.getId(), amount);
        }
        AdminLog adminLog = plugin.getAdminLog();
        if (adminLog != null) {
            adminLog.stockGrant(actor, uuid, stock.getId(), amount);
        }
        return holding;
    }

    public TradeResult buy(UUID uuid, String stockId, int amount) {
        if (amount <= 0) {
            return TradeResult.INVALID_AMOUNT;
        }
        Stock stock = getStock(stockId);
        if (stock == null) {
            return TradeResult.INVALID_AMOUNT;
        }
        if (stock.isHalted()) {
            return TradeResult.HALTED;
        }
        double unitPrice = stock.getPrice();
        // 최소가를 0으로 둔 종목은 주가가 0까지 떨어질 수 있다. 그때 사면 공짜로 무한히 살 수 있고,
        // 가격이 조금만 올라도 전부 팔아 G를 찍어낼 수 있으므로 0원 매수는 거래 정지로 막는다.
        if (!(unitPrice > 0)) {
            return TradeResult.HALTED;
        }
        double cost = unitPrice * amount;
        if (!economyManager.subtract(uuid, cost)) {
            return TradeResult.NOT_ENOUGH_BALANCE;
        }
        setHolding(uuid, stock.getId(), getHolding(uuid, stock.getId()) + amount);
        TradeLogger tradeLogger = plugin.getTradeLogger();
        if (tradeLogger != null) {
            tradeLogger.stockBuy(uuid, playerName(uuid), stock.getId(), stock.getName(), amount, unitPrice, cost);
        }
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
        if (stock.isHalted()) {
            return TradeResult.HALTED;
        }
        int owned = getHolding(uuid, stock.getId());
        if (owned < amount) {
            return TradeResult.NOT_ENOUGH_QUANTITY;
        }
        double unitPrice = stock.getPrice();
        double total = unitPrice * amount;
        setHolding(uuid, stock.getId(), owned - amount);
        economyManager.add(uuid, total);
        TradeLogger tradeLogger = plugin.getTradeLogger();
        if (tradeLogger != null) {
            tradeLogger.stockSell(uuid, playerName(uuid), stock.getId(), stock.getName(), amount, unitPrice, total);
        }
        return TradeResult.SUCCESS;
    }

    private static String playerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName();
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
            AtomicYaml.save(holdingsData, holdingsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("stockholdings.yml 저장 실패: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 저장

    /**
     * 종목 정의(아이콘·중지 상태·revision 포함)와 현재 가격/직전 가격/마지막 갱신 시각을 저장해
     * 서버 재시작 후에도 유지되게 한다.
     */
    public void saveState() {
        data.set("last-update-millis", lastUpdateMillis);
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (Stock stock : stocks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", stock.getId());
            entry.put("name", stock.getName());
            entry.put("icon", stock.getMaterial().name());
            if (!stock.isCustom()) {
                entry.put("custom", false);
                entry.put("max-change-percent", stock.getMaxChangePercent());
            }
            entry.put("min-price", stock.getMinPrice());
            entry.put("max-price", stock.getMaxPrice());
            if (stock.isCustom()) {
                entry.put("change-unit", stock.getChangeUnit());
            }
            entry.put("business", stock.getBusinessType());
            entry.put("halted", stock.isHalted());
            entry.put("revision", stock.getRevision());
            definitions.add(entry);

            data.set("prices." + stock.getId() + ".price", stock.getPrice());
            data.set("prices." + stock.getId() + ".previous-price", stock.getPreviousPrice());
        }
        data.set("custom-stocks", definitions);
        try {
            AtomicYaml.save(data, dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("stockdata.yml 저장 실패: " + e.getMessage());
        }
    }
}
