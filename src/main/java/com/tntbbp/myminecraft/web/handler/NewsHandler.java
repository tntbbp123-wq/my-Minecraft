package com.tntbbp.myminecraft.web.handler;

import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.WebBridge;

/**
 * 뉴스 관리 API (api-bridge.md §4.9~4.14). <b>P1에서는 스텁</b> — 라우트만 {@link WebBridge}에 등록돼 있고
 * 모두 {@code 501 {"error":{"code":"not_found"}}}를 돌려준다. <b>P3가 이 파일의 본문만 채운다</b>
 * (WebBridge 라우트 등록은 고치지 않는다).
 *
 * <p>구현 메모(P3):
 * <ul>
 *   <li>쓰기 라우트는 통로가 이미 X-Request-Id 확인·멱등 재생·처리 중 409를 해 준다.</li>
 *   <li>AI 초안: {@code GeminiNewsClient}의 CompletableFuture를 draft_id에 걸고(10분 보관) <b>submit 하지 않는다</b>.
 *       AI 비활성이면 {@code new BridgeException(503, "ai_unavailable", …)}.</li>
 *   <li>로그: {@code bridge.adminLog().newsCreate/newsEdit/newsCancel}(공개·반영은 NewsManager에서 newsReveal/newsApply).</li>
 *   <li>{@code GET /news/ai-draft/{id}}는 {@code /news/{id}}보다 우선 매칭된다(고정 조각이 더 많은 라우트 우선).</li>
 * </ul>
 */
public class NewsHandler {

    private final WebBridge bridge;

    public NewsHandler(WebBridge bridge) {
        this.bridge = bridge;
    }

    /** {@code GET /news?status=}. */
    public BridgeResponse list(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code POST /news} (쓰기) — 뉴스 작성(확정). */
    public BridgeResponse create(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code POST /news/ai-draft} (쓰기, 비동기) — AI 초안만 생성. */
    public BridgeResponse aiDraft(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code GET /news/ai-draft/{id}} — AI 초안 조회(폴링). */
    public BridgeResponse aiDraftStatus(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code PATCH /news/{id}} (쓰기) — 공개 전 수정. */
    public BridgeResponse update(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code POST /news/{id}/cancel} (쓰기) — 반영 전 취소. */
    public BridgeResponse cancel(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }
}
