package com.tntbbp.myminecraft;

import com.tntbbp.myminecraft.command.EcCommand;
import com.tntbbp.myminecraft.command.HomeCommand;
import com.tntbbp.myminecraft.command.SpecialItemSummonCommand;
import com.tntbbp.myminecraft.command.LobbyCommand;
import com.tntbbp.myminecraft.command.MenuCommand;
import com.tntbbp.myminecraft.command.RtCommand;
import com.tntbbp.myminecraft.command.SpawnCommand;
import com.tntbbp.myminecraft.command.TpaCommand;
import com.tntbbp.myminecraft.listener.GUIListener;
import com.tntbbp.myminecraft.listener.LaevateinnListener;
import com.tntbbp.myminecraft.manager.CurrencyManager;
import com.tntbbp.myminecraft.manager.EconomyManager;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.HomeManager;
import com.tntbbp.myminecraft.manager.InfernalBurnManager;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import com.tntbbp.myminecraft.manager.LocationsManager;
import com.tntbbp.myminecraft.manager.RandomTeleportManager;
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
    private InfernalBurnManager infernalBurnManager;
    private LaevateinnManager laevateinnManager;
    private CurrencyManager currencyManager;

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
        this.infernalBurnManager = new InfernalBurnManager(this);
        this.laevateinnManager = new LaevateinnManager(this);
        this.currencyManager = new CurrencyManager(this);

        stockManager.startFluctuationTask();
        infernalBurnManager.start();

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
        getCommand("특수아이템소환").setExecutor(new SpecialItemSummonCommand(this));

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new LaevateinnListener(this), this);

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

    public InfernalBurnManager getInfernalBurnManager() {
        return infernalBurnManager;
    }

    public LaevateinnManager getLaevateinnManager() {
        return laevateinnManager;
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }
}
