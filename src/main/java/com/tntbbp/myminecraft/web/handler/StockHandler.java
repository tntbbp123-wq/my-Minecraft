package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.manager.economy.StockManager;
import com.tntbbp.myminecraft.manager.economy.StockRules;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.web.BridgeException;
import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.PlayerNames;
import com.tntbbp.myminecraft.web.WebBridge;
import org.bukkit.Material;

import java.util.List;
import java.util.UUID;

/**
 * 주식 관리 API (api-bridge.md §4.1~4.8). 종목 조회·추가·수정·가격 직접 설정·거래 중지/재개·지급.
 * 모든 매니저 접근은 메인 스레드({@link WebBridge#callSync})에서 하고, 관리/거래 기록은
 * {@link StockManager}가 남긴다(actor = X-GN-Actor).
 */
public class StockHandler {

    private final WebBridge bridge;

    public StockHandler(WebBridge bridge) {
        this.bridge = bridge;
    }

    /** {@code GET /stocks}. */
    public BridgeResponse list(BridgeExchange exchange) throws Exception {
        JsonObject body = bridge.callSync(() -> {
            JsonArray stocks = new JsonArray();
            for (Stock stock : manager().getStocks()) {
                stocks.add(stockJson(stock));
            }
            JsonObject result = new JsonObject();
            result.add("stocks", stocks);
            return result;
        });
        return BridgeResponse.ok(body);
    }

    /** {@code GET /stocks/{id}}. */
    public BridgeResponse get(BridgeExchange exchange) throws Exception {
        String id = exchange.pathParam("id");
        JsonObject body = bridge.callSync(() -> {
            JsonObject result = new JsonObject();
            result.add("stock", stockJson(requireStock(id)));
            return result;
        });
        return BridgeResponse.ok(body);
    }

    /** {@code GET /stocks/{id}/holders}. 이름은 서버가 아는 것만(모르면 null). */
    public BridgeResponse holders(BridgeExchange exchange) throws Exception {
        String id = exchange.pathParam("id");
        JsonObject body = bridge.callSync(() -> {
            Stock stock = requireStock(id);
            List<StockManager.Holder> holders = manager().holdersOf(stock.getId());
            JsonArray array = new JsonArray();
            for (StockManager.Holder holder : holders) {
                JsonObject entry = new JsonObject();
                entry.addProperty("uuid", holder.uuid().toString());
                entry.addProperty("name", PlayerNames.nameOf(holder.uuid()));
                entry.addProperty("quantity", holder.quantity());
                array.add(entry);
            }
            JsonObject result = new JsonObject();
            result.addProperty("count", holders.size());
            result.add("holders", array);
            return result;
        });
        return BridgeResponse.ok(body);
    }

    /** {@code POST /stocks} (쓰기) — 종목 추가. 시작가 = min_price. */
    public BridgeResponse create(BridgeExchange exchange) throws Exception {
        JsonObject request = exchange.json();
        String name = BridgeExchange.optString(request, "name");
        if (name == null) {
            throw BridgeException.invalidField("name 이 필요합니다.");
        }
        double minPrice = requiredNumber(request, "min_price");
        double maxPrice = requiredNumber(request, "max_price");
        double changeUnit = requiredNumber(request, "change_unit");
        String business = BridgeExchange.optString(request, "business");
        String iconName = BridgeExchange.optString(request, "icon_material");
        String actor = exchange.actor();

        JsonObject body = bridge.callSync(() -> {
            Material icon = iconName == null || iconName.isBlank() ? null : requireIcon(iconName);
            StockManager.AddOutcome outcome = manager().addCustomStock(name, minPrice, maxPrice, changeUnit,
                    business == null ? "" : business, icon, actor);
            switch (outcome.result()) {
                case DUPLICATE_NAME -> throw BridgeException.conflict(outcome.message());
                case INVALID_RANGE, INVALID_NAME -> throw BridgeException.invalidField(outcome.message());
                default -> {
                    // SUCCESS
                }
            }
            return okWithStock(outcome.stock(), null);
        });
        return BridgeResponse.ok(body);
    }

