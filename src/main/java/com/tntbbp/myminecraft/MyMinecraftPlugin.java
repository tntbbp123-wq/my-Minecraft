package com.tntbbp.myminecraft;

import com.tntbbp.myminecraft.command.admin.AdminMenuCommand;
import com.tntbbp.myminecraft.command.admin.SecretEncryptCommand;
import com.tntbbp.myminecraft.command.admin.SpecialItemSummonCommand;
import com.tntbbp.myminecraft.command.combat.BountyCommand;
import com.tntbbp.myminecraft.command.economy.BankCommand;
import com.tntbbp.myminecraft.command.economy.NewsCommand;
import com.tntbbp.myminecraft.command.economy.StockAddCommand;
import com.tntbbp.myminecraft.command.economy.StockGiveCommand;
import com.tntbbp.myminecraft.command.item.TranscendAltarPlaceCommand;
import com.tntbbp.myminecraft.command.mail.MailCommand;
import com.tntbbp.myminecraft.command.menu.EcCommand;
import com.tntbbp.myminecraft.command.menu.MenuCommand;
import com.tntbbp.myminecraft.command.raid.RaidBossCommand;
import com.tntbbp.myminecraft.command.social.DiscordLinkCommand;
import com.tntbbp.myminecraft.command.social.HomeCommand;
import com.tntbbp.myminecraft.command.social.TeamCommand;
import com.tntbbp.myminecraft.command.social.TpaCommand;
import com.tntbbp.myminecraft.command.world.LobbyCommand;
import com.tntbbp.myminecraft.command.world.RtCommand;
import com.tntbbp.myminecraft.command.world.SpawnCommand;
import com.tntbbp.myminecraft.listener.ActivityListener;
import com.tntbbp.myminecraft.listener.GUIListener;
import com.tntbbp.myminecraft.listener.combat.CombatListener;
import com.tntbbp.myminecraft.listener.combat.CombatStanceKeyListener;
import com.tntbbp.myminecraft.listener.combat.CombatStanceListener;
import com.tntbbp.myminecraft.listener.combat.BountyListener;
import com.tntbbp.myminecraft.listener.combat.CurseListener;
import com.tntbbp.myminecraft.listener.economy.BlackMarketListener;
import com.tntbbp.myminecraft.listener.economy.ProtocolSilenceListener;
import com.tntbbp.myminecraft.listener.item.StarforceListener;
import com.tntbbp.myminecraft.listener.item.TranscendAltarBlockListener;
import com.tntbbp.myminecraft.gui.economy.StockGUIListener;
import com.tntbbp.myminecraft.listener.mail.MailGUIListener;
import com.tntbbp.myminecraft.listener.mail.MailJoinListener;
import com.tntbbp.myminecraft.listener.raid.RaidBossListener;
import com.tntbbp.myminecraft.listener.social.DiscordIntrusionListener;
import com.tntbbp.myminecraft.listener.weapon.BalmungListener;
import com.tntbbp.myminecraft.listener.weapon.DrakenPierceListener;
import com.tntbbp.myminecraft.listener.weapon.GleipnirKeyListener;
import com.tntbbp.myminecraft.listener.weapon.GleipnirListener;
import com.tntbbp.myminecraft.listener.weapon.JahaShingeomListener;
import com.tntbbp.myminecraft.listener.weapon.LaevateinnListener;
import com.tntbbp.myminecraft.listener.weapon.LaevateinnModelListener;
import com.tntbbp.myminecraft.listener.weapon.MalyongdoListener;
import com.tntbbp.myminecraft.listener.weapon.SainchamsagumListener;
import com.tntbbp.myminecraft.listener.world.CoreBlockListener;
import com.tntbbp.myminecraft.log.AdminLog;
import com.tntbbp.myminecraft.log.EventLog;
import com.tntbbp.myminecraft.log.TradeLogger;
import com.tntbbp.myminecraft.manager.combat.CombatManager;
import com.tntbbp.myminecraft.manager.combat.CombatMusicManager;
import com.tntbbp.myminecraft.manager.combat.CombatStanceManager;
import com.tntbbp.myminecraft.manager.combat.BountyManager;
import com.tntbbp.myminecraft.manager.combat.CurseManager;
import com.tntbbp.myminecraft.manager.combat.InfernalBurnManager;
import com.tntbbp.myminecraft.manager.combat.ShockManager;
import com.tntbbp.myminecraft.manager.economy.BlackMarketManager;
import com.tntbbp.myminecraft.manager.economy.CurrencyManager;
import com.tntbbp.myminecraft.manager.economy.EconomyManager;
import com.tntbbp.myminecraft.manager.economy.GeminiNewsClient;
import com.tntbbp.myminecraft.manager.economy.NewsManager;
import com.tntbbp.myminecraft.manager.economy.StockManager;
import com.tntbbp.myminecraft.manager.item.EnhanceManager;
import com.tntbbp.myminecraft.manager.item.GradeManager;
import com.tntbbp.myminecraft.manager.item.StarforceManager;
import com.tntbbp.myminecraft.manager.item.TranscendAltarBlockManager;
import com.tntbbp.myminecraft.manager.mail.MailManager;
import com.tntbbp.myminecraft.manager.raid.RaidBossManager;
import com.tntbbp.myminecraft.manager.social.DiscordLinkManager;
import com.tntbbp.myminecraft.manager.social.DiscordManager;
import com.tntbbp.myminecraft.manager.social.HomeManager;
import com.tntbbp.myminecraft.manager.social.TeamManager;
import com.tntbbp.myminecraft.manager.social.TeleportRequestManager;
import com.tntbbp.myminecraft.manager.weapon.BalmungManager;
import com.tntbbp.myminecraft.manager.weapon.DrakenPierceManager;
import com.tntbbp.myminecraft.manager.weapon.GleipnirManager;
import com.tntbbp.myminecraft.manager.weapon.JahaShingeomManager;
import com.tntbbp.myminecraft.manager.weapon.LaevateinnManager;
import com.tntbbp.myminecraft.manager.weapon.MalyongdoManager;
import com.tntbbp.myminecraft.manager.weapon.SainchamsagumManager;
import com.tntbbp.myminecraft.manager.world.CoreManager;
import com.tntbbp.myminecraft.manager.world.LocationsManager;
import com.tntbbp.myminecraft.manager.world.RandomTeleportManager;
import com.tntbbp.myminecraft.util.SecretResolver;
import com.tntbbp.myminecraft.web.WebBridge;
import com.tntbbp.myminecraft.web.profile.PlayerDataRegistry;
import com.tntbbp.myminecraft.web.profile.providers.BasicProvider;
import com.tntbbp.myminecraft.web.profile.providers.DiscordProvider;
import com.tntbbp.myminecraft.web.profile.providers.EconomyProvider;
import com.tntbbp.myminecraft.web.profile.providers.HomeProvider;
import com.tntbbp.myminecraft.web.profile.providers.StockProvider;
import com.tntbbp.myminecraft.web.profile.providers.TeamProvider;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
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
    private CurseManager curseManager;
    private SainchamsagumManager sainchamsagumManager;
    private MalyongdoManager malyongdoManager;
    private BountyManager bountyManager;
    private BalmungManager balmungManager;
    private JahaShingeomManager jahaShingeomManager;
    private CurrencyManager currencyManager;
    private NewsManager newsManager;
    private GeminiNewsClient geminiNewsClient;
    private CombatManager combatManager;
    private CombatMusicManager combatMusicManager;
    private CombatStanceManager combatStanceManager;
    private DiscordManager discordManager;
    private DiscordLinkManager discordLinkManager;
    private TeamManager teamManager;
    private CoreManager coreManager;
    private RaidBossManager raidBossManager;
    private SecretResolver secretResolver;
    private GleipnirManager gleipnirManager;
    private MailManager mailManager;
    private EventLog eventLog;
    private TradeLogger tradeLogger;
    private AdminLog adminLog;
    private PlayerDataRegistry playerDataRegistry;
    private WebBridge webBridge;

    /** PacketEvents는 서버가 켜지기 전 단계에서 먼저 준비해야 패킷을 놓치지 않는다. */
    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

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
        this.curseManager = new CurseManager(this);
        this.sainchamsagumManager = new SainchamsagumManager(this);
        this.malyongdoManager = new MalyongdoManager(this);
        this.bountyManager = new BountyManager(this);
        this.balmungManager = new BalmungManager(this);
        this.jahaShingeomManager = new JahaShingeomManager(this);
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
        this.mailManager = new MailManager(this);

        // 웹 관리자(gn-admin)용 기록기: 매니저가 만들어진 뒤, 태스크(주식 변동 등)가 돌기 전에 준비한다.
        this.eventLog = new EventLog(this);
        this.tradeLogger = new TradeLogger(eventLog);
        this.adminLog = new AdminLog(eventLog);
        this.playerDataRegistry = new PlayerDataRegistry(getLogger());
        playerDataRegistry.register(new BasicProvider());
        playerDataRegistry.register(new EconomyProvider(this));
        playerDataRegistry.register(new StockProvider(this));
        playerDataRegistry.register(new HomeProvider(this));
        playerDataRegistry.register(new TeamProvider(this));
        playerDataRegistry.register(new DiscordProvider(this));
        if (getConfig().getBoolean("activity-log.enabled", true)) {
            getServer().getPluginManager().registerEvents(new ActivityListener(this, eventLog), this);
        }

        stockManager.startFluctuationTask();
        infernalBurnManager.start();
        shockManager.start();
        curseManager.start();
        sainchamsagumManager.start();
        malyongdoManager.start();
        newsManager.startTask();
        blackMarketManager.start();
        discordManager.start();
        raidBossManager.start();
        combatMusicManager.start();
        mailManager.start();

        TpaCommand tpaCommand = new TpaCommand(this);
        BountyCommand bountyCommand = new BountyCommand(this);
        getCommand("현상금").setExecutor(bountyCommand);
        getCommand("현상금").setTabCompleter(bountyCommand);
        getCommand("텔레포트요청").setExecutor(tpaCommand);
        getCommand("텔레포트수락").setExecutor(tpaCommand);
        getCommand("텔레포트거절").setExecutor(tpaCommand);

        HomeCommand homeCommand = new HomeCommand(this);
        getCommand("홈").setExecutor(homeCommand);
        getCommand("홈설정").setExecutor(homeCommand);
        getCommand("홈삭제").setExecutor(homeCommand);

        getCommand("엔더상자").setExecutor(new EcCommand());
        getCommand("은행").setExecutor(new BankCommand(this));
        getCommand("우편함").setExecutor(new MailCommand(this));
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
        getServer().getPluginManager().registerEvents(new CurseListener(this), this);
        getServer().getPluginManager().registerEvents(new SainchamsagumListener(this), this);
        getServer().getPluginManager().registerEvents(new MalyongdoListener(this), this);
        getServer().getPluginManager().registerEvents(new BountyListener(this), this);
        getServer().getPluginManager().registerEvents(new BalmungListener(this), this);
        getServer().getPluginManager().registerEvents(new JahaShingeomListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatStanceListener(this), this);
        getServer().getPluginManager().registerEvents(new StarforceListener(this), this);
        getServer().getPluginManager().registerEvents(new TranscendAltarBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new BlackMarketListener(this), this);
        getServer().getPluginManager().registerEvents(new RaidBossListener(this), this);
        getServer().getPluginManager().registerEvents(new GleipnirListener(this), this);
        getServer().getPluginManager().registerEvents(new MailGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new MailJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new StockGUIListener(this), this);

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

        // PacketEvents는 jar에 포함되어 있어 별도 설치 없이 항상 쓸 수 있다.
        PacketEvents.getAPI().init();
        PacketEvents.getAPI().getEventManager().registerListener(new ProtocolSilenceListener(this));
        PacketEvents.getAPI().getEventManager().registerListener(new CombatStanceKeyListener(this));
        PacketEvents.getAPI().getEventManager().registerListener(new GleipnirKeyListener(this));
        getLogger().info("패킷 기능 활성화: 소음 차단 포션 은폐 / 웅크리기+L 전투모드 / 글레이프니르 절대봉인(L키)");

        getLogger().info("MyMinecraft 플러그인이 활성화되었습니다.");

        // 웹 관리 통로는 맨 마지막에 연다. 열지 못해도(토큰 없음·포트 충돌 등) 다른 기능에는 영향이 없다.
        startWebBridge();
    }

    private void startWebBridge() {
        if (!getConfig().getBoolean("web-bridge.enabled", true)) {
            getLogger().info("웹 관리 통로가 설정(web-bridge.enabled: false)으로 꺼져 있습니다.");
            return;
        }
        try {
            this.webBridge = new WebBridge(this, eventLog, playerDataRegistry);
            webBridge.start();
        } catch (Exception | LinkageError e) {
            getLogger().severe("웹 관리 통로를 시작하지 못했습니다: " + e + " (다른 기능은 정상 동작합니다)");
        }
    }

    @Override
    public void onDisable() {
        // 웹 관리 통로를 가장 먼저 닫는다(이후 들어오는 요청은 503 server_stopping).
        if (webBridge != null) {
            webBridge.stop();
        }
        if (stockManager != null) {
            stockManager.stopFluctuationTask();
        }
        if (infernalBurnManager != null) {
            infernalBurnManager.stop();
        }
        if (shockManager != null) {
            shockManager.stop();
        }
        if (curseManager != null) {
            curseManager.stop();
        }
        if (sainchamsagumManager != null) {
            sainchamsagumManager.stop();
        }
        if (malyongdoManager != null) {
            malyongdoManager.stop();
        }
        if (newsManager != null) {
            newsManager.stopTask();
        }
        if (blackMarketManager != null) {
            blackMarketManager.stop();
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
        if (PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized()) {
            PacketEvents.getAPI().terminate();
        }
        // 지연 발송 대기 우편을 보내고, 아직 디스크에 쓰지 못한 우편 파일을 모두 저장한다.
        if (mailManager != null) {
            mailManager.shutdown();
        }
        getLogger().info("MyMinecraft 플러그인이 비활성화되었습니다.");
        // 활동 기록은 맨 마지막에 남은 것을 모두 파일에 쓰고 닫는다.
        if (eventLog != null) {
            eventLog.flush();
        }
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

    public CurseManager getCurseManager() {
        return curseManager;
    }

    public SainchamsagumManager getSainchamsagumManager() {
        return sainchamsagumManager;
    }

    public MalyongdoManager getMalyongdoManager() {
        return malyongdoManager;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public BalmungManager getBalmungManager() {
        return balmungManager;
    }

    public JahaShingeomManager getJahaShingeomManager() {
        return jahaShingeomManager;
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

    /** 우편 시스템. {@code send}/{@code enqueue}는 퀘스트 보상 등에서 쓰는 공개 API. */
    public MailManager getMailManager() {
        return mailManager;
    }

    public EventLog getEventLog() {
        return eventLog;
    }

    public TradeLogger getTradeLogger() {
        return tradeLogger;
    }

    public AdminLog getAdminLog() {
        return adminLog;
    }

    /** 웹 관리 통로. 꺼져 있거나 시작하지 못했으면 null일 수 있다. */
    public WebBridge getWebBridge() {
        return webBridge;
    }

    public PlayerDataRegistry getPlayerDataRegistry() {
        return playerDataRegistry;
    }
}
