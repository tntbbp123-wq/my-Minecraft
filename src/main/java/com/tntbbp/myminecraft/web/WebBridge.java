package com.tntbbp.myminecraft.web;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.log.AdminLog;
import com.tntbbp.myminecraft.log.EventLog;
import com.tntbbp.myminecraft.log.TradeLogger;
import com.tntbbp.myminecraft.web.handler.CatalogHandler;
import com.tntbbp.myminecraft.web.handler.CommandHandler;
import com.tntbbp.myminecraft.web.handler.HealthHandler;
import com.tntbbp.myminecraft.web.handler.MailHandler;
import com.tntbbp.myminecraft.web.handler.NewsHandler;
import com.tntbbp.myminecraft.web.handler.PlayerHandler;
import com.tntbbp.myminecraft.web.handler.PluginHandler;
import com.tntbbp.myminecraft.web.handler.ServerHandler;
import com.tntbbp.myminecraft.web.handler.SettingsCatalogHandler;
import com.tntbbp.myminecraft.web.handler.StockHandler;
import com.tntbbp.myminecraft.web.profile.PlayerDataRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * 웹 관리자(gn-admin) 백엔드와 게임 서버를 잇는 HTTP 연결 통로. 계약은 gn-admin {@code docs/api-bridge.md}.
 *
 * <ul>
 *   <li>127.0.0.1에만 열고({@code web-bridge.port}, 기본 25590), 모든 경로는 {@code /v1} 아래.</li>
 *   <li>{@code Authorization: Bearer <token>} 필수(토큰은 {@code web-bridge.token}, 기본 env:GN_BRIDGE_TOKEN).
 *       토큰을 풀 수 없으면 통로를 시작하지 않는다(플러그인의 다른 기능은 그대로 동작).</li>
 *   <li>쓰기 요청(POST/PATCH/DELETE)은 {@code X-Request-Id} 필수이고, 같은 ID는 저장된 응답을 재생한다(멱등).</li>
 *   <li>HTTP 스레드는 {@code web-bridge.threads}개. Bukkit 상태는 {@link #callSync}로 메인 스레드에서 만진다.</li>
 * </ul>
 *
 * <p><b>라우트는 {@link #registerHandlers()} 한 곳에서 전부 등록한다</b>(P2 우편·P3 주식/뉴스 스텁 포함).
 * P2/P3는 이 파일을 고치지 않고 각 핸들러 클래스의 본문만 채운다.
 */
public class WebBridge {

    private static final String API_PREFIX = "/v1";
    private static final int IDEMPOTENCY_MAX_ENTRIES = 2000;

    private final MyMinecraftPlugin plugin;
    private final EventLog eventLog;
    private final PlayerDataRegistry playerDataRegistry;
    private final MainThread mainThread;
    private final CommandRunner commandRunner = new CommandRunner();
    private final RouteTable<RouteHandler> routes = new RouteTable<>();
    private final long startedAtMillis = System.currentTimeMillis();

    private IdempotencyCache idempotency;
    private BridgeAuth auth;
    private HttpServer server;
    private ExecutorService executor;
    private ServerHandler serverHandler;
    private int maxBodyBytes;
    private long syncTimeoutMs;
    private long saveTimeoutMs;
    private volatile boolean ready;
    private volatile boolean running;

    public WebBridge(MyMinecraftPlugin plugin, EventLog eventLog, PlayerDataRegistry playerDataRegistry) {
        this.plugin = plugin;
        this.eventLog = eventLog;
        this.playerDataRegistry = playerDataRegistry;
        this.mainThread = new MainThread(plugin);
    }

    /**
     * 통로를 연다. 설정 오류·토큰 없음·포트 충돌 등으로 열지 못하면 경고만 남기고 false를 돌려준다
     * (예외를 밖으로 던지지 않으므로 플러그인의 다른 기능에는 영향이 없다). 메인 스레드(onEnable)에서 호출.
     */
    public boolean start() {
        if (running) {
            return true;
        }
        FileConfiguration config = plugin.getConfig();
        String token = plugin.getSecretResolver().resolve(config.getString("web-bridge.token", "env:GN_BRIDGE_TOKEN"));
        if (token == null || token.isBlank()) {
            plugin.getLogger().warning("웹 관리 통로 토큰(web-bridge.token)이 설정되지 않아 통로를 시작하지 않습니다. "
                    + "환경변수 GN_BRIDGE_TOKEN 을 설정한 뒤 서버를 재시작하세요. (다른 기능은 정상 동작합니다)");
            return false;
        }
        int port = config.getInt("web-bridge.port", 25590);
        int threads = Math.max(1, config.getInt("web-bridge.threads", 2));
        this.maxBodyBytes = Math.max(1024, config.getInt("web-bridge.max-body-bytes", 65536));
        this.syncTimeoutMs = Math.max(100L, config.getLong("web-bridge.sync-timeout-ms", 5000L));
        this.saveTimeoutMs = Math.max(syncTimeoutMs, config.getLong("web-bridge.save-timeout-ms", 30000L));
        long ttlSeconds = Math.max(1L, config.getLong("web-bridge.idempotency-ttl-seconds", 600L));
        this.idempotency = new IdempotencyCache(ttlSeconds * 1000L, IDEMPOTENCY_MAX_ENTRIES, System::currentTimeMillis);
        this.auth = new BridgeAuth(token);

        InetAddress bindAddress = InetAddress.getLoopbackAddress();
        String host = config.getString("web-bridge.host", "127.0.0.1");
        if (host != null && !host.isBlank() && !host.equals("127.0.0.1") && !host.equalsIgnoreCase("localhost")) {
            plugin.getLogger().warning("web-bridge.host(" + host + ")는 무시하고 보안을 위해 127.0.0.1 에만 엽니다.");
        }

        try {
            registerHandlers();
            // JDK HttpServer는 헤더/응답 읽기·쓰기 시간 제한이 기본 없어, 느린 연결 2개만으로도 스레드
            // 2개가 계속 묶일 수 있다. 이 값은 JDK가 HttpServer 클래스를 처음 쓸 때 읽으므로 반드시
            // HttpServer.create(...) 호출 전에 두어야 하고, JVM 옵션으로 이미 설정돼 있으면 덮어쓰지 않는다.
            // maxRspTime은 긴 명령 출력 응답을 끊지 않도록 넉넉히 60초로 둔다.
            if (System.getProperty("sun.net.httpserver.maxReqTime") == null) {
                System.setProperty("sun.net.httpserver.maxReqTime", "10");
            }
            if (System.getProperty("sun.net.httpserver.maxRspTime") == null) {
                System.setProperty("sun.net.httpserver.maxRspTime", "60");
            }
            server = HttpServer.create(new InetSocketAddress(bindAddress, port), 32);
            executor = Executors.newFixedThreadPool(threads, namedDaemonThreads());
            server.setExecutor(executor);
            server.createContext("/", this::dispatch);
            server.start();
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("웹 관리 통로를 열지 못했습니다 (127.0.0.1:" + port + "): " + e.getMessage()
                    + " — 포트가 이미 쓰이고 있는지 확인하세요. (다른 기능은 정상 동작합니다)");
            shutdownServer();
            return false;
        }
        mainThread.startHeartbeat();
        running = true;
        // 서버가 첫 틱을 돌기 시작해야 메인 스레드 작업을 받을 수 있다. 그 전 요청은 503 server_starting.
        Bukkit.getScheduler().runTask(plugin, () -> ready = true);
        plugin.getLogger().info("웹 관리 통로가 127.0.0.1:" + port + " 에서 열렸습니다 (스레드 " + threads + "개).");
        return true;
    }

    /** 통로를 닫는다. onDisable 첫 줄에서 호출(메인 스레드). 처리 중인 요청은 503 server_stopping으로 끝난다. */
    public void stop() {
        mainThread.markStopping();
        if (serverHandler != null) {
            serverHandler.shutdown();
        }
        boolean wasRunning = running;
        running = false;
        ready = false;
        shutdownServer();
        if (wasRunning) {
            plugin.getLogger().info("웹 관리 통로를 닫았습니다.");
        }
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 경로 → 핸들러 매핑. <b>P1·P2·P3의 모든 라우트를 여기서 등록한다</b>(P2/P3는 스텁 본문만 채움).
     * 쓰기 라우트(POST/PATCH/DELETE)는 자동으로 X-Request-Id 필수 + 멱등 캐시 대상이 된다.
     * 상태를 바꾸지 않는 POST만 {@code readOnly=true}로 등록한다.
     */
    private void registerHandlers() {
        if (!routes.routes().isEmpty()) {
            return;
        }
        HealthHandler health = new HealthHandler(this);
        ServerHandler server = new ServerHandler(this);
        this.serverHandler = server;
        PlayerHandler players = new PlayerHandler(this);
        CommandHandler commands = new CommandHandler(this);
        CatalogHandler catalog = new CatalogHandler(this);
        MailHandler mail = new MailHandler(this);
        StockHandler stocks = new StockHandler(this);
        NewsHandler news = new NewsHandler(this);
        PluginHandler plugins = new PluginHandler(this);
        SettingsCatalogHandler settingsCatalog = new SettingsCatalogHandler(this);

        // P1 — 상태·플레이어·명령·서버 제어·카탈로그
        routes.add("GET", "/health", false, health::health);
        routes.add("GET", "/server/status", false, server::status);
        routes.add("POST", "/server/broadcast", false, server::broadcast);
        routes.add("POST", "/server/countdown", false, server::startCountdown);
        routes.add("DELETE", "/server/countdown", false, server::cancelCountdown);
        routes.add("POST", "/server/save", false, server::save);
        routes.add("GET", "/players/online", false, players::online);
        routes.add("GET", "/players/offline", false, players::offline);
        routes.add("GET", "/players/resolve", false, players::resolve);
        routes.add("GET", "/players/{uuid}/profile", false, players::profile);
        routes.add("GET", "/commands", false, commands::list);
        routes.add("POST", "/commands/complete", true, commands::complete);
        routes.add("POST", "/commands/execute", false, commands::execute);
        routes.add("GET", "/commands/executions/{id}", false, commands::execution);
        routes.add("GET", "/catalog/{kind}", false, catalog::catalog);
        routes.add("GET", "/plugins/runtime", false, plugins::runtime);
        routes.add("GET", "/settings-catalog", false, settingsCatalog::catalog);

        // P2 — 우편 (MailHandler 스텁)
        routes.add("POST", "/mail", false, mail::send);
        routes.add("GET", "/players/{uuid}/mail", false, mail::list);
        routes.add("POST", "/players/{uuid}/mail/{id}/recall", false, mail::recall);

        // P3 — 주식 (StockHandler 스텁)
        routes.add("GET", "/stocks", false, stocks::list);
        routes.add("POST", "/stocks", false, stocks::create);
        routes.add("GET", "/stocks/{id}", false, stocks::get);
        routes.add("PATCH", "/stocks/{id}", false, stocks::update);
        routes.add("GET", "/stocks/{id}/holders", false, stocks::holders);
        routes.add("POST", "/stocks/{id}/price", false, stocks::setPrice);
        routes.add("POST", "/stocks/{id}/halt", false, stocks::halt);
        routes.add("POST", "/stocks/{id}/resume", false, stocks::resume);
        routes.add("POST", "/stocks/{id}/grant", false, stocks::grant);

        // P3 — 뉴스 (NewsHandler 스텁)
        routes.add("GET", "/news", false, news::list);
        routes.add("POST", "/news", false, news::create);
        routes.add("POST", "/news/ai-draft", false, news::aiDraft);
        routes.add("GET", "/news/ai-draft/{id}", false, news::aiDraftStatus);
        routes.add("PATCH", "/news/{id}", false, news::update);
        routes.add("POST", "/news/{id}/cancel", false, news::cancel);
    }

    // ---------------------------------------------------------------- 요청 처리

    private void dispatch(HttpExchange exchange) {
        try {
            handle(exchange);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "웹 관리 통로 요청 처리 중 오류", t);
            try {
                sendError(exchange, 500, "internal_error", "처리 중 오류가 발생했습니다.");
            } catch (IOException | RuntimeException ignored) {
                // 이미 응답을 보냈거나 연결이 끊김
            }
        } finally {
            exchange.close();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!auth.check(exchange)) {
            sendError(exchange, 401, "unauthorized", "인증이 필요합니다.");
            return;
        }
        String path = apiPath(exchange.getRequestURI());
        RouteTable.Match<RouteHandler> match = path == null ? null
                : routes.match(exchange.getRequestMethod(), path);
        if (match == null) {
            sendError(exchange, 404, "not_found", "없는 경로입니다.");
            return;
        }
        boolean isHealth = match.route().pattern().equals("/health");
        if (!isHealth) {
            if (!running || mainThread.isStopping()) {
                sendError(exchange, 503, "server_stopping", "서버가 종료 중입니다.");
                return;
            }
            if (!ready) {
                sendError(exchange, 503, "server_starting", "서버가 아직 준비 중입니다.");
                return;
            }
        }

        BridgeExchange bridgeExchange = new BridgeExchange(exchange, path, match.params(), maxBodyBytes);
        if (!match.route().write()) {
            BridgeResponse response = invoke(match.route().handler(), bridgeExchange);
            send(bridgeExchange, response, null);
            return;
        }

        String requestId;
        try {
            requestId = bridgeExchange.requestId();
        } catch (BridgeException e) {
            sendError(exchange, e.status(), e.code(), e.getMessage());
            return;
        }
        if (requestId == null) {
            sendError(exchange, 400, "missing_request_id", "쓰기 요청에는 X-Request-Id 헤더가 필요합니다.");
            return;
        }
        IdempotencyCache.Lookup lookup = idempotency.lookupOrBegin(requestId);
        switch (lookup.state()) {
            case CACHED -> {
                bridgeExchange.send(lookup.stored().status(), lookup.stored().body(),
                        Map.of(BridgeExchange.HEADER_REPLAY, "true"));
                return;
            }
            case IN_FLIGHT -> {
                sendError(exchange, 409, "duplicate_in_flight", "같은 X-Request-Id 요청이 아직 처리 중입니다.");
                return;
            }
            default -> {
                // STARTED: 아래에서 처리
            }
        }
        try {
            BridgeResponse response = invoke(match.route().handler(), bridgeExchange);
            String body = BridgeExchange.GSON.toJson(response.body());
            if (IdempotencyCache.isCacheable(response.status())) {
                idempotency.put(requestId, response.status(), body);
            }
            bridgeExchange.send(response.status(), body, null);
        } finally {
            idempotency.end(requestId);
        }
    }

    private BridgeResponse invoke(RouteHandler handler, BridgeExchange exchange) {
        try {
            BridgeResponse response = handler.handle(exchange);
            return response == null ? BridgeResponse.okTrue() : response;
        } catch (BridgeException e) {
            return BridgeResponse.error(e.status(), e.code(), e.getMessage());
        } catch (MainThread.MainThreadTimeoutException e) {
            return BridgeResponse.error(504, "main_thread_timeout", e.getMessage());
        } catch (MainThread.ServerStoppingException e) {
            return BridgeResponse.error(503, "server_stopping", e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "웹 관리 통로 " + exchange.method() + " " + exchange.path()
                    + " 처리 중 오류", e);
            return BridgeResponse.error(500, "internal_error", "처리 중 오류가 발생했습니다.");
        }
    }

    private void send(BridgeExchange exchange, BridgeResponse response, Map<String, String> headers)
            throws IOException {
        exchange.send(response.status(), BridgeExchange.GSON.toJson(response.body()), headers);
    }

    private void sendError(HttpExchange exchange, int status, String code, String message) throws IOException {
        BridgeExchange.sendRaw(exchange, status,
                BridgeExchange.GSON.toJson(BridgeResponse.errorBody(code, message)), null);
    }

    /** {@code /v1/...} 경로에서 {@code /v1}을 뗀 디코딩된 경로. /v1 밖이면 null. */
    static String apiPath(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return null;
        }
        if (path.equals(API_PREFIX)) {
            return "/";
        }
        if (!path.startsWith(API_PREFIX + "/")) {
            return null;
        }
        String rest = path.substring(API_PREFIX.length());
        while (rest.length() > 1 && rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return rest;
    }

    private void shutdownServer() {
        if (server != null) {
            try {
                server.stop(1);
            } catch (RuntimeException ignored) {
                // 이미 닫힘
            }
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static ThreadFactory namedDaemonThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "MyMinecraft-WebBridge-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    // ---------------------------------------------------------------- 핸들러용 접근자

    public MyMinecraftPlugin plugin() {
        return plugin;
    }

    public EventLog eventLog() {
        return eventLog;
    }

    public AdminLog adminLog() {
        return plugin.getAdminLog();
    }

    public TradeLogger tradeLogger() {
        return plugin.getTradeLogger();
    }

    public PlayerDataRegistry playerDataRegistry() {
        return playerDataRegistry;
    }

    public MainThread mainThread() {
        return mainThread;
    }

    public CommandRunner commandRunner() {
        return commandRunner;
    }

    /** 통로가 처리 중인 요청의 공통 메인 스레드 제한 시간({@code web-bridge.sync-timeout-ms}). */
    public long syncTimeoutMs() {
        return syncTimeoutMs;
    }

    /** {@code POST /server/save} 전용 제한 시간({@code web-bridge.save-timeout-ms}). */
    public long saveTimeoutMs() {
        return saveTimeoutMs;
    }

    /** 플러그인(통로) 시작 후 경과 시간. */
    public long uptimeMs() {
        return System.currentTimeMillis() - startedAtMillis;
    }

    /** 서버/통로가 종료 중인지. */
    public boolean shuttingDown() {
        return !running || mainThread.isStopping();
    }

    /** {@code callable}을 메인 스레드에서 실행({@link #syncTimeoutMs()} 제한). */
    public <T> T callSync(Callable<T> callable) throws Exception {
        return mainThread.callSync(callable, syncTimeoutMs);
    }

    /** {@code callable}을 메인 스레드에서 실행(제한 시간 지정). */
    public <T> T callSync(Callable<T> callable, long timeoutMs) throws Exception {
        return mainThread.callSync(callable, timeoutMs);
    }
}