    /** {@code PATCH /stocks/{id}} (쓰기, If-Match: revision) — 종목 수정. 준 필드만 바꾼다. */
    public BridgeResponse update(BridgeExchange exchange) throws Exception {
        String id = exchange.pathParam("id");
        String ifMatch = exchange.header("If-Match");
        if (ifMatch == null || ifMatch.isBlank()) {
            throw BridgeException.badRequest("If-Match 헤더(현재 revision)가 필요합니다.");
        }
        Integer expected = StockRules.parseRevision(ifMatch);
        if (expected == null) {
            throw BridgeException.badRequest("If-Match 헤더는 revision 정수여야 합니다.");
        }
        JsonObject request = exchange.json();
        StockManager.StockPatch patch = new StockManager.StockPatch();
        patch.name = BridgeExchange.optString(request, "name");
        patch.minPrice = BridgeExchange.optDouble(request, "min_price");
        patch.maxPrice = BridgeExchange.optDouble(request, "max_price");
        patch.changeUnit = BridgeExchange.optDouble(request, "change_unit");
        patch.business = BridgeExchange.optString(request, "business");
        String iconName = BridgeExchange.optString(request, "icon_material");
        String actor = exchange.actor();

        JsonObject body = bridge.callSync(() -> {
            if (iconName != null) {
                patch.icon = requireIcon(iconName);
            }
            StockManager.UpdateOutcome outcome = manager().updateStock(id, patch, expected, actor);
            switch (outcome.status()) {
                case NOT_FOUND -> throw BridgeException.notFound(outcome.message());
                case REVISION_CONFLICT -> throw new BridgeException(409, "stock_revision_conflict", outcome.message());
                case DUPLICATE_NAME -> throw BridgeException.conflict(outcome.message());
                case INVALID -> throw BridgeException.invalidField(outcome.message());
                default -> {
                    // OK
                }
            }
            return okWithStock(outcome.stock(), outcome.message());
        });
        return BridgeResponse.ok(body);
    }

    /** {@code POST /stocks/{id}/price} (쓰기) — 가격 직접 설정(사유 필수). */
    public BridgeResponse setPrice(BridgeExchange exchange) throws Exception {
        String id = exchange.pathParam("id");
        JsonObject request = exchange.json();
        double price = requiredNumber(request, "price");
        String reason = BridgeExchange.optString(request, "reason");
        String reasonError = StockRules.validateReason(reason, true);
        if (reasonError != null) {
            throw BridgeException.invalidField(reasonError);
        }
        String actor = exchange.actor();
        JsonObject body = bridge.callSync(() -> {
            requireStock(id);
            StockManager.PriceOutcome outcome = manager().setPrice(id, price, reason.strip(), actor);
            String message = outcome.clamped()
                    ? "요청한 가격 " + price + " 이(가) 가격 범위를 벗어나 " + outcome.stock().getPrice() + " (으)로 맞췄습니다."
                    : null;
            return okWithStock(outcome.stock(), message);
        });
        return BridgeResponse.ok(body);
    }

    /** {@code POST /stocks/{id}/halt} (쓰기) — 거래 중지. 이미 중지면 그대로 성공. */
    public BridgeResponse halt(BridgeExchange exchange) throws Exception {
        return changeHalted(exchange, true);
    }

    /** {@code POST /stocks/{id}/resume} (쓰기) — 거래 재개. 이미 거래 중이면 그대로 성공. */
    public BridgeResponse resume(BridgeExchange exchange) throws Exception {
        return changeHalted(exchange, false);
    }

    private BridgeResponse changeHalted(BridgeExchange exchange, boolean halted) throws Exception {
        String id = exchange.pathParam("id");
        JsonObject request = exchange.json();
        String reason = BridgeExchange.optString(request, "reason");
        String reasonError = StockRules.validateReason(reason, false);
        if (reasonError != null) {
            throw BridgeException.invalidField(reasonError);
        }
        String finalReason = reason == null ? "" : reason.strip();
        String actor = exchange.actor();
        JsonObject body = bridge.callSync(() -> {
            requireStock(id);
            Stock stock = halted
                    ? manager().halt(id, finalReason, actor)
                    : manager().resume(id, finalReason, actor);
            return okWithStock(stock, null);
        });
        return BridgeResponse.ok(body);
    }

