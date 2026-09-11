package com.tntbbp.myminecraft;

import com.tntbbp.myminecraft.command.AdminMenuCommand;
import com.tntbbp.myminecraft.command.EcCommand;
import com.tntbbp.myminecraft.command.HomeCommand;
import com.tntbbp.myminecraft.command.NewsCommand;
import com.tntbbp.myminecraft.command.SpecialItemSummonCommand;
import com.tntbbp.myminecraft.command.StockAddCommand;
import com.tntbbp.myminecraft.command.StockGiveCommand;
import com.tntbbp.myminecraft.command.LobbyCommand;
import com.tntbbp.myminecraft.command.MenuCommand;
import com.tntbbp.myminecraft.command.RtCommand;
import com.tntbbp.myminecraft.command.SpawnCommand;
import com.tntbbp.myminecraft.command.TpaCommand;
import com.tntbbp.myminecraft.listener.CombatListener;
import com.tntbbp.myminecraft.listener.GUIListener;
import com.tntbbp.myminecraft.listener.LaevateinnListener;
import com.tntbbp.myminecraft.listener.LaevateinnModelListener;
import com.tntbbp.myminecraft.listener.StarforceListener;
import com.tntbbp.myminecraft.manager.CombatManager;
import com.tntbbp.myminecraft.manager.CurrencyManager;
import com.tntbbp.myminecraft.manager.EconomyManager;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.GeminiNewsClient;
import com.tntbbp.myminecraft.manager.GradeManager;
import com.tntbbp.myminecraft.manager.HomeManager;
import com.tntbbp.myminecraft.manager.InfernalBurnManager;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import com.tntbbp.myminecraft.manager.LocationsManager;
import com.tntbbp.myminecraft.manager.NewsManager;
import com.tntbbp.myminecraft.manager.RandomTeleportManager;
import com.tntbbp.myminecraft.manager.StarforceManager;
import com.tntbbp.myminecraft.manager.StockManager;
import com.tntbbp.myminecraft.manager.TeleportRequestManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MyMinecraftPlugin extends JavaPlugin {

    private EconomyManager economyManager;
    private HomeManager homeManager;
    private LocationsManager locationsManager;
    private TeleportRequestManager teleportRequestManager;
    private StockManager stockManager;
    private RandomTeleportManager randomTeleportManager;
    private EnhanceManager enhanceManager;
    private GradeManager gradeManager;
    private StarforceManager starforceManager;
    private InfernalBurnManager infernalBurnManager;
    private LaevateinnManager laevateinnManager;
    private CurrencyManager currencyManager;
    private NewsManager newsManager;
    private GeminiNewsClient geminiNewsClient;
    private CombatManager combatManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.economyManager = new EconomyManager(this);
        this.homeManager = new HomeManager(this);
        this.locationsManager = new LocationsManager(this);
        this.teleportRequestManager = new TeleportRequestManager(this);
        this.stockManager = new StockManager(this, economyManager);
        this.randomTeleportManager = new RandomTeleportManager(this);
        this.enhanceManager = new EnhanceManager(this);
        this.gradeManager = new GradeManager(this);
        this.starforceManager = new StarforceManager(this);
        this.infernalBurnManager = new InfernalBurnManager(this);
        this.laevateinnManager = new LaevateinnManager(this);
        this.currencyManager = new CurrencyManager(this);
        this.newsManager = new NewsManager(this, stockManager);
        this.geminiNewsClient = new GeminiNewsClient(this);
        this.combatManager = new CombatManager(this);

        stockManager.startFluctuationTask();
        infernalBurnManager.start();
        newsManager.startTask();

        TpaCommand tpaCommand = new TpaCommand(this);
        getCommand("텔레포트요청").setExecutor(tpaCommand);
        getCommand("텔레포트수락").setExecutor(tpaCommand);
        getCommand("텔레포트거절").setExecutor(tpaCommand);

        HomeCommand homeCommand = new HomeCommand(this);
        getCommand("홈").setExecutor(homeCommand);
        getCommand("홈설정").setExecutor(homeCommand);
        getCommand("홈삭제").setExecutor(homeCommand);

        getCommand("엔더상자").setExecutor(new EcCommand());
        getCommand("메뉴").setExecutor(new MenuCommand(this));
        getCommand("로비").setExecutor(new LobbyCommand(this));
        getCommand("스폰").setExecutor(new SpawnCommand(this));
        getCommand("랜덤이동").setExecutor(new RtCommand(this));
        SpecialItemSummonCommand specialItemSummonCommand = new SpecialItemSummonCommand(this);
        getCommand("특수아이템소환").setExecutor(specialItemSummonCommand);
        getCommand("특수아이템소환").setTabCompleter(specialItemSummonCommand);

        getCommand("주식종류추가").setExecutor(new StockAddCommand(this));

        StockGiveCommand stockGiveCommand = new StockGiveCommand(this);
        getCommand("주식지급").setExecutor(stockGiveCommand);
        getCommand("주식지급").setTabCompleter(stockGiveCommand);

        getCommand("관리자메뉴").setExecutor(new AdminMenuCommand(this));

        NewsCommand newsCommand = new NewsCommand(this);
        getCommand("뉴스작성").setExecutor(newsCommand);
        getCommand("뉴스작성").setTabCompleter(newsCommand);
        getCommand("가짜뉴스작성").setExecutor(newsCommand);
        getCommand("가짜뉴스작성").setTabCompleter(newsCommand);

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new LaevateinnListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new StarforceListener(this), this);

        if (getServer().getPluginManager().isPluginEnabled("BetterModel")) {
            getServer().getPluginManager().registerEvents(new LaevateinnModelListener(this), this);
            getLogger().info("BetterModel 연동: 레바테인 3D 모델 표시 기능이 활성화되었습니다.");
        } else {
            getLogger().info("BetterModel이 설치되어 있지 않아 레바테인 3D 모델 표시 기능은 비활성화됩니다.");
        }

        getLogger().info("MyMinecraft 플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (stockManager != null) {
            stockManager.stopFluctuationTask();
        }
        if (infernalBurnManager != null) {
            infernalBurnManager.stop();
        }
        if (newsManager != null) {
            newsManager.stopTask();
        }
        getLogger().info("MyMinecraft 플러그인이 비활성화되었습니다.");
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public LocationsManager getLocationsManager() {
        return locationsManager;
    }

    public TeleportRequestManager getTeleportRequestManager() {
        return teleportRequestManager;
    }

    public StockManager getStockManager() {
        return stockManager;
    }

    public RandomTeleportManager getRandomTeleportManager() {
        return randomTeleportManager;
    }

    public EnhanceManager getEnhanceManager() {
        return enhanceManager;
    }

    public GradeManager getGradeManager() {
        return gradeManager;
    }

    public StarforceManager getStarforceManager() {
        return starforceManager;
    }

    public InfernalBurnManager getInfernalBurnManager() {
        return infernalBurnManager;
    }

    public LaevateinnManager getLaevateinnManager() {
        return laevateinnManager;
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    public NewsManager getNewsManager() {
        return newsManager;
    }

    public GeminiNewsClient getGeminiNewsClient() {
        return geminiNewsClient;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }
}
