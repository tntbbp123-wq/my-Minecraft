package com.tntbbp.myminecraft.log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
     *
     * <p>{@code execute ... run <명령> ...} 형태면 {@link #runLabelIndexes}로 찾은 run 뒤 라벨도 같은 기준으로
     * 검사해, 가림 대상이면 그 라벨 뒤의 인자만 가린다(예: {@code "execute as @a run 디스코드연동확인 1234"} →
     * {@code "execute as @a run 디스코드연동확인 ***"}).
     */
    public static String redactCommand(String commandLine, Collection<String> redactLabels) {
        if (commandLine == null) {
            return "";
        }
        if (redactLabels == null || redactLabels.isEmpty()) {
            return commandLine;
        }
        String label = commandLabel(commandLine);
        if (!label.isEmpty() && containsIgnoreCase(redactLabels, label)) {
            String trimmed = commandLine.strip();
            int space = indexOfWhitespace(trimmed);
            if (space >= 0 && !trimmed.substring(space).isBlank()) {
                return trimmed.substring(0, space) + " " + MASK;
            }
            return commandLine;
        }
        for (int index : runLabelIndexes(commandLine)) {
            String runLabel = commandLabel(commandLine.substring(index));
            if (!runLabel.isEmpty() && containsIgnoreCase(redactLabels, runLabel)) {
                String masked = maskArgsFrom(commandLine, index);
                if (masked != null) {
                    return masked;
                }
            }
        }
        return commandLine;
    }

    /**
     * 명령의 첫 토큰이 {@code execute}(앞의 {@code /}와 네임스페이스를 뗀 소문자, 예: {@code minecraft:execute}도)이면,
     * 문자열 안의 모든 {@code run} 토큰 바로 다음 토큰이 시작하는 인덱스 목록을 {@code commandLine} 기준 절대 위치로
     * 돌려준다. 중첩된 {@code execute ... run execute ... run <명령>}도 모든 run 뒤를 보므로 자연히 처리된다.
     * 첫 토큰이 execute가 아니면 빈 리스트. 순수 함수.
     */
    public static List<Integer> runLabelIndexes(String commandLine) {
        if (commandLine == null) {
            return List.of();
        }
        List<int[]> tokens = tokenSpans(commandLine);
        if (tokens.isEmpty()) {
            return List.of();
        }
        int[] first = tokens.get(0);
        if (!"execute".equals(stripSlashesAndNamespace(commandLine.substring(first[0], first[1])))) {
            return List.of();
        }
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < tokens.size() - 1; i++) {
            int[] span = tokens.get(i);
            String word = commandLine.substring(span[0], span[1]);
            if (word.equalsIgnoreCase("run")) {
                indexes.add(tokens.get(i + 1)[0]);
            }
        }
        return indexes;
    }

    /** {@code labelStart}에서 시작하는 토큰 뒤에 인자가 있으면 그 인자를 {@link #MASK}로 바꾼다. 인자가 없으면 null. */
    private static String maskArgsFrom(String commandLine, int labelStart) {
        int end = labelStart;
        while (end < commandLine.length() && !Character.isWhitespace(commandLine.charAt(end))) {
            end++;
        }
        if (end >= commandLine.length() || commandLine.substring(end).isBlank()) {
            return null;
        }
        return commandLine.substring(0, end) + " " + MASK;
    }

    /** 공백으로 나눈 토큰들의 {@code [시작, 끝)} 구간 목록(왼쪽부터 순서대로). */
    private static List<int[]> tokenSpans(String text) {
        List<int[]> spans = new ArrayList<>();
        int i = 0;
        int length = text.length();
        while (i < length) {
            while (i < length && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < length && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i > start) {
                spans.add(new int[] {start, i});
            }
        }
        return spans;
    }

    /** 토큰 앞의 {@code /}(들)와 {@code 네임스페이스:} 접두어를 떼고 소문자로 바꾼다. */
    private static String stripSlashesAndNamespace(String token) {
        String value = token;
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        return value.toLowerCase(Locale.ROOT);
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
