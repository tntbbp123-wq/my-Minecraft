package com.tntbbp.myminecraft.web.handler;

import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.WebBridge;

/**
 * 주식 관리 API (api-bridge.md §4.1~4.8). <b>P1에서는 스텁</b> — 라우트만 {@link WebBridge}에 등록돼 있고
 * 모두 {@code 501 {"error":{"code":"not_found"}}}를 돌려준다. <b>P3가 이 파일의 본문만 채운다</b>
 * (WebBridge 라우트 등록은 고치지 않는다).
 *
 * <p>구현 메모(P3):
 * <ul>
 *   <li>쓰기 라우트는 통로가 이미 X-Request-Id 확인·멱등 재생·처리 중 409를 해 준다.</li>
 *   <li>{@code PATCH /stocks/{id}}의 revision은 {@code exchange.header("If-Match")}로 읽는다(불일치 → 409 stock_revision_conflict,
 *       {@code new BridgeException(409, "stock_revision_conflict", …)}).</li>
 *   <li>로그: {@code bridge.adminLog().stockCreate/stockUpdate/stockPrice/stockHalt/stockResume/stockGrant},
 *       {@code bridge.tradeLogger().adminStockGrant}.</li>
 *   <li>경로 변수 {@code exchange.pathParam("id")}는 이미 URL 디코딩돼 있다(예: {@code custom_금광}).</li>
 * </ul>
 */
public class StockHandler {

    private final WebBridge bridge;

    public StockHandler(WebBridge bridge) {
        this.bridge = bridge;
    }

    /** {@code GET /stocks}. */
    public BridgeResponse list(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code GET /stocks/{id}}. */
    public BridgeResponse get(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code GET /stocks/{id}/holders}. */
    public BridgeResponse holders(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code POST /stocks} (쓰기) — 종목 추가. */
    public BridgeResponse create(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code PATCH /stocks/{id}} (쓰기, If-Match: revision) — 종목 수정. */
    public BridgeResponse update(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code POST /stocks/{id}/price} (쓰기) — 가격 직접 설정. */
    public BridgeResponse setPrice(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code POST /stocks/{id}/halt} (쓰기) — 거래 중지. */
    public BridgeResponse halt(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code POST /stocks/{id}/resume} (쓰기) — 거래 재개. */
    public BridgeResponse resume(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code POST /stocks/{id}/grant} (쓰기) — 주식 지급(오프라인 OK). */
    public BridgeResponse grant(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }
}
