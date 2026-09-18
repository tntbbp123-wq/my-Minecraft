package com.tntbbp.myminecraft.manager.economy;

import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.log.AdminLog;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.util.AtomicYaml;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 관리자가 작성한 (진짜/가짜) 뉴스를 예약 발행한다.
 * 작성 후 {@code reveal-delay-seconds}가 지나면 해당 종목에 커서를 올렸을 때(주식 거래소 GUI 툴팁)
 * 보이기 시작하고, 작성 후 {@code apply-delay-seconds}가 지나면 (가짜 뉴스가 아닌 경우에만)
 * 해당 종목 가격에 실제로 반영된다. 전체 채팅 공지는 하지 않는다.
 *
 * <p>상태: 공개 전(pending) → 공개됨(revealed) → 반영됨(applied), 또는 반영 전에 취소(cancelled).
 * 내용 수정은 공개 전까지만, 취소는 반영 전까지만 된다. 반영·취소된 뉴스도 지우지 않고
 * {@code news.yml}의 {@code history}에 보관한다(웹 관리에서 이력 조회). 작성·수정·취소·공개·반영은
 * 관리 기록(admin 채널)에 남긴다.
 *
 * <p>모든 메서드는 메인 스레드에서 호출한다.
 */
public class NewsManager {

    /** 이력으로 보관하는 반영·취소 뉴스 최대 개수(넘으면 오래된 것부터 버림). */
    static final int HISTORY_LIMIT = 1000;
    /** 뉴스 본문 최대 길이(코드 포인트). */
    public static final int MAX_CONTENT_LENGTH = 256;

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_REVEALED = "revealed";
    public static final String STATUS_APPLIED = "applied";
    public static final String STATUS_CANCELLED = "cancelled";

    /** 뉴스 한 건(내부 상태). 밖에서는 {@link NewsView}로 읽는다. */
    public static final class NewsItem {
        int id;
        String stockId;
        String stockName;
        double impactPercent;
        String content;
        boolean fake;
        long createdAtMillis;
        long revealAtMillis;
        long applyAtMillis;
        boolean revealed;
        boolean applied;
        String author;
        boolean cancelled;
        long cancelledAtMillis;
        String cancelReason;

        boolean isActive() {
            return !applied && !cancelled;
        }

        public NewsView view() {
            return new NewsView(id, stockId, stockName, impactPercent, content, fake, author, createdAtMillis,
                    revealAtMillis, applyAtMillis, revealed, applied, cancelled,
                    cancelled ? cancelledAtMillis : null, cancelReason);
        }
    }

    /** 조회용 읽기 전용 뷰(웹 관리 {@code GET /news}의 한 항목). */
    public record NewsView(int id, String stockId, String stockName, double impactPercent, String content,
                           boolean fake, String author, long createdAt, long revealAt, long applyAt,
                           boolean revealed, boolean applied, boolean cancelled, Long cancelledAt,
                           String cancelReason) {

        /** {@code pending|revealed|applied|cancelled}. */
        public String status() {
            return statusOf(revealed, applied, cancelled);
        }

        /** {@code up|down}. */
        public String direction() {
            return directionOf(impactPercent);
        }

        public double magnitudePercent() {
            return Math.abs(impactPercent);
        }
    }

    /** 공개 전 수정 내용. null인 필드는 바꾸지 않는다. {@code direction}은 +1(상승)/-1(하락). */
    public static final class NewsPatch {
        public String content;
        public Integer direction;
        public Double magnitudePercent;
        public Boolean fake;

        public boolean isEmpty() {
            return content == null && direction == null && magnitudePercent == null && fake == null;
        }
    }

    public enum ChangeStatus {
        OK,
        NOT_FOUND,
        /** 이미 공개돼 수정할 수 없음. */
        ALREADY_REVEALED,
        /** 이미 가격에 반영돼 취소할 수 없음. */
        ALREADY_APPLIED,
        /** 이미 취소됨. */
        ALREADY_CANCELLED
    }

    /** 수정·취소 결과. */
    public record ChangeOutcome(ChangeStatus status, NewsView news) {
    }

    private static final Pattern PERCENT_PATTERN = Pattern.compile("[+-]?\\d+(\\.\\d+)?\\s*%");
    private static final Pattern DIRECTION_WORD_PATTERN = Pattern.compile("상승|하락");

    /**
     * 뉴스 내용에는 실제 사건/소식만 담아야 하며, 등락 방향이나 변동폭(%)은 관리자가
     * 명령어로 별도 입력한 값으로만 내부에서 처리된다. 관리자가 실수로 넣거나 AI가
     * 생성 과정에서 덧붙인 퍼센트/등락 표현은 여기서 걸러낸다.
     */
    public static String sanitizeContent(String content) {
        String sanitized = PERCENT_PATTERN.matcher(content).replaceAll("");
        sanitized = DIRECTION_WORD_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = sanitized.replaceAll("[ \\t]{2,}", " ").trim();
        return sanitized;
    }

