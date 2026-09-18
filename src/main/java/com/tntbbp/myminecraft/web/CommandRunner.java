package com.tntbbp.myminecraft.web;

import com.tntbbp.myminecraft.log.LogRedaction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 웹에서 보낸 명령을 콘솔급 권한으로 실행하고 출력을 평문으로 캡처한다 (api-bridge.md §2.8~2.10).
 *
 * <p>{@link #execute}와 {@link #complete}는 반드시 메인 스레드에서 부른다. 실행마다
 * {@code Bukkit.createCommandSender}로 전용 sender를 만들어 그 sender로 오는 메시지만 모은다.
 * 명령이 끝난 뒤 늦게 오는 출력도 10분 동안 같은 실행 기록에 쌓이고 {@link #get}으로 조회할 수 있다.
 *
 * <p>{@code reload}/{@code rl}은 항상 막는다. {@code 토큰암호화}처럼 {@code enc:} 값이 출력되면
 * 출력 전체를 {@value #SENSITIVE_PLACEHOLDER}로 바꾸고 원문은 보관하지 않는다.
 */
public class CommandRunner {

    public static final String STATUS_DONE = "done";
    public static final String STATUS_RUNNING = "running";
    public static final String SENSITIVE_PLACEHOLDER = "<민감 출력 생략>";

    /** 실행 결과 보관 시간. */
    static final long RETENTION_MILLIS = 10 * 60 * 1000L;
    /** 즉시 출력이 없던 명령은 마지막 출력 후 이 시간 동안 더 오는 게 없으면 done으로 본다. */
    static final long SETTLE_MILLIS = 3000L;
    private static final int MAX_LINES = 500;
    private static final int MAX_SUGGESTIONS = 200;

    private static final Pattern LEGACY_CODES = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern ENC_VALUE = Pattern.compile("enc:[A-Za-z0-9+/=]{16,}");
    private static final List<String> BLOCKED_LABELS = List.of("reload", "rl");
    private static final String SENSITIVE_COMMAND = "토큰암호화";

    /** 명령 한 번의 실행 기록. 출력은 다른 스레드에서도 붙을 수 있어 동기화한다. */
    public static final class Execution {
        private final String id;
        private final String command;
        private final boolean sensitiveCommand;
        private final long createdAt;
        private final List<String> lines = new ArrayList<>();
        private boolean sensitive;
        private boolean truncated;
        private boolean outputAtDispatch;
        private long dispatchedAt;
        private long updated;

        Execution(String id, String command, long createdAt) {
            this.id = id;
            this.command = command;
            this.sensitiveCommand = isSensitiveCommand(command);
            this.createdAt = createdAt;
            this.updated = createdAt;
            this.dispatchedAt = createdAt;
        }

        public String id() {
            return id;
        }

        /** 실행한 명령(앞의 / 제거, 가림 규칙 적용 전). 로그에 남길 때는 가림 규칙을 적용할 것. */
        public String command() {
            return command;
        }

        public long createdAt() {
            return createdAt;
        }

        public synchronized long updated() {
            return updated;
        }

        /** 캡처한 출력(민감 출력이면 안내 문구 한 줄). */
        public synchronized List<String> output() {
            if (sensitive) {
                return List.of(SENSITIVE_PLACEHOLDER);
            }
            return List.copyOf(lines);
        }

        /** done | running. 즉시 출력이 있었으면 done, 없었으면 마지막 활동 후 {@link #SETTLE_MILLIS}가 지나면 done. */
        public synchronized String status(long now) {
            if (outputAtDispatch) {
                return STATUS_DONE;
            }
            return now - Math.max(dispatchedAt, updated) < SETTLE_MILLIS ? STATUS_RUNNING : STATUS_DONE;
        }

        synchronized void appendText(String text, long now) {
            if (text == null) {
                return;
            }
            updated = now;
            if (sensitive) {
                return;
            }
            for (String line : text.split("\\r?\\n", -1)) {
                String cleaned = stripLegacyCodes(line);
                if (looksSensitive(cleaned, sensitiveCommand)) {
                    sensitive = true;
                    lines.clear();
                    return;
                }
                if (lines.size() >= MAX_LINES) {
                    if (!truncated) {
                        truncated = true;
                        lines.add("... (출력이 너무 길어 나머지는 생략)");
                    }
                    return;
                }
                lines.add(cleaned);
            }
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
        }

        synchronized void markDispatched(long now) {
            dispatchedAt = now;
            outputAtDispatch = sensitive || !lines.isEmpty();
        }
    }

    private final Map<String, Execution> executions = new ConcurrentHashMap<>();
    private CommandSender completionSender;

    /**
     * 명령을 실행한다. <b>메인 스레드 전용.</b> {@code command}는 앞의 {@code /} 없이(있어도 뗀다).
     * 막는 명령인지는 호출 전에 {@link #isBlocked}로 확인한다.
     */
    public Execution execute(String command) {
        purgeExpired();
        String normalized = normalize(command);
        long now = System.currentTimeMillis();
        Execution execution = new Execution(UUID.randomUUID().toString(), normalized, now);
        executions.put(execution.id(), execution);

        CommandSender sender = Bukkit.createCommandSender(
                component -> execution.appendText(plain(component), System.currentTimeMillis()));
        boolean handled;
        try {
            handled = Bukkit.dispatchCommand(sender, normalized);
        } catch (CommandException e) {
            execution.appendText("명령 실행 중 오류가 발생했습니다: " + rootMessage(e), System.currentTimeMillis());
            handled = true;
        }
        if (!handled && execution.output().isEmpty()) {
            execution.appendText("알 수 없는 명령이거나 실행에 실패했습니다.", System.currentTimeMillis());
        }
        execution.markDispatched(System.currentTimeMillis());
        return execution;
    }

    /** 자동완성 제안. <b>메인 스레드 전용.</b> {@code line}은 커서까지의 전체 명령 줄(앞의 / 없이). */
    public List<String> complete(String line) {
        if (completionSender == null) {
            completionSender = Bukkit.createCommandSender(component -> {
            });
        }
        String input = line == null ? "" : line;
        while (input.startsWith("/")) {
            input = input.substring(1);
        }
        List<String> suggestions;
        try {
            suggestions = Bukkit.getCommandMap().tabComplete(completionSender, input);
        } catch (RuntimeException e) {
            return List.of();
        }
        if (suggestions == null) {
            return List.of();
        }
        return suggestions.size() > MAX_SUGGESTIONS
                ? List.copyOf(suggestions.subList(0, MAX_SUGGESTIONS))
                : List.copyOf(suggestions);
    }

    /** 실행 기록 조회. 없거나 10분이 지났으면 null. 아무 스레드에서나 호출 가능. */
    public Execution get(String id) {
        purgeExpired();
        return id == null ? null : executions.get(id);
    }

    private void purgeExpired() {
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        Iterator<Execution> iterator = executions.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().createdAt() < cutoff) {
                iterator.remove();
            }
        }
    }

    // ---------------------------------------------------------------- 순수 헬퍼

    /** 앞뒤 공백과 앞의 {@code /}를 뗀다. */
    public static String normalize(String command) {
        if (command == null) {
            return "";
        }
        String value = command.strip();
        while (value.startsWith("/")) {
            value = value.substring(1).stripLeading();
        }
        return value;
    }

    /**
     * 통로가 항상 막는 명령인지: 정규화 후 첫 토큰이 {@code reload}/{@code rl} (예: {@code reload confirm},
     * {@code bukkit:rl}). {@code execute ... run <명령> ...} 형태면 문자열 안의 모든 {@code run} 토큰 바로 다음
     * 라벨도 같은 기준으로 검사한다(예: {@code execute run reload confirm}, {@code execute as @a run rl},
     * 중첩된 {@code execute ... run execute ... run reload}도 모든 run 뒤를 보므로 막힌다).
     */
    public static boolean isBlocked(String command) {
        String normalized = normalize(command);
        if (BLOCKED_LABELS.contains(LogRedaction.commandLabel(normalized))) {
            return true;
        }
        for (int index : LogRedaction.runLabelIndexes(normalized)) {
            if (BLOCKED_LABELS.contains(LogRedaction.commandLabel(normalized.substring(index)))) {
                return true;
            }
        }
        return false;
    }

    /** 출력에 비밀값이 나올 수 있는 명령인지(토큰암호화). */
    public static boolean isSensitiveCommand(String command) {
        return SENSITIVE_COMMAND.equals(LogRedaction.commandLabel(command));
    }

    /** 이 출력 줄을 가려야 하는지. 토큰암호화면 {@code enc:}가 보이기만 해도, 아니면 {@code enc:<긴 값>} 형태일 때. */
    public static boolean looksSensitive(String line, boolean sensitiveCommand) {
        if (line == null) {
            return false;
        }
        if (sensitiveCommand && line.contains("enc:")) {
            return true;
        }
        return ENC_VALUE.matcher(line).find();
    }

    public static String stripLegacyCodes(String text) {
        return text == null ? "" : LEGACY_CODES.matcher(text).replaceAll("");
    }

    private static String plain(Component component) {
        if (component == null) {
            return "";
        }
        try {
            return PlainTextComponentSerializer.plainText().serialize(component);
        } catch (RuntimeException e) {
            return String.valueOf(component);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }
}
