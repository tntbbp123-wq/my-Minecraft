package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.model.Stock;

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

    /** AI가 스스로 정한 뉴스 본문 + 등락 방향(+1/-1) + 변동폭(%). */
    public record NewsDraft(String content, int direction, double magnitudePercent) {
    }

    private final MyMinecraftPlugin plugin;
    private final HttpClient httpClient;
    private static final Pattern TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern DIRECTION_PATTERN = Pattern.compile("\"direction\"\\s*:\\s*\"(up|down|상승|하락)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAGNITUDE_PATTERN = Pattern.compile("\"magnitude\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");

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

    /** 주어진 주제로 짧은 한국어 뉴스 기사를 비동기로 생성한다 (등락/변동폭은 관리자가 별도로 정함). */
    public CompletableFuture<String> generateNewsArticle(String topic, boolean fake) {
        String style = styleFor(fake);
        String prompt = "마인크래프트 서버의 가상 주식 시장에 올라갈 짧은 한국어 뉴스 기사를 1~2문장으로 작성해줘. "
                + "주제: " + topic + ". 스타일: " + style + ". "
                + "중요: 이 기사는 사건/소식 자체만 전달해야 해. 주가가 오른다/내린다는 언급, "
                + "'상승'이나 '하락' 같은 단어, 구체적인 퍼센트(%)나 수치 전망은 절대 포함하지 마. "
                + "따옴표나 접두사 없이 기사 본문만 출력해줘.";
        return sendPrompt(prompt);
    }

    /**
     * 종목의 업종/하는 일에 맞는 뉴스를 AI가 작성하고, 등락 방향과 변동폭(%)까지 AI가 직접 정하게 한다.
     * topic이 비어있으면 AI가 업종에 맞는 그럴듯한 사건을 스스로 창작한다.
     */
    public CompletableFuture<NewsDraft> generateNewsWithImpact(Stock stock, String topic, boolean fake) {
        String style = styleFor(fake);
        String business = stock.getBusinessType().isBlank() ? "일반 기업" : stock.getBusinessType();
        String topicPart = topic.isBlank()
                ? "이 회사와 관련된 그럴듯한 사건을 하나 직접 창작해."
                : "이 회사와 관련된 다음 소식을 바탕으로 작성해: " + topic;

        String prompt = "마인크래프트 서버의 가상 주식 시장 뉴스를 작성해줘.\n"
                + "회사명: " + stock.getName() + "\n"
                + "업종/하는 일: " + business + "\n"
                + topicPart + "\n"
                + "스타일: " + style + "\n"
                + "이 사건이 이 회사 주가에 긍정적인지 부정적인지, 그 영향의 크기(1~10 사이 숫자, %)도 함께 판단해줘.\n"
                + "기사 본문에는 등락 방향이나 퍼센트, 구체적 수치 전망을 절대 언급하지 마 - 사건 자체만 서술해.\n"
                + "다른 텍스트 없이 아래 JSON 형식으로만 응답해:\n"
                + "{\"content\": \"기사 본문 1~2문장\", \"direction\": \"up 또는 down\", \"magnitude\": 숫자}";

        double minMagnitude = plugin.getConfig().getDouble("news.ai-min-magnitude-percent", 1.0);
        double maxMagnitude = plugin.getConfig().getDouble("news.ai-max-magnitude-percent", 10.0);

        return sendPrompt(prompt).thenApply(rawText -> {
            String json = stripCodeFence(rawText);

            Matcher contentMatcher = CONTENT_PATTERN.matcher(json);
            Matcher directionMatcher = DIRECTION_PATTERN.matcher(json);
            Matcher magnitudeMatcher = MAGNITUDE_PATTERN.matcher(json);
            if (!contentMatcher.find() || !directionMatcher.find() || !magnitudeMatcher.find()) {
                throw new RuntimeException("Gemini 응답에서 content/direction/magnitude를 찾을 수 없습니다: " + rawText);
            }

            String content = unescapeJson(contentMatcher.group(1)).trim();
            String directionText = directionMatcher.group(1).toLowerCase();
            int direction = (directionText.equals("up") || directionText.equals("상승")) ? 1 : -1;
            double magnitude = Double.parseDouble(magnitudeMatcher.group(1));
            magnitude = Math.max(minMagnitude, Math.min(maxMagnitude, Math.abs(magnitude)));

            return new NewsDraft(content, direction, magnitude);
        });
    }

    private String styleFor(boolean fake) {
        return fake
                ? "그럴듯하지만 사실과 다른 낚시성 루머 기사"
                : "실제 있었던 것처럼 자연스러운 경제/사건 기사";
    }

    private CompletableFuture<String> sendPrompt(String prompt) {
        if (!isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "AI 뉴스 생성이 비활성화되어 있습니다. config.yml의 ai.enabled/ai.api-key를 확인하세요."));
        }

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

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
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
