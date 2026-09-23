package com.tntbbp.myminecraft.log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.UUID;

/**
 * 거래(trade) 채널 기록 헬퍼. 이벤트 형식은 gn-admin {@code docs/api-bridge.md} §5.4를 따른다.
 * 모든 메서드는 큐에 넣기만 하므로 메인 스레드에서 불러도 기다리지 않는다.
 *
 * <p>플레이어 거래의 {@code actor}는 플레이어 이름(모르면 UUID), 관리자 지급의 {@code actor}는 웹 사용자 이름이다.
 * 거래소·개인 거래처럼 아직 없는 유형({@code exchange_list}, {@code exchange_sale}, {@code exchange_expire},
 * {@code p2p_trade})은 기능이 생기면 {@link #record}로 남기면 된다.
 */
public class TradeLogger {

    /** 첨부/지급 아이템 요약 한 줄 ({@code {"name","count"}}). */
    public record ItemCount(String name, int count) {
    }

    private final EventLog log;

    public TradeLogger(EventLog log) {
        this.log = log;
    }

    /** 주식 매수. {@code name}은 모르면 null. */
    public void stockBuy(UUID uuid, String name, String stockId, String stockName, int quantity,
                         double unitPrice, double total) {
        stockTrade("stock_buy", uuid, name, stockId, stockName, quantity, unitPrice, total);
    }

    /** 주식 매도. {@code name}은 모르면 null. */
    public void stockSell(UUID uuid, String name, String stockId, String stockName, int quantity,
                          double unitPrice, double total) {
        stockTrade("stock_sell", uuid, name, stockId, stockName, quantity, unitPrice, total);
    }

    /** 동전 구매(포인트 → 동전). */
    public void coinBuy(UUID uuid, String name, String coinId, int coinValue, int count, double total) {
        coinTrade("coin_buy", uuid, name, coinId, coinValue, count, total);
    }

    /** 동전 판매(동전 → 포인트). */
    public void coinSell(UUID uuid, String name, String coinId, int coinValue, int count, double total) {
        coinTrade("coin_sell", uuid, name, coinId, coinValue, count, total);
    }

    /** 은행 전부입금. {@code amount}는 입금된 총액. */
    public void bankDepositAll(UUID uuid, String name, double amount) {
        JsonObject data = player(uuid, name);
        data.addProperty("amount", amount);
        log.write(EventLog.Channel.TRADE, "bank_deposit_all", actorOf(uuid, name), data);
    }

    /** 은행 전부출금. {@code amount}는 출금된 총액. */
    public void bankWithdrawAll(UUID uuid, String name, double amount) {
        JsonObject data = player(uuid, name);
        data.addProperty("amount", amount);
        log.write(EventLog.Channel.TRADE, "bank_withdraw_all", actorOf(uuid, name), data);
    }

    /** 코어 상점 구매. */
    public void shopCore(UUID uuid, String name, String itemName, double price) {
        shop("shop_core", uuid, name, itemName, price);
    }

    /** 코어 암시장 구매. */
    public void shopBlackmarket(UUID uuid, String name, String itemName, double price) {
        shop("shop_blackmarket", uuid, name, itemName, price);
    }

    /** 웹 관리자 우편 발송. */
    public void adminMailSend(String actor, int recipientsCount, String title, List<ItemCount> attachments,
                              long attachedG, List<Long> mailIds) {
        JsonObject data = new JsonObject();
        data.addProperty("actor", actor);
        data.addProperty("recipients_count", recipientsCount);
        data.addProperty("title", title);
        data.add("attachments", items(attachments));
        data.addProperty("attached_g", attachedG);
        data.add("mail_ids", ids(mailIds));
        log.write(EventLog.Channel.TRADE, "admin_mail_send", actor, data);
    }

    /** 우편 수령. 인벤토리가 부족해 일부만 받았으면 {@code partial=true}. */
    public void mailClaim(UUID uuid, String name, long mailId, List<ItemCount> attachments, long attachedG,
                          boolean partial) {
        JsonObject data = player(uuid, name);
        data.addProperty("mail_id", mailId);
        data.add("attachments", items(attachments));
        data.addProperty("attached_g", attachedG);
        data.addProperty("partial", partial);
        log.write(EventLog.Channel.TRADE, "mail_claim", actorOf(uuid, name), data);
    }

