package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Gemini API(무료 티어)로 뉴스 기사 초안을 생성한다.
 * https://ai.google.dev/ 에서 무료로 API 키를 발급받아 config.yml의 ai.api-key에 넣으면 활성화된다.
 * 별도 라이브러리 의존성을 피하기 위해 JSON은 간단한 정규식으로 조립/파싱한다.
 */
public class GeminiNewsClient {

    private final MyMinecraftPlugin plugin;
    private final HttpClient httpClient;
    private static final Pattern TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    public GeminiNewsClient(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("ai.enabled", false)
                && !apiKey().isBlank();
    }

    private String apiKey() {
        return plugin.getConfig().getString("ai.api-key", "");
    }

    private String model() {
        return plugin.getConfig().getString("ai.model", "gemini-2.5-flash");
    }

    /** 주어진 주제로 짧은 한국어 뉴스 기사를 비동기로 생성한다. */
    public CompletableFuture<String> generateNewsArticle(String topic, boolean fake) {
        if (!isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "AI 뉴스 생성이 비활성화되어 있습니다. config.yml의 ai.enabled/ai.api-key를 확인하세요."));
        }

        String style = fake
                ? "그럴듯하지만 사실과 다른 낚시성 루머 기사"
                : "실제 있었던 것처럼 자연스러운 경제/사건 기사";
        String prompt = "마인크래프트 서버의 가상 주식 시장에 올라갈 짧은 한국어 뉴스 기사를 1~2문장으로 작성해줘. "
                + "주제: " + topic + ". 스타일: " + style + ". 따옴표나 접두사 없이 기사 본문만 출력해줘.";

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model()
                + ":generateContent?key=" + apiKey();
        String body = "{\"contents\":[{\"parts\":[{\"text\":\"" + escapeJson(prompt) + "\"}]}]}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Gemini API 오류 (HTTP " + response.statusCode() + "): " + response.body());
                    }
                    return extractText(response.body());
                });
    }

    private String extractText(String json) {
        Matcher matcher = TEXT_PATTERN.matcher(json);
        if (!matcher.find()) {
            throw new RuntimeException("Gemini 응답을 해석할 수 없습니다: " + json);
        }
        return unescapeJson(matcher.group(1)).trim();
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String unescapeJson(String text) {
        return text.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
