package com.tntbbp.myminecraft;

import com.tntbbp.myminecraft.command.AdminMenuCommand;
import com.tntbbp.myminecraft.command.BankCommand;
import com.tntbbp.myminecraft.command.EcCommand;
import com.tntbbp.myminecraft.command.HomeCommand;
import com.tntbbp.myminecraft.command.NewsCommand;
import com.tntbbp.myminecraft.command.SpecialItemSummonCommand;
import com.tntbbp.myminecraft.command.StockAddCommand;
import com.tntbbp.myminecraft.command.StockGiveCommand;
import com.tntbbp.myminecraft.command.LobbyCommand;
import com.tntbbp.myminecraft.command.MenuCommand;
import com.tntbbp.myminecraft.command.RaidBossCommand;
import com.tntbbp.myminecraft.command.RtCommand;
import com.tntbbp.myminecraft.command.SecretEncryptCommand;
import com.tntbbp.myminecraft.command.SpawnCommand;
import com.tntbbp.myminecraft.command.TeamCommand;
import com.tntbbp.myminecraft.command.TpaCommand;
import com.tntbbp.myminecraft.command.DiscordLinkCommand;
import com.tntbbp.myminecraft.command.TranscendAltarPlaceCommand;
import com.tntbbp.myminecraft.listener.BlackMarketListener;
import com.tntbbp.myminecraft.listener.CombatListener;
import com.tntbbp.myminecraft.listener.CombatStanceKeyListener;
import com.tntbbp.myminecraft.listener.CombatStanceListener;
import com.tntbbp.myminecraft.listener.CoreBlockListener;
import com.tntbbp.myminecraft.listener.DiscordIntrusionListener;
import com.tntbbp.myminecraft.listener.DrakenPierceListener;
import com.tntbbp.myminecraft.listener.GleipnirKeyListener;
import com.tntbbp.myminecraft.listener.GleipnirListener;
import com.tntbbp.myminecraft.listener.GUIListener;
import com.tntbbp.myminecraft.listener.LaevateinnListener;
import com.tntbbp.myminecraft.listener.LaevateinnModelListener;
import com.tntbbp.myminecraft.listener.ProtocolSilenceListener;
import com.tntbbp.myminecraft.listener.RaidBossListener;
import com.tntbbp.myminecraft.listener.StarforceListener;
import com.tntbbp.myminecraft.listener.TranscendAltarBlockListener;
import com.tntbbp.myminecraft.manager.BlackMarketManager;
import com.tntbbp.myminecraft.manager.CombatManager;
import com.tntbbp.myminecraft.manager.CombatMusicManager;
import com.tntbbp.myminecraft.manager.CombatStanceManager;
import com.tntbbp.myminecraft.manager.CoreManager;
import com.tntbbp.myminecraft.manager.CurrencyManager;
import com.tntbbp.myminecraft.manager.DiscordLinkManager;
import com.tntbbp.myminecraft.manager.DiscordManager;
import com.tntbbp.myminecraft.manager.DrakenPierceManager;
import com.tntbbp.myminecraft.manager.EconomyManager;
import com.tntbbp.myminecraft.manager.EnhanceManager;
import com.tntbbp.myminecraft.manager.GeminiNewsClient;
import com.tntbbp.myminecraft.manager.GleipnirManager;
import com.tntbbp.myminecraft.manager.GradeManager;
import com.tntbbp.myminecraft.manager.HomeManager;
import com.tntbbp.myminecraft.manager.InfernalBurnManager;
import com.tntbbp.myminecraft.manager.LaevateinnManager;
import com.tntbbp.myminecraft.manager.LocationsManager;
import com.tntbbp.myminecraft.manager.NewsManager;
import com.tntbbp.myminecraft.manager.RaidBossManager;
import com.tntbbp.myminecraft.manager.RandomTeleportManager;
import com.tntbbp.myminecraft.manager.ShockManager;
import com.tntbbp.myminecraft.manager.StarforceManager;
import com.tntbbp.myminecraft.manager.StockManager;
import com.tntbbp.myminecraft.manager.TeamManager;
import com.tntbbp.myminecraft.manager.TeleportRequestManager;
import com.tntbbp.myminecraft.manager.TranscendAltarBlockManager;
import com.tntbbp.myminecraft.util.SecretResolver;
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
    private TranscendAltarBlockManager transcendAltarBlockManager;
    private BlackMarketManager blackMarketManager;
    private InfernalBurnManager infernalBurnManager;
    private LaevateinnManager laevateinnManager;
    private ShockManager shockManager;
    private DrakenPierceManager drakenPierceManager;
    private CurrencyManager currencyManager;
    private NewsManager newsManager;
    private GeminiNewsClient geminiNewsClient;
    private CombatManager combatManager;
    private CombatMusicManager combatMusicManager;
    private CombatStanceManager combatStanceManager;
    private ProtocolSilenceListener protocolSilenceListener;
    private CombatStanceKeyListener combatStanceKeyListener;
    private DiscordManager discordManager;
    private DiscordLinkManager discordLinkManager;
    private TeamManager teamManager;
    private CoreManager coreManager;
    private RaidBossManager raidBossManager;
    private SecretResolver secretResolver;
    private GleipnirManager gleipnirManager;
    private GleipnirKeyListener gleipnirKeyListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.secretResolver = new SecretResolver(this);
        this.economyManager = new EconomyManager(this);
        this.homeManager = new HomeManager(this);
        this.locationsManager = new LocationsManager(this);
        this.teleportRequestManager = new TeleportRequestManager(this);
        this.stockManager = new StockManager(this, economyManager);
        this.randomTeleportManager = new RandomTeleportManager(this);
        this.enhanceManager = new EnhanceManager(this);
        this.gradeManager = new GradeManager(this);
        this.starforceManager = new StarforceManager(this);
        this.transcendAltarBlockManager = new TranscendAltarBlockManager(this);
        this.blackMarketManager = new BlackMarketManager(this);
        this.infernalBurnManager = new InfernalBurnManager(this);
        this.laevateinnManager = new LaevateinnManager(this);
        this.shockManager = new ShockManager(this);
        this.drakenPierceManager = new DrakenPierceManager(this);
        this.currencyManager = new CurrencyManager(this);
        this.newsManager = new NewsManager(this, stockManager);
        this.geminiNewsClient = new GeminiNewsClient(this);
        this.combatManager = new CombatManager(this);
        this.combatMusicManager = new CombatMusicManager(this, combatManager);
        this.combatStanceManager = new CombatStanceManager(this);
        this.discordManager = new DiscordManager(this);
        this.discordLinkManager = new DiscordLinkManager(this);
        this.teamManager = new TeamManager(this);
        this.coreManager = new CoreManager(this);
        this.gleipnirManager = new GleipnirManager(this);
        this.raidBossManager = new RaidBossManager(this);

        stockManager.startFluctuationTask();
        infernalBurnManager.start();
        shockManager.start();
        newsManager.startTask();
        blackMarketManager.start();
        discordManager.start();
        raidBossManager.start();
        combatMusicManager.start();

        TpaCommand tpaCommand = new TpaCommand(this);
        getCommand("텔레포트요청").setExecutor(tpaCommand);
        getCommand("텔레포트수락").setExecutor(tpaCommand);
        getCommand("텔레포트거절").setExecutor(tpaCommand);

        HomeCommand homeCommand = new HomeCommand(this);
        getCommand("홈").setExecutor(homeCommand);
        getCommand("홈설정").setExecutor(homeCommand);
        getCommand("홈삭제").setExecutor(homeCommand);

        getCommand("엔더상자").setExecutor(new EcCommand());
        getCommand("은행").setExecutor(new BankCommand(this));
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
        getServer().getPluginManager().registerEvents(new DrakenPierceListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatStanceListener(this), this);
        getServer().getPluginManager().registerEvents(new StarforceListener(this), this);
        getServer().getPluginManager().registerEvents(new TranscendAltarBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new BlackMarketListener(this), this);
        getServer().getPluginManager().registerEvents(new RaidBossListener(this), this);
        getServer().getPluginManager().registerEvents(new GleipnirListener(this), this);

        RaidBossCommand raidBossCommand = new RaidBossCommand(this);
        getCommand("레이드보스").setExecutor(raidBossCommand);
        getCommand("레이드보스").setTabCompleter(raidBossCommand);

        getCommand("초월제단설치").setExecutor(new TranscendAltarPlaceCommand(this));

        TeamCommand teamCommand = new TeamCommand(this);
        getCommand("팀생성").setExecutor(teamCommand);
        getCommand("팀생성").setTabCompleter(teamCommand);
        getCommand("팀원추가").setExecutor(teamCommand);
        getCommand("팀원추가").setTabCompleter(teamCommand);
        getCommand("팀원삭제").setExecutor(teamCommand);
        getCommand("팀원삭제").setTabCompleter(teamCommand);
        getCommand("팀삭제").setExecutor(teamCommand);
        getCommand("팀삭제").setTabCompleter(teamCommand);
        getCommand("팀정보").setExecutor(teamCommand);
        getCommand("팀정보").setTabCompleter(teamCommand);
        getServer().getPluginManager().registerEvents(new CoreBlockListener(this), this);
        getServer().addRecipe(coreManager.recipe());

        DiscordLinkCommand discordLinkCommand = new DiscordLinkCommand(this);
        getCommand("디스코드연동").setExecutor(discordLinkCommand);
        getCommand("디스코드연동확인").setExecutor(discordLinkCommand);
        getCommand("토큰암호화").setExecutor(new SecretEncryptCommand(this));
        getServer().getPluginManager().registerEvents(new DiscordIntrusionListener(this), this);

        if (getServer().getPluginManager().isPluginEnabled("BetterModel")) {
            getServer().getPluginManager().registerEvents(new LaevateinnModelListener(this), this);
            getLogger().info("BetterModel 연동: 레바테인 3D 모델 표시 기능이 활성화되었습니다.");
        } else {
            getLogger().info("BetterModel이 설치되어 있지 않아 레바테인 3D 모델 표시 기능은 비활성화됩니다.");
        }

        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            this.protocolSilenceListener = new ProtocolSilenceListener(this);
            protocolSilenceListener.register();
            getLogger().info("ProtocolLib 연동: 소음 차단 포션의 소리/애니메이션 은폐 기능이 활성화되었습니다.");

            this.combatStanceKeyListener = new CombatStanceKeyListener(this);
            combatStanceKeyListener.register();
            getLogger().info("ProtocolLib 연동: 웅크리기+L(도전과제 화면 열기)로 전투모드 진입/해제 기능이 활성화되었습니다.");

            this.gleipnirKeyListener = new GleipnirKeyListener(this);
            gleipnirKeyListener.register();
            getLogger().info("ProtocolLib 연동: 글레이프니르의 L키(절대봉인) 기능이 활성화되었습니다.");
        } else {
            getLogger().info("ProtocolLib이 설치되어 있지 않아 소음 차단 포션은 상태 효과만 적용되고 "
                    + "소리/애니메이션 은폐는 동작하지 않습니다.");
            getLogger().info("ProtocolLib이 설치되어 있지 않아 웅크리기+L 전투모드 진입 기능은 동작하지 않습니다.");
            getLogger().info("ProtocolLib이 설치되어 있지 않아 글레이프니르의 절대봉인(L키)은 동작하지 않습니다. "
                    + "봉인(F)과 속박(Q)은 정상 동작합니다.");
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
        if (shockManager != null) {
            shockManager.stop();
        }
        if (newsManager != null) {
            newsManager.stopTask();
        }
        if (blackMarketManager != null) {
            blackMarketManager.stop();
        }
        if (protocolSilenceListener != null) {
            protocolSilenceListener.unregister();
        }
        if (combatStanceKeyListener != null) {
            combatStanceKeyListener.unregister();
        }
        if (gleipnirKeyListener != null) {
            gleipnirKeyListener.unregister();
        }
        if (combatStanceManager != null) {
            combatStanceManager.restoreAll();
        }
        if (discordManager != null) {
            discordManager.stop();
        }
        if (raidBossManager != null) {
            raidBossManager.stop();
        }
        if (combatMusicManager != null) {
            combatMusicManager.stop();
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

    public TranscendAltarBlockManager getTranscendAltarBlockManager() {
        return transcendAltarBlockManager;
    }

    public BlackMarketManager getBlackMarketManager() {
        return blackMarketManager;
    }

    public InfernalBurnManager getInfernalBurnManager() {
        return infernalBurnManager;
    }

    public LaevateinnManager getLaevateinnManager() {
        return laevateinnManager;
    }

    public ShockManager getShockManager() {
        return shockManager;
    }

    public DrakenPierceManager getDrakenPierceManager() {
        return drakenPierceManager;
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

    public CombatMusicManager getCombatMusicManager() {
        return combatMusicManager;
    }

    public CombatStanceManager getCombatStanceManager() {
        return combatStanceManager;
    }

    public DiscordManager getDiscordManager() {
        return discordManager;
    }

    public DiscordLinkManager getDiscordLinkManager() {
        return discordLinkManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public CoreManager getCoreManager() {
        return coreManager;
    }

    public RaidBossManager getRaidBossManager() {
        return raidBossManager;
    }

    public SecretResolver getSecretResolver() {
        return secretResolver;
    }

    public GleipnirManager getGleipnirManager() {
        return gleipnirManager;
    }
}
