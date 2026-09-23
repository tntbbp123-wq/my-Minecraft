package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.manager.economy.GeminiNewsClient;
import com.tntbbp.myminecraft.manager.economy.NewsManager;
import com.tntbbp.myminecraft.manager.economy.StockRules;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.web.BridgeException;
import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.WebBridge;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * 뉴스 관리 API (api-bridge.md §4.9~4.14). 조회·작성·공개 전 수정·반영 전 취소·AI 초안.
 * 뉴스 상태 변경과 관리 기록은 {@link NewsManager}가 한다(메인 스레드).
 *
 * <p>AI 초안은 뉴스가 아니다: {@link GeminiNewsClient}의 비동기 결과를 {@code draft_id}에 걸어 10분 보관만 하고
 * 발행(submit)하지 않는다. 확정은 웹이 {@code POST /news}로 따로 한다.
 */
public class NewsHandler {

    /** AI 초안 보관 시간. */
    static final long DRAFT_TTL_MILLIS = 10 * 60 * 1000L;
    /** 동시에 보관하는 초안 최대 개수(넘으면 가장 오래된 것부터 버림). */
    static final int MAX_DRAFTS = 200;
    private static final int MAX_TOPIC_LENGTH = 200;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final Pattern API_KEY_PATTERN = Pattern.compile("key=[^&\\s\"']+");

    /** AI 초안 한 건. 상태는 HTTP 스레드·AI 응답 스레드가 함께 보므로 volatile. */
    static final class Draft {
        final String id;
        final long createdAt;
        volatile String status = "pending";
        volatile String content;
        volatile String direction;
        volatile double magnitudePercent;
        volatile String error;

        Draft(String id, long createdAt) {
            this.id = id;
            this.createdAt = createdAt;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("draft_id", id);
            json.addProperty("status", status);
            if ("done".equals(status)) {
                JsonObject draft = new JsonObject();
                draft.addProperty("content", content);
                draft.addProperty("direction", direction);
                draft.addProperty("magnitude_percent", magnitudePercent);
                json.add("draft", draft);
            } else {
                json.add("draft", null);
            }
            json.addProperty("error", error);
            return json;
        }
    }

    private final WebBridge bridge;
    private final Map<String, Draft> drafts = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public NewsHandler(WebBridge bridge) {
        this(bridge, System::currentTimeMillis);
    }

    NewsHandler(WebBridge bridge, LongSupplier clock) {
        this.bridge = bridge;
        this.clock = clock;
    }

    /** {@code GET /news?status=} (기본 all). 최신순. */
    public BridgeResponse list(BridgeExchange exchange) throws Exception {
        String status = exchange.query("status");
        if (status == null || status.isBlank()) {
            status = "all";
        }
        if (!NewsManager.isStatusFilter(status)) {
            throw BridgeException.invalidField("status 는 pending|revealed|applied|cancelled|all 중 하나여야 합니다.");
        }
        String filter = status;
        JsonObject body = bridge.callSync(() -> {
            JsonArray news = new JsonArray();
            for (NewsManager.NewsView view : manager().all(filter)) {
                news.add(newsJson(view));
            }
            JsonObject result = new JsonObject();
            result.add("news", news);
            return result;
        });
        return BridgeResponse.ok(body);
    }

    /** {@code POST /news} (쓰기) — 뉴스 작성(확정). 작성자 = X-GN-Actor. */
    public BridgeResponse create(BridgeExchange exchange) throws Exception {
        JsonObject request = exchange.json();
        String stockId = BridgeExchange.optString(request, "stock_id");
        if (stockId == null || stockId.isBlank()) {
            throw BridgeException.invalidField("stock_id 가 필요합니다.");
        }
        int direction = parseDirection(BridgeExchange.optString(request, "direction"), true);
        double magnitude = parseMagnitude(BridgeExchange.optDouble(request, "magnitude_percent"), true);
        String content = validateContent(BridgeExchange.optString(request, "content"), true);
        Boolean fakeValue = BridgeExchange.optBoolean(request, "fake");
        boolean fake = fakeValue != null && fakeValue;
        String actor = exchange.actor();

        JsonObject body = bridge.callSync(() -> {
            Stock stock = requireStock(stockId);
            NewsManager.NewsItem item = manager().submit(stock, direction, magnitude, content, fake, actor);
            return okWithNews(item.view());
        });
        return BridgeResponse.ok(body);
    }

