package com.tntbbp.myminecraft.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRunnerTest {

    @Test
    void blocksReloadFamily() {
        assertTrue(CommandRunner.isBlocked("reload"));
        assertTrue(CommandRunner.isBlocked("reload confirm"));
        assertTrue(CommandRunner.isBlocked("  /RELOAD   confirm "));
        assertTrue(CommandRunner.isBlocked("rl"));
        assertTrue(CommandRunner.isBlocked("/rl"));
        assertTrue(CommandRunner.isBlocked("bukkit:reload"));
        assertTrue(CommandRunner.isBlocked("bukkit:rl confirm"));
    }

    @Test
    void allowsOtherCommands() {
        assertFalse(CommandRunner.isBlocked("time set day"));
        assertFalse(CommandRunner.isBlocked("reloadx"));
        assertFalse(CommandRunner.isBlocked("say reload"));
        assertFalse(CommandRunner.isBlocked("minecraft:give Gonk diamond"));
        assertFalse(CommandRunner.isBlocked(""));
    }

    @Test
    void normalizeStripsSlashAndSpaces() {
        assertEquals("time set day", CommandRunner.normalize("  /time set day  "));
        assertEquals("say hi", CommandRunner.normalize("//say hi"));
        assertEquals("", CommandRunner.normalize(null));
    }

    @Test
    void detectsSensitiveOutput() {
        assertTrue(CommandRunner.isSensitiveCommand("토큰암호화 discord"));
        assertTrue(CommandRunner.isSensitiveCommand("/myminecraft:토큰암호화 ai"));
        assertFalse(CommandRunner.isSensitiveCommand("time query daytime"));

        assertTrue(CommandRunner.looksSensitive("enc:abc", true));
        assertFalse(CommandRunner.looksSensitive("enc:abc", false));
        assertTrue(CommandRunner.looksSensitive("값: enc:QUJDREVGR0hJSktMTU5PUFFSU1RVVldY", false));
        assertFalse(CommandRunner.looksSensitive("The time is 1000", true));
    }

    @Test
    void executionMasksSensitiveOutputAndDropsOriginal() {
        CommandRunner.Execution execution = new CommandRunner.Execution("id", "토큰암호화 discord", 0L);
        execution.appendText("§a아래 값을 config.yml에 붙여넣으세요.", 1L);
        execution.appendText("§fenc:c2VjcmV0LXZhbHVl", 2L);
        execution.appendText("이후 줄", 3L);
        execution.markDispatched(4L);
        assertEquals(List.of(CommandRunner.SENSITIVE_PLACEHOLDER), execution.output());
        assertEquals(CommandRunner.STATUS_DONE, execution.status(5L));
    }

    @Test
    void executionStripsColorsAndSplitsLines() {
        CommandRunner.Execution execution = new CommandRunner.Execution("id", "time query daytime", 0L);
        execution.appendText("§6The time is §e1000\nsecond line\n", 1L);
        execution.markDispatched(2L);
        assertEquals(List.of("The time is 1000", "second line"), execution.output());
        assertEquals(CommandRunner.STATUS_DONE, execution.status(3L));
    }

    @Test
    void executionWithoutImmediateOutputIsRunningUntilSettled() {
        CommandRunner.Execution execution = new CommandRunner.Execution("id", "say hi", 0L);
        execution.markDispatched(1000L);
        assertEquals(CommandRunner.STATUS_RUNNING, execution.status(1000L));
        // 늦게 온 출력은 쌓이고, 마지막 출력 후 일정 시간이 지나면 done
        execution.appendText("late output", 2000L);
        assertEquals(List.of("late output"), execution.output());
        assertEquals(CommandRunner.STATUS_RUNNING, execution.status(2000L + CommandRunner.SETTLE_MILLIS - 1));
        assertEquals(CommandRunner.STATUS_DONE, execution.status(2000L + CommandRunner.SETTLE_MILLIS));
        assertEquals(2000L, execution.updated());
    }
}
