package com.tntbbp.myminecraft;

import com.tntbbp.myminecraft.command.EcCommand;
import com.tntbbp.myminecraft.command.EnhanceItemCommand;
import com.tntbbp.myminecraft.command.HomeCommand;
import com.tntbbp.myminecraft.command.LaevateinnCommand;
import com.tntbbp.myminecraft.command.LobbyCommand;
import com.tntbbp.myminecraft.command.MenuCommand;
import com.tntbbp.myminecraft.command.RtCommand;
import com.tntbbp.myminecraft.command.ServerSelectCommand;
import com.tntbbp.myminecraft.command.SpawnCommand;
import com.tntbbp.myminecraft.command.TpaCommand;
import com.tntbbp.myminecraft.listener.GUIListener;
import com.tntbbp.myminecraft.listener.LaevateinnListener;
import com.tntbbp.myminecraft.manager.EconomyManager;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.HomeManager;
import com.tntbbp.myminecraft.manager.InfernalBurnManager;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import com.tntbbp.myminecraft.manager.LocationsManager;
import com.tntbbp.myminecraft.manager.RandomTeleportManager;
import com.tntbbp.myminecraft.manager.StockManager;
import com.tntbbp.myminecraft.manager.TeleportRequestManager;
import com.tntbbp.myminecraft.manager.WorldSelectManager;
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
    private WorldSelectManager worldSelectManager;

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
        this.worldSelectManager = new WorldSelectManager(this);

        stockManager.startFluctuationTask();
        infernalBurnManager.start();

        TpaCommand tpaCommand = new TpaCommand(this);
        getCommand("tpa").setExecutor(tpaCommand);
        getCommand("tpaccept").setExecutor(tpaCommand);
        getCommand("tpdeny").setExecutor(tpaCommand);

        HomeCommand homeCommand = new HomeCommand(this);
        getCommand("home").setExecutor(homeCommand);
        getCommand("sethome").setExecutor(homeCommand);
        getCommand("delhome").setExecutor(homeCommand);

        getCommand("ec").setExecutor(new EcCommand());
        getCommand("menu").setExecutor(new MenuCommand(this));
        getCommand("lobby").setExecutor(new LobbyCommand(this));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("rt").setExecutor(new RtCommand(this));
        getCommand("enhanceitem").setExecutor(new EnhanceItemCommand(this));
        getCommand("laevateinn").setExecutor(new LaevateinnCommand(this));
        getCommand("serverselect").setExecutor(new ServerSelectCommand(this));

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

    public WorldSelectManager getWorldSelectManager() {
        return worldSelectManager;
    }
}
