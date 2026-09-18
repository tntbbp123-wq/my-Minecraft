package com.tntbbp.myminecraft.log;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.UUID;

/**
 * 관리(admin) 채널 기록 헬퍼. 이벤트 형식은 gn-admin {@code docs/api-bridge.md} §5.5를 따른다.
 * 웹 통로의 쓰기 작업, 주식 가격 변화, 뉴스 이력 등 "관리 이벤트"를 남긴다.
 * {@code actor}는 웹 사용자 이름(X-GN-Actor) 또는 스케줄러가 한 일이면 {@value #SYSTEM}.
 *
 * <p>모든 메서드는 큐에 넣기만 하므로 메인 스레드에서 불러도 기다리지 않는다.
 * ({@code log_dropped}는 {@link EventLog}가 직접 남긴다.)
 */
public class AdminLog {

    public static final String SYSTEM = EventLog.SYSTEM_ACTOR;

    /** {@link #stockPrice}의 source 값. */
    public static final String SOURCE_FLUCTUATION = "fluctuation";
    public static final String SOURCE_NEWS = "news";
    public static final String SOURCE_MANUAL = "manual";

    private final EventLog log;

    public AdminLog(EventLog log) {
        this.log = log;
    }

    /** {@code POST /mail}. recipientsMode: uuids|all|online. */
    public void mailSend(String actor, String recipientsMode, int sent, int held, List<Long> mailIds, String title) {
        JsonObject data = withActor(actor);
        data.addProperty("recipients_mode", recipientsMode);
        data.addProperty("sent", sent);
        data.addProperty("held", held);
        data.add("mail_ids", TradeLogger.ids(mailIds));
        data.addProperty("title", title);
        write("mail_send", actor, data);
    }

    /** {@code POST /players/{uuid}/mail/{id}/recall}. */
    public void mailRecall(String actor, UUID uuid, long mailId) {
        JsonObject data = withActor(actor);
        data.addProperty("uuid", uuid == null ? null : uuid.toString());
        data.addProperty("mail_id", mailId);
        write("mail_recall", actor, data);
    }

    /** {@code POST /stocks}. */
    public void stockCreate(String actor, String stockId, String name) {
        JsonObject data = withActor(actor);
        data.addProperty("stock_id", stockId);
        data.addProperty("name", name);
        write("stock_create", actor, data);
    }

    /** {@code PATCH /stocks/{id}}. {@code fields}는 바뀐 필드만 담는다. */
    public void stockUpdate(String actor, String stockId, JsonObject fields) {
        JsonObject data = withActor(actor);
        data.addProperty("stock_id", stockId);
        data.add("fields", fields == null ? new JsonObject() : fields);
        write("stock_update", actor, data);
    }

    /**
     * 주식 가격 변화 <b>전부</b>(정기 변동·뉴스 반영·수동 설정). 웹 DB 가격 이력의 유일한 소스다.
     *
     * @param source {@link #SOURCE_FLUCTUATION} / {@link #SOURCE_NEWS} / {@link #SOURCE_MANUAL}
     * @param actor  수동 설정이면 웹 사용자, 아니면 {@link #SYSTEM}
     */
    public void stockPrice(String stockId, double price, double previousPrice, String reason, String source,
                           String actor) {
        String who = actor == null ? SYSTEM : actor;
        JsonObject data = new JsonObject();
        data.addProperty("stock_id", stockId);
        data.addProperty("price", price);
        data.addProperty("previous_price", previousPrice);
        data.addProperty("reason", reason);
        data.addProperty("source", source);
        data.addProperty("actor", who);
        write("stock_price", who, data);
    }

    public void stockHalt(String actor, String stockId, String reason) {
        JsonObject data = withActor(actor);
        data.addProperty("stock_id", stockId);
        data.addProperty("reason", reason);
        write("stock_halt", actor, data);
    }

    public void stockResume(String actor, String stockId, String reason) {
        JsonObject data = withActor(actor);
        data.addProperty("stock_id", stockId);
        data.addProperty("reason", reason);
        write("stock_resume", actor, data);
    }