    /** 상태 계산: 취소 → cancelled, 반영 → applied, 공개 → revealed, 그 밖 → pending. */
    public static String statusOf(boolean revealed, boolean applied, boolean cancelled) {
        if (cancelled) {
            return STATUS_CANCELLED;
        }
        if (applied) {
            return STATUS_APPLIED;
        }
        if (revealed) {
            return STATUS_REVEALED;
        }
        return STATUS_PENDING;
    }

    /** 변동률 부호 → {@code up}(0 포함)/{@code down}. */
    public static String directionOf(double impactPercent) {
        return impactPercent < 0 ? "down" : "up";
    }

    /** 방향(+1/-1)과 변동폭(%)으로 내부 변동률을 만든다. */
    public static double impactOf(int direction, double magnitudePercent) {
        return Math.abs(magnitudePercent) * (direction < 0 ? -1 : 1);
    }

    /** 공개 전 수정 가능 여부(공개·반영·취소되지 않은 뉴스만). */
    public static ChangeStatus editCheck(boolean revealed, boolean applied, boolean cancelled) {
        if (cancelled) {
            return ChangeStatus.ALREADY_CANCELLED;
        }
        if (applied) {
            return ChangeStatus.ALREADY_APPLIED;
        }
        if (revealed) {
            return ChangeStatus.ALREADY_REVEALED;
        }
        return ChangeStatus.OK;
    }

    /** 반영 전 취소 가능 여부(이미 취소된 것은 {@link ChangeStatus#ALREADY_CANCELLED}). */
    public static ChangeStatus cancelCheck(boolean applied, boolean cancelled) {
        if (cancelled) {
            return ChangeStatus.ALREADY_CANCELLED;
        }
        if (applied) {
            return ChangeStatus.ALREADY_APPLIED;
        }
        return ChangeStatus.OK;
    }

    /** {@code GET /news?status=}의 필터 값인지. */
    public static boolean isStatusFilter(String status) {
        return "all".equals(status) || STATUS_PENDING.equals(status) || STATUS_REVEALED.equals(status)
                || STATUS_APPLIED.equals(status) || STATUS_CANCELLED.equals(status);
    }

    private final MyMinecraftPlugin plugin;
    private final StockManager stockManager;
    private final File file;
    private final YamlConfiguration data;
    /** 모든 뉴스(진행 중 + 이력), id 순. */
    private final List<NewsItem> items = new ArrayList<>();
    private int nextId = 1;
    private BukkitTask task;

    public NewsManager(MyMinecraftPlugin plugin, StockManager stockManager) {
        this.plugin = plugin;
        this.stockManager = stockManager;
        this.file = new File(plugin.getDataFolder(), "news.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("news.yml 생성 실패: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        for (String key : List.of("pending", "history")) {
            for (Map<?, ?> raw : data.getMapList(key)) {
                items.add(parse(raw));
            }
        }
        items.sort(Comparator.comparingInt(item -> item.id));
        int maxId = 0;
        for (NewsItem item : items) {
            maxId = Math.max(maxId, item.id);
        }
        nextId = Math.max(data.getInt("next-id", 1), maxId + 1);
    }

    private NewsItem parse(Map<?, ?> raw) {
        NewsItem item = new NewsItem();
        item.id = raw.get("id") instanceof Number n ? n.intValue() : nextId++;
        item.stockId = String.valueOf(raw.get("stock-id"));
        item.stockName = raw.get("stock-name") != null ? String.valueOf(raw.get("stock-name")) : item.stockId;
        item.impactPercent = raw.get("impact-percent") instanceof Number n ? n.doubleValue() : 0.0;
        item.content = raw.get("content") != null ? String.valueOf(raw.get("content")) : "";
        item.fake = Boolean.TRUE.equals(raw.get("fake"));
        item.createdAtMillis = raw.get("created-at") instanceof Number n ? n.longValue() : System.currentTimeMillis();
        item.revealAtMillis = raw.get("reveal-at") instanceof Number n ? n.longValue() : item.createdAtMillis;
        item.applyAtMillis = raw.get("apply-at") instanceof Number n ? n.longValue() : item.createdAtMillis;
        item.revealed = Boolean.TRUE.equals(raw.get("revealed"));
        item.applied = Boolean.TRUE.equals(raw.get("applied"));
        // 예전 파일에는 작성자가 없다 → 알 수 없음으로 둔다.
        item.author = raw.get("author") != null ? String.valueOf(raw.get("author")) : "unknown";
        item.cancelled = Boolean.TRUE.equals(raw.get("cancelled"));
        item.cancelledAtMillis = raw.get("cancelled-at") instanceof Number n ? n.longValue() : 0L;
        item.cancelReason = raw.get("cancel-reason") != null ? String.valueOf(raw.get("cancel-reason")) : null;
        return item;
    }

    private void save() {
        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> history = new ArrayList<>();
        for (NewsItem item : items) {
            (item.isActive() ? pending : history).add(serialize(item));
        }
        data.set("next-id", nextId);
        data.set("pending", pending);
        data.set("history", history);
        try {
            AtomicYaml.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().severe("news.yml 저장 실패: " + e.getMessage());
        }
    }

    private static Map<String, Object> serialize(NewsItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.id);
        map.put("stock-id", item.stockId);
        map.put("stock-name", item.stockName);
        map.put("impact-percent", item.impactPercent);
        map.put("content", item.content);
        map.put("fake", item.fake);
        map.put("author", item.author);
        map.put("created-at", item.createdAtMillis);
        map.put("reveal-at", item.revealAtMillis);
        map.put("apply-at", item.applyAtMillis);
        map.put("revealed", item.revealed);
        map.put("applied", item.applied);
        map.put("cancelled", item.cancelled);
        if (item.cancelled) {
            map.put("cancelled-at", item.cancelledAtMillis);
            if (item.cancelReason != null) {
                map.put("cancel-reason", item.cancelReason);
            }
        }
        return map;
    }