    /** {@code POST /news/ai-draft} (쓰기, 비동기) — AI 초안만 생성. 바로 {@code draft_id}를 돌려준다. */
    public BridgeResponse aiDraft(BridgeExchange exchange) throws Exception {
        JsonObject request = exchange.json();
        String stockId = BridgeExchange.optString(request, "stock_id");
        if (stockId == null || stockId.isBlank()) {
            throw BridgeException.invalidField("stock_id 가 필요합니다.");
        }
        String mode = BridgeExchange.optString(request, "mode");
        if (mode == null || mode.isBlank()) {
            mode = "full";
        }
        if (!mode.equals("full") && !mode.equals("content")) {
            throw BridgeException.invalidField("mode 는 full 또는 content 여야 합니다.");
        }
        String rawTopic = BridgeExchange.optString(request, "topic");
        String topic = rawTopic == null ? "" : rawTopic.strip();
        if (topic.codePointCount(0, topic.length()) > MAX_TOPIC_LENGTH) {
            throw BridgeException.invalidField("topic 은 " + MAX_TOPIC_LENGTH + "자 이하여야 합니다.");
        }
        if (BridgeExchange.hasControlChars(topic)) {
            throw BridgeException.invalidField("topic 에 줄바꿈 같은 제어문자를 쓸 수 없습니다.");
        }
        Boolean fakeValue = BridgeExchange.optBoolean(request, "fake");
        boolean fake = fakeValue != null && fakeValue;
        boolean contentMode = mode.equals("content");
        int direction = contentMode ? parseDirection(BridgeExchange.optString(request, "direction"), true) : 0;
        double magnitude = contentMode
                ? parseMagnitude(BridgeExchange.optDouble(request, "magnitude_percent"), true) : 0.0;
        if (contentMode && topic.isEmpty()) {
            throw BridgeException.invalidField("mode=content 에는 topic 이 필요합니다.");
        }

        Draft draft = new Draft("d-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                clock.getAsLong());
        CompletableFuture<?> future = bridge.callSync(() -> {
            GeminiNewsClient ai = bridge.plugin().getGeminiNewsClient();
            if (ai == null || !ai.isEnabled()) {
                throw new BridgeException(503, "ai_unavailable",
                        "AI 뉴스 기능이 꺼져 있습니다 (config.yml ai.enabled / ai.api-key).");
            }
            Stock stock = requireStock(stockId);
            if (contentMode) {
                return ai.generateNewsArticle(topic, fake).thenAccept(text ->
                        finish(draft, text, NewsManager.directionOf(direction), magnitude));
            }
            return ai.generateNewsWithImpact(stock, topic, fake).thenAccept(result ->
                    finish(draft, result.content(), NewsManager.directionOf(result.direction()),
                            result.magnitudePercent()));
        });
        storeDraft(draft);
        future.whenComplete((ignored, failure) -> {
            if (failure != null) {
                fail(draft, failure);
            }
        });

        JsonObject body = new JsonObject();
        body.addProperty("draft_id", draft.id);
        body.addProperty("status", draft.status);
        return BridgeResponse.ok(body);
    }

    /** {@code GET /news/ai-draft/{id}} — AI 초안 조회(폴링). 없거나 10분 지나면 404. */
    public BridgeResponse aiDraftStatus(BridgeExchange exchange) throws Exception {
        purgeDrafts();
        Draft draft = drafts.get(exchange.pathParam("id"));
        if (draft == null) {
            throw BridgeException.notFound("없거나 만료된 초안입니다.");
        }
        return BridgeResponse.ok(draft.toJson());
    }

    /** {@code PATCH /news/{id}} (쓰기) — 공개 전 수정. */
    public BridgeResponse update(BridgeExchange exchange) throws Exception {
        int id = parseNewsId(exchange.pathParam("id"));
        JsonObject request = exchange.json();
        NewsManager.NewsPatch patch = new NewsManager.NewsPatch();
        patch.content = validateContent(BridgeExchange.optString(request, "content"), false);
        String direction = BridgeExchange.optString(request, "direction");
        patch.direction = direction == null ? null : parseDirection(direction, true);
        Double magnitude = BridgeExchange.optDouble(request, "magnitude_percent");
        patch.magnitudePercent = magnitude == null ? null : parseMagnitude(magnitude, true);
        patch.fake = BridgeExchange.optBoolean(request, "fake");
        if (patch.isEmpty()) {
            throw BridgeException.invalidField("수정할 필드(content/direction/magnitude_percent/fake)가 없습니다.");
        }
        String actor = exchange.actor();
        JsonObject body = bridge.callSync(() -> {
            NewsManager.ChangeOutcome outcome = manager().edit(id, patch, actor);
            switch (outcome.status()) {
                case NOT_FOUND -> throw BridgeException.notFound("없는 뉴스입니다: " + id);
                case ALREADY_REVEALED, ALREADY_APPLIED ->
                        throw BridgeException.conflict("이미 공개되어 수정할 수 없습니다.");
                case ALREADY_CANCELLED -> throw BridgeException.conflict("이미 취소된 뉴스는 수정할 수 없습니다.");
                default -> {
                    // OK
                }
            }
            return okWithNews(outcome.news());
        });
        return BridgeResponse.ok(body);
    }

    /** {@code POST /news/{id}/cancel} (쓰기) — 반영 전 취소. 이미 취소된 뉴스면 그대로 성공. */
    public BridgeResponse cancel(BridgeExchange exchange) throws Exception {
        int id = parseNewsId(exchange.pathParam("id"));
        JsonObject request = exchange.json();
        String reason = BridgeExchange.optString(request, "reason");
        String reasonError = StockRules.validateReason(reason, false);
        if (reasonError != null) {
            throw BridgeException.invalidField(reasonError);
        }
        String actor = exchange.actor();
        JsonObject body = bridge.callSync(() -> {
            NewsManager.ChangeOutcome outcome = manager().cancel(id, reason, actor);
            switch (outcome.status()) {
                case NOT_FOUND -> throw BridgeException.notFound("없는 뉴스입니다: " + id);
                case ALREADY_APPLIED -> throw BridgeException.conflict("이미 반영되어 취소할 수 없습니다.");
                default -> {
                    // OK 또는 이미 취소됨(그대로 성공)
                }
            }
            return okWithNews(outcome.news());
        });
        return BridgeResponse.ok(body);
    }

    // ---------------------------------------------------------------- AI 초안 보관

    private void storeDraft(Draft draft) {
        purgeDrafts();
        while (drafts.size() >= MAX_DRAFTS) {
            Draft oldest = null;
            for (Draft candidate : drafts.values()) {
                if (oldest == null || candidate.createdAt < oldest.createdAt) {
                    oldest = candidate;
                }
            }
            if (oldest == null) {
                break;
            }
            drafts.remove(oldest.id);
        }
        drafts.put(draft.id, draft);
    }

    private void purgeDrafts() {
        long now = clock.getAsLong();
        Iterator<Draft> iterator = drafts.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().createdAt > DRAFT_TTL_MILLIS) {
                iterator.remove();
            }
        }
    }

    /** AI 결과 도착(AI 응답 스레드). 본문은 퍼센트·등락 표현을 거르고 256자로 자른다. */
    static void finish(Draft draft, String content, String direction, double magnitudePercent) {
        String sanitized = NewsManager.sanitizeContent(content == null ? "" : content)
                .replaceAll("[\\r\\n\\t]+", " ").strip();
        if (sanitized.isEmpty()) {
            draft.error = "AI가 빈 기사를 돌려줬습니다. 다시 시도하세요.";
            draft.status = "error";
            return;
        }
        if (sanitized.codePointCount(0, sanitized.length()) > NewsManager.MAX_CONTENT_LENGTH) {
            sanitized = sanitized.substring(0, sanitized.offsetByCodePoints(0, NewsManager.MAX_CONTENT_LENGTH));
        }
        draft.content = sanitized;
        draft.direction = direction;
        draft.magnitudePercent = magnitudePercent;
        draft.status = "done";
    }

    static void fail(Draft draft, Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        draft.error = redactError(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        draft.status = "error";
    }

    /** 오류 메시지에서 API 키(URL의 key=…)를 가리고 너무 길면 자른다. */
    static String redactError(String message) {
        String redacted = API_KEY_PATTERN.matcher(message).replaceAll("key=***");
        if (redacted.length() > MAX_ERROR_LENGTH) {
            redacted = redacted.substring(0, MAX_ERROR_LENGTH) + "…";
        }
        return redacted;
    }

    // ---------------------------------------------------------------- 검증 · 변환

    private NewsManager manager() {
        return bridge.plugin().getNewsManager();
    }

    private Stock requireStock(String id) throws BridgeException {
        Stock stock = bridge.plugin().getStockManager().getStock(id);
        if (stock == null) {
            throw BridgeException.notFound("없는 종목입니다: " + id);
        }
        return stock;
    }

    /** {@code up} → +1, {@code down} → -1. */
    static int parseDirection(String direction, boolean required) throws BridgeException {
        if (direction == null) {
            if (required) {
                throw BridgeException.invalidField("direction 은 up 또는 down 이어야 합니다.");
            }
            return 0;
        }
        return switch (direction) {
            case "up" -> 1;
            case "down" -> -1;
            default -> throw BridgeException.invalidField("direction 은 up 또는 down 이어야 합니다.");
        };
    }

    static double parseMagnitude(Double magnitude, boolean required) throws BridgeException {
        if (magnitude == null) {
            if (required) {
                throw BridgeException.invalidField("magnitude_percent 가 필요합니다.");
            }
            return 0.0;
        }
        // Gson은 기본적으로 NaN·Infinity 글자도 받아들인다. NaN은 "<= 0"을 통과하므로 따로 막는다.
        if (!Double.isFinite(magnitude) || magnitude <= 0) {
            throw BridgeException.invalidField("magnitude_percent 는 0보다 큰 숫자여야 합니다.");
        }
        return magnitude;
    }

    /** 본문 검사(1~256자, 제어문자 금지, 퍼센트·등락 표현을 거른 뒤에도 내용이 남아야 함). 없으면 null(필수면 422). */
    static String validateContent(String content, boolean required) throws BridgeException {
        if (content == null) {
            if (required) {
                throw BridgeException.invalidField("content 가 필요합니다 (1~" + NewsManager.MAX_CONTENT_LENGTH + "자).");
            }
            return null;
        }
        int length = content.codePointCount(0, content.length());
        if (content.isBlank() || length > NewsManager.MAX_CONTENT_LENGTH) {
            throw BridgeException.invalidField("content 는 1~" + NewsManager.MAX_CONTENT_LENGTH + "자여야 합니다.");
        }
        if (BridgeExchange.hasControlChars(content)) {
            throw BridgeException.invalidField("content 에 줄바꿈 같은 제어문자를 쓸 수 없습니다.");
        }
        if (NewsManager.sanitizeContent(content).isEmpty()) {
            throw BridgeException.invalidField("content 에서 퍼센트·상승/하락 표현을 빼면 남는 내용이 없습니다.");
        }
        return content;
    }

    static int parseNewsId(String raw) throws BridgeException {
        try {
            return Integer.parseInt(raw == null ? "" : raw.strip());
        } catch (NumberFormatException e) {
            throw BridgeException.badRequest("뉴스 id 는 정수여야 합니다: " + raw);
        }
    }

    private static JsonObject okWithNews(NewsManager.NewsView view) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.add("news", newsJson(view));
        return result;
    }

    /** api-bridge.md §4.9 뉴스 모델. */
    static JsonObject newsJson(NewsManager.NewsView view) {
        JsonObject json = new JsonObject();
        json.addProperty("id", view.id());
        json.addProperty("stock_id", view.stockId());
        json.addProperty("stock_name", view.stockName());
        json.addProperty("direction", view.direction());
        json.addProperty("magnitude_percent", view.magnitudePercent());
        json.addProperty("content", view.content());
        json.addProperty("fake", view.fake());
        json.addProperty("author", view.author());
        json.addProperty("created_at", view.createdAt());
        json.addProperty("reveal_at", view.revealAt());
        json.addProperty("apply_at", view.applyAt());
        json.addProperty("status", view.status());
        json.addProperty("revealed", view.revealed());
        json.addProperty("applied", view.applied());
        json.addProperty("cancelled", view.cancelled());
        json.addProperty("cancelled_at", view.cancelledAt());
        json.addProperty("cancel_reason", view.cancelReason());
        return json;
    }
}
