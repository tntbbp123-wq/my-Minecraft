package com.tntbbp.myminecraft.log;

import java.util.Collection;
import java.util.Locale;

/**
 * 기록(JSONL)과 관리 로그에 남기기 전에 민감한 명령 인자를 가리는 순수 함수 모음.
 * 서버 없이 단위 테스트할 수 있도록 Bukkit에 의존하지 않는다.
 */
public final class LogRedaction {

    /** 가린 인자 자리에 들어가는 문자열. */
    public static final String MASK = "***";

    private LogRedaction() {
    }

    /**
     * 명령 줄의 첫 토큰을 비교용으로 정리한다. 앞의 {@code /}와 {@code 플러그인:} 접두어를 떼고 소문자로 바꾼다.
     * 예: {@code "/MyMinecraft:디스코드연동확인 1234"} → {@code "디스코드연동확인"}. 빈 줄이면 빈 문자열.
     */
    public static String commandLabel(String commandLine) {
        if (commandLine == null) {
            return "";
        }
        String trimmed = commandLine.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int space = indexOfWhitespace(trimmed);
        String token = space < 0 ? trimmed : trimmed.substring(0, space);
        int colon = token.lastIndexOf(':');
        if (colon >= 0) {
            token = token.substring(colon + 1);
        }
        return token.toLowerCase(Locale.ROOT);
    }

    /**
     * 첫 토큰이 가림 대상이면 인자 전체를 {@link #MASK}로 바꾼다. 입력한 첫 토큰(앞의 {@code /} 포함)은 그대로 둔다.
     * 예: {@code "/디스코드연동확인 1234"} → {@code "/디스코드연동확인 ***"}. 인자가 없거나 대상이 아니면 원문 그대로.
     */
    public static String redactCommand(String commandLine, Collection<String> redactLabels) {
        if (commandLine == null) {
            return "";
        }
        if (redactLabels == null || redactLabels.isEmpty()) {
            return commandLine;
        }
        String label = commandLabel(commandLine);
        if (label.isEmpty() || !containsIgnoreCase(redactLabels, label)) {
            return commandLine;
        }
        String trimmed = commandLine.strip();
        int space = indexOfWhitespace(trimmed);
        if (space < 0 || trimmed.substring(space).isBlank()) {
            return commandLine;
        }
        return trimmed.substring(0, space) + " " + MASK;
    }

    private static boolean containsIgnoreCase(Collection<String> values, String label) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = commandLabel(value);
            if (normalized.equals(label)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
