package com.tntbbp.myminecraft.web.handler;

import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.WebBridge;

/**
 * 우편 API (api-bridge.md §3). <b>P1에서는 스텁</b> — 라우트만 {@link WebBridge}에 등록돼 있고
 * 모두 {@code 501 {"error":{"code":"not_found"}}}를 돌려준다. <b>P2가 이 파일의 본문만 채운다</b>
 * (WebBridge 라우트 등록은 고치지 않는다).
 *
 * <p>구현 메모(P2):
 * <ul>
 *   <li>쓰기 라우트는 통로가 이미 X-Request-Id 확인·멱등 재생·처리 중 409를 해 준다. 핸들러는 결과만 돌려주면 된다.</li>
 *   <li>Bukkit/매니저 접근은 {@code bridge.callSync(() -> …)}(메인 스레드). 에러는 {@code BridgeException} 던지기.</li>
 *   <li>로그: {@code bridge.tradeLogger().adminMailSend(…)}, {@code bridge.adminLog().mailSend/mailRecall(…)}.</li>
 *   <li>요청 본문 {@code exchange.json()}, 작성자 {@code exchange.actor()}, 경로 변수 {@code exchange.pathParam("uuid")}.</li>
 * </ul>
 */
public class MailHandler {

    private final WebBridge bridge;

    public MailHandler(WebBridge bridge) {
        this.bridge = bridge;
    }

    /** {@code POST /mail} (쓰기) — 관리자 우편 발송. */
    public BridgeResponse send(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code GET /players/{uuid}/mail} — 우편함 조회(오프라인 OK). */
    public BridgeResponse list(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }

    /** {@code POST /players/{uuid}/mail/{id}/recall} (쓰기) — 미수령 우편 회수. */
    public BridgeResponse recall(BridgeExchange exchange) throws Exception {
        return BridgeResponse.notImplemented();
    }
}
