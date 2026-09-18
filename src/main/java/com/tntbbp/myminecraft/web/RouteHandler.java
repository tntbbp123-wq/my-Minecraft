package com.tntbbp.myminecraft.web;

/**
 * 통로 엔드포인트 하나를 처리한다. HTTP 스레드에서 불리므로 Bukkit 상태는
 * {@link WebBridge#callSync}로 메인 스레드에서 만져야 한다.
 * 에러는 {@link BridgeException}을 던지면 된다. 멱등 처리·인증·본문 크기 제한은 통로가 앞에서 한다.
 */
@FunctionalInterface
public interface RouteHandler {

    BridgeResponse handle(BridgeExchange exchange) throws Exception;
}