    /** 웹 관리자 주식 지급. */
    public void adminStockGrant(String actor, UUID uuid, String name, String stockId, int quantity) {
        JsonObject data = new JsonObject();
        data.addProperty("actor", actor);
        data.addProperty("uuid", uuid == null ? null : uuid.toString());
        data.addProperty("name", name);
        data.addProperty("stock_id", stockId);
        data.addProperty("quantity", quantity);
        log.write(EventLog.Channel.TRADE, "admin_stock_grant", actor, data);
    }

    /**
     * 대장간 강화 비용. 성공·실패와 관계없이 동전(G)과 강화석이 나간다.
     *
     * @param amount    나간 G (동전으로 낸 값에서 거스름돈을 뺀 순액)
     * @param stones    소모한 강화석 수
     * @param fromLevel 강화 전 레벨
     */
    public void enhanceCost(UUID uuid, String name, String itemName, int fromLevel, double amount, int stones,
                            boolean usedScroll, boolean success) {
        JsonObject data = player(uuid, name);
        data.addProperty("item_name", itemName);
        data.addProperty("from_level", fromLevel);
        data.addProperty("amount", amount);
        data.addProperty("stones", stones);
        data.addProperty("used_scroll", usedScroll);
        data.addProperty("success", success);
        log.write(EventLog.Channel.TRADE, "enhance_cost", actorOf(uuid, name), data);
    }

    /** 일괄 약탈 주문서로 상자 안의 아이템을 통째로 가져감. */
    public void blackmarketLootAll(UUID uuid, String name, String world, int x, int y, int z, List<ItemCount> taken) {
        JsonObject data = player(uuid, name);
        data.addProperty("world", world);
        data.addProperty("x", x);
        data.addProperty("y", y);
        data.addProperty("z", z);
        data.add("items", items(taken));
        int total = 0;
        for (ItemCount item : taken) {
            total += item.count();
        }
        data.addProperty("count", total);
        log.write(EventLog.Channel.TRADE, "blackmarket_loot_all", actorOf(uuid, name), data);
    }

    /** 위에 없는 거래 유형(예약 유형 등)을 그대로 남긴다. {@code data}는 넘긴 뒤 수정하지 않는다. */
    public void record(String type, String actor, JsonObject data) {
        log.write(EventLog.Channel.TRADE, type, actor, data);
    }

    private void stockTrade(String type, UUID uuid, String name, String stockId, String stockName, int quantity,
                            double unitPrice, double total) {
        JsonObject data = player(uuid, name);
        data.addProperty("stock_id", stockId);
        data.addProperty("stock_name", stockName);
        data.addProperty("quantity", quantity);
        data.addProperty("unit_price", unitPrice);
        data.addProperty("total", total);
        log.write(EventLog.Channel.TRADE, type, actorOf(uuid, name), data);
    }

    private void coinTrade(String type, UUID uuid, String name, String coinId, int coinValue, int count,
                           double total) {
        JsonObject data = player(uuid, name);
        data.addProperty("coin_id", coinId);
        data.addProperty("coin_value", coinValue);
        data.addProperty("count", count);
        data.addProperty("total", total);
        log.write(EventLog.Channel.TRADE, type, actorOf(uuid, name), data);
    }

    private void shop(String type, UUID uuid, String name, String itemName, double price) {
        JsonObject data = player(uuid, name);
        data.addProperty("item_name", itemName);
        data.addProperty("price", price);
        log.write(EventLog.Channel.TRADE, type, actorOf(uuid, name), data);
    }

    private static JsonObject player(UUID uuid, String name) {
        JsonObject data = new JsonObject();
        data.addProperty("uuid", uuid == null ? null : uuid.toString());
        data.addProperty("name", name);
        return data;
    }

    private static String actorOf(UUID uuid, String name) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return uuid == null ? EventLog.SYSTEM_ACTOR : uuid.toString();
    }

    static JsonArray items(List<ItemCount> attachments) {
        JsonArray array = new JsonArray();
        if (attachments == null) {
            return array;
        }
        for (ItemCount item : attachments) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", item.name());
            entry.addProperty("count", item.count());
            array.add(entry);
        }
        return array;
    }

    static JsonArray ids(List<Long> ids) {
        JsonArray array = new JsonArray();
        if (ids == null) {
            return array;
        }
        for (Long id : ids) {
            array.add(id);
        }
        return array;
    }
}