    /** 이력(반영·취소된 뉴스)이 너무 많으면 오래된 것부터 버린다. */
    private void trimHistory() {
        long historyCount = items.stream().filter(item -> !item.isActive()).count();
        if (historyCount <= HISTORY_LIMIT) {
            return;
        }
        long toRemove = historyCount - HISTORY_LIMIT;
        List<NewsItem> removed = new ArrayList<>();
        for (NewsItem item : items) {
            if (removed.size() >= toRemove) {
                break;
            }
            if (!item.isActive()) {
                removed.add(item);
            }
        }
        items.removeAll(removed);
    }

    /**
     * 새 뉴스를 예약한다. direction은 +1(상승) 또는 -1(하락), magnitudePercent는 0 이상의 변동폭(%).
     *
     * @param author 작성자(웹 사용자 이름, 플레이어 이름, "console" 등). 관리 기록 {@code news_create}의 actor.
     */
    public NewsItem submit(Stock stock, int direction, double magnitudePercent, String content, boolean fake,
                           String author) {
        long now = System.currentTimeMillis();
        long revealDelay = plugin.getConfig().getLong("news.reveal-delay-seconds", 3600) * 1000L;
        long applyDelay = plugin.getConfig().getLong("news.apply-delay-seconds", 7200) * 1000L;

        NewsItem item = new NewsItem();
        item.id = nextId++;
        item.stockId = stock.getId();
        item.stockName = stock.getName();
        item.impactPercent = impactOf(direction, magnitudePercent);
        item.content = sanitizeContent(content);
        item.fake = fake;
        item.author = author == null || author.isBlank() ? "unknown" : author;
        item.createdAtMillis = now;
        item.revealAtMillis = now + revealDelay;
        item.applyAtMillis = now + applyDelay;
        items.add(item);
        save();

        AdminLog adminLog = plugin.getAdminLog();
        if (adminLog != null) {
            adminLog.newsCreate(item.author, item.id, item.stockId, directionOf(item.impactPercent),
                    Math.abs(item.impactPercent), item.fake);
        }
        return item;
    }

    /** 공개 전 뉴스의 내용·방향·변동폭·가짜 여부를 바꾼다. 본문은 {@link #sanitizeContent}를 거친다. */
    public ChangeOutcome edit(int id, NewsPatch patch, String actor) {
        NewsItem item = find(id);
        if (item == null) {
            return new ChangeOutcome(ChangeStatus.NOT_FOUND, null);
        }
        ChangeStatus check = editCheck(item.revealed, item.applied, item.cancelled);
        if (check != ChangeStatus.OK) {
            return new ChangeOutcome(check, item.view());
        }
        JsonObject fields = new JsonObject();
        if (patch.content != null) {
            String sanitized = sanitizeContent(patch.content);
            if (!sanitized.equals(item.content)) {
                item.content = sanitized;
                fields.addProperty("content", sanitized);
            }
        }
        int direction = patch.direction != null ? patch.direction : (item.impactPercent < 0 ? -1 : 1);
        double magnitude = patch.magnitudePercent != null ? patch.magnitudePercent : Math.abs(item.impactPercent);
        double impact = impactOf(direction, magnitude);
        if (impact != item.impactPercent) {
            if (!directionOf(impact).equals(directionOf(item.impactPercent))) {
                fields.addProperty("direction", directionOf(impact));
            }
            if (Math.abs(impact) != Math.abs(item.impactPercent)) {
                fields.addProperty("magnitude_percent", Math.abs(impact));
            }
            item.impactPercent = impact;
        }
        if (patch.fake != null && patch.fake != item.fake) {
            item.fake = patch.fake;
            fields.addProperty("fake", item.fake);
        }
        if (fields.size() > 0) {
            save();
            AdminLog adminLog = plugin.getAdminLog();
            if (adminLog != null) {
                adminLog.newsEdit(actor, item.id, fields);
            }
        }
        return new ChangeOutcome(ChangeStatus.OK, item.view());
    }