    /** {@code POST /stocks/{id}/grant}. */
    public void stockGrant(String actor, UUID uuid, String stockId, int quantity) {
        JsonObject data = withActor(actor);
        data.addProperty("uuid", uuid == null ? null : uuid.toString());
        data.addProperty("stock_id", stockId);
        data.addProperty("quantity", quantity);
        write("stock_grant", actor, data);
    }

    /** {@code POST /news}. direction: up|down. */
    public void newsCreate(String actor, long newsId, String stockId, String direction, double magnitudePercent,
                           boolean fake) {
        JsonObject data = withActor(actor);
        data.addProperty("news_id", newsId);
        data.addProperty("stock_id", stockId);
        data.addProperty("direction", direction);
        data.addProperty("magnitude_percent", magnitudePercent);
        data.addProperty("fake", fake);
        write("news_create", actor, data);
    }

    /** {@code PATCH /news/{id}}. {@code fields}는 바뀐 필드만 담는다. */
    public void newsEdit(String actor, long newsId, JsonObject fields) {
        JsonObject data = withActor(actor);
        data.addProperty("news_id", newsId);
        data.add("fields", fields == null ? new JsonObject() : fields);
        write("news_edit", actor, data);
    }

    public void newsCancel(String actor, long newsId, String reason) {
        JsonObject data = withActor(actor);
        data.addProperty("news_id", newsId);
        data.addProperty("reason", reason);
        write("news_cancel", actor, data);
    }

    /** NewsManager가 뉴스를 공개한 시점(스케줄러). */
    public void newsReveal(long newsId, String stockId) {
        JsonObject data = new JsonObject();
        data.addProperty("news_id", newsId);
        data.addProperty("stock_id", stockId);
        data.addProperty("actor", SYSTEM);
        write("news_reveal", SYSTEM, data);
    }

    /** NewsManager가 뉴스를 가격에 반영한 시점(스케줄러). */
    public void newsApply(long newsId, String stockId, double impactPercent) {
        JsonObject data = new JsonObject();
        data.addProperty("news_id", newsId);
        data.addProperty("stock_id", stockId);
        data.addProperty("impact_percent", impactPercent);
        data.addProperty("actor", SYSTEM);
        write("news_apply", SYSTEM, data);
    }

    /** {@code POST /commands/execute}. {@code command}는 가림 규칙을 적용한 값. */
    public void commandExecute(String actor, String command, String status, int outputLines) {
        JsonObject data = withActor(actor);
        data.addProperty("command", command);
        data.addProperty("status", status);
        data.addProperty("output_lines", outputLines);
        write("command_execute", actor, data);
    }

    public void serverBroadcast(String actor, String message) {
        JsonObject data = withActor(actor);
        data.addProperty("message", message);
        write("server_broadcast", actor, data);
    }

    /** {@code POST/DELETE /server/countdown}. action: stop|restart. */
    public void serverCountdown(String actor, int seconds, String action, boolean cancelled) {
        JsonObject data = withActor(actor);
        data.addProperty("seconds", seconds);
        data.addProperty("action", action);
        data.addProperty("cancelled", cancelled);
        write("server_countdown", actor, data);
    }

    public void serverSave(String actor, long elapsedMs) {
        JsonObject data = withActor(actor);
        data.addProperty("elapsed_ms", elapsedMs);
        write("server_save", actor, data);
    }

    /** 위에 없는 관리 이벤트를 그대로 남긴다. {@code data}는 넘긴 뒤 수정하지 않는다. */
    public void record(String type, String actor, JsonObject data) {
        write(type, actor, data);
    }

    private void write(String type, String actor, JsonObject data) {
        log.write(EventLog.Channel.ADMIN, type, actor == null ? SYSTEM : actor, data);
    }

    private static JsonObject withActor(String actor) {
        JsonObject data = new JsonObject();
        data.addProperty("actor", actor == null ? SYSTEM : actor);
        return data;
    }
}