    /** {@code POST /stocks/{id}/grant} (쓰기) — 주식 지급(오프라인 OK). */
    public BridgeResponse grant(BridgeExchange exchange) throws Exception {
        String id = exchange.pathParam("id");
        JsonObject request = exchange.json();
        String rawUuid = BridgeExchange.optString(request, "uuid");
        if (rawUuid == null) {
            throw BridgeException.invalidField("uuid 가 필요합니다.");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(rawUuid.strip());
        } catch (IllegalArgumentException e) {
            throw BridgeException.invalidField("uuid 형식이 올바르지 않습니다: " + rawUuid);
        }
        Integer quantity = BridgeExchange.optInt(request, "quantity");
        if (quantity == null) {
            throw BridgeException.invalidField("quantity 가 필요합니다.");
        }
        if (quantity < 1) {
            throw BridgeException.invalidField("quantity 는 1 이상이어야 합니다.");
        }
        String actor = exchange.actor();
        // 월드를 초기화하면 플레이어 파일(playerdata)이 사라져도 주식 보유(stockholdings.yml)는 남으므로
        // "접속한 적 있는지"는 검사하지 않는다. 대상 확인은 웹이 플레이어 목록에서 고르는 것으로 한다.
        JsonObject body = bridge.callSync(() -> {
            Stock stock = requireStock(id);
            int holding = manager().giveHolding(uuid, stock.getId(), quantity, actor);
            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.addProperty("holding", holding);
            return result;
        });
        return BridgeResponse.ok(body);
    }

    // ---------------------------------------------------------------- 공용

    private StockManager manager() {
        return bridge.plugin().getStockManager();
    }

    /** 메인 스레드에서 호출. 없으면 404. */
    private Stock requireStock(String id) throws BridgeException {
        Stock stock = manager().getStock(id);
        if (stock == null) {
            throw BridgeException.notFound("없는 종목입니다: " + id);
        }
        return stock;
    }

    /** 메인 스레드에서 호출. 아이템으로 쓸 수 없는 재질이면 422. */
    private static Material requireIcon(String name) throws BridgeException {
        Material icon = StockManager.parseIcon(name);
        if (icon == null) {
            throw BridgeException.invalidField("icon_material 이 아이템 재질이 아닙니다: " + name);
        }
        return icon;
    }

    private static double requiredNumber(JsonObject request, String key) throws BridgeException {
        Double value = BridgeExchange.optDouble(request, key);
        if (value == null) {
            throw BridgeException.invalidField(key + " 가 필요합니다.");
        }
        return value;
    }

    private static JsonObject okWithStock(Stock stock, String message) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.add("stock", stockJson(stock));
        if (message != null) {
            result.addProperty("message", message);
        }
        return result;
    }

    /** api-bridge.md §4.1 주식 모델. 메인 스레드에서 호출. */
    static JsonObject stockJson(Stock stock) {
        JsonObject json = new JsonObject();
        json.addProperty("id", stock.getId());
        json.addProperty("name", stock.getName());
        json.addProperty("icon_material", stock.getMaterial().name());
        json.addProperty("price", stock.getPrice());
        json.addProperty("previous_price", stock.getPreviousPrice());
        json.addProperty("change_percent", StockRules.round2(stock.changePercentFromPrevious()));
        json.addProperty("min_price", stock.getMinPrice());
        json.addProperty("max_price", stock.getMaxPrice());
        json.addProperty("change_unit", stock.getChangeUnit());
        json.addProperty("max_change_percent", stock.getMaxChangePercent());
        json.addProperty("custom", stock.isCustom());
        json.addProperty("business", stock.getBusinessType());
        json.addProperty("halted", stock.isHalted());
        json.addProperty("revision", stock.getRevision());
        return json;
    }
}