    /**
     * 반영 전 뉴스를 취소한다(이력은 남는다). 이미 취소된 뉴스면 아무것도 바꾸지 않고
     * {@link ChangeStatus#ALREADY_CANCELLED}와 현재 상태를 돌려준다.
     */
    public ChangeOutcome cancel(int id, String reason, String actor) {
        NewsItem item = find(id);
        if (item == null) {
            return new ChangeOutcome(ChangeStatus.NOT_FOUND, null);
        }
        ChangeStatus check = cancelCheck(item.applied, item.cancelled);
        if (check != ChangeStatus.OK) {
            return new ChangeOutcome(check, item.view());
        }
        item.cancelled = true;
        item.cancelledAtMillis = System.currentTimeMillis();
        item.cancelReason = reason == null || reason.isBlank() ? null : reason.strip();
        trimHistory();
        save();
        AdminLog adminLog = plugin.getAdminLog();
        if (adminLog != null) {
            adminLog.newsCancel(actor, item.id, item.cancelReason);
        }
        return new ChangeOutcome(ChangeStatus.OK, item.view());
    }

    public void startTask() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkDue, 20L * 10, 20L * 30);
    }

    public void stopTask() {
        if (task != null) {
            task.cancel();
        }
    }

    private void checkDue() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        AdminLog adminLog = plugin.getAdminLog();

        for (NewsItem item : items) {
            if (item.isActive() && !item.revealed && item.revealAtMillis <= now) {
                item.revealed = true;
                changed = true;
                if (adminLog != null) {
                    adminLog.newsReveal(item.id, item.stockId);
                }
            }
        }

        for (NewsItem item : items) {
            if (item.isActive() && item.revealed && item.applyAtMillis <= now) {
                item.applied = true;
                changed = true;
                double appliedImpact = 0.0;
                if (!item.fake) {
                    Stock stock = stockManager.getStock(item.stockId);
                    if (stock != null) {
                        stockManager.applyNewsImpact(stock, item.impactPercent, item.id);
                        appliedImpact = item.impactPercent;
                    }
                }
                if (adminLog != null) {
                    adminLog.newsApply(item.id, item.stockId, appliedImpact);
                }
            }
        }

        if (changed) {
            trimHistory();
            save();
        }
    }

    private NewsItem find(int id) {
        for (NewsItem item : items) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }

    /** 뉴스 한 건. 없으면 null. */
    public NewsView get(int id) {
        NewsItem item = find(id);
        return item == null ? null : item.view();
    }

    /**
     * 조회용 목록(진행 중 + 이력), 최신순(작성 시각 내림차순, 같으면 id 내림차순).
     *
     * @param status {@code all} 또는 null이면 전부, 아니면 {@code pending|revealed|applied|cancelled} 중 그 상태만
     */
    public List<NewsView> all(String status) {
        List<NewsView> result = new ArrayList<>();
        for (NewsItem item : items) {
            NewsView view = item.view();
            if (status == null || "all".equals(status) || view.status().equals(status)) {
                result.add(view);
            }
        }
        result.sort(Comparator.comparingLong(NewsView::createdAt).thenComparingInt(NewsView::id).reversed());
        return result;
    }

    /** 아직 반영·취소되지 않은(진행 중인) 뉴스. */
    public List<NewsItem> getPendingItems() {
        List<NewsItem> result = new ArrayList<>();
        for (NewsItem item : items) {
            if (item.isActive()) {
                result.add(item);
            }
        }
        return result;
    }

    /** 해당 종목에 대해 공개되었지만 아직 가격에 반영되지 않은(취소되지 않은) 뉴스 내용 목록 (주식 GUI 툴팁 표시용). */
    public List<String> getRevealedNewsForStock(String stockId) {
        List<String> result = new ArrayList<>();
        for (NewsItem item : items) {
            if (item.isActive() && item.revealed && item.stockId.equalsIgnoreCase(stockId)) {
                result.add(item.content);
            }
        }
        return result;
    }
}
