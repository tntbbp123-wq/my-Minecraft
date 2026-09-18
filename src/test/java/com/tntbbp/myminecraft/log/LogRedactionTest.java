package com.tntbbp.myminecraft.log;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogRedactionTest {

    private static final List<String> LABELS = List.of("디스코드연동확인", "디스코드연동");

    @Test
    void redactsArgumentsOfListedCommand() {
        assertEquals("/디스코드연동확인 ***", LogRedaction.redactCommand("/디스코드연동확인 1234", LABELS));
        assertEquals("/디스코드연동 ***", LogRedaction.redactCommand("/디스코드연동 123456789012345678", LABELS));
    }

    @Test
    void redactsNamespacedAndConsoleForms() {
        assertEquals("/myminecraft:디스코드연동확인 ***",
                LogRedaction.redactCommand("/myminecraft:디스코드연동확인 1234", LABELS));
        // 콘솔 명령은 앞에 / 가 없다
        assertEquals("디스코드연동확인 ***", LogRedaction.redactCommand("디스코드연동확인 1234 extra", LABELS));
    }

    @Test
    void keepsOtherCommandsAndBareCommands() {
        assertEquals("/give Gonk diamond 3", LogRedaction.redactCommand("/give Gonk diamond 3", LABELS));
        assertEquals("/디스코드연동확인", LogRedaction.redactCommand("/디스코드연동확인", LABELS));
        assertEquals("/디스코드연동확인   ", LogRedaction.redactCommand("/디스코드연동확인   ", LABELS));
        // 첫 토큰이 정확히 같아야 한다 (접두어 일치는 가리지 않음)
        assertEquals("/디스코드연동확인해줘 1", LogRedaction.redactCommand("/디스코드연동확인해줘 1", LABELS));
    }

    @Test
    void emptyLabelsOrNullInput() {
        assertEquals("/디스코드연동확인 1234", LogRedaction.redactCommand("/디스코드연동확인 1234", List.of()));
        assertEquals("", LogRedaction.redactCommand(null, LABELS));
    }

    @Test
    void commandLabelNormalizes() {
        assertEquals("reload", LogRedaction.commandLabel("  /Bukkit:RELOAD confirm"));
        assertEquals("time", LogRedaction.commandLabel("time set day"));
        assertEquals("", LogRedaction.commandLabel("   "));
    }

    @Test
    void redactsArgumentsOfCommandBehindExecuteRun() {
        assertEquals("/execute as @a run 디스코드연동확인 ***",
                LogRedaction.redactCommand("/execute as @a run 디스코드연동확인 1234", LABELS));
        assertEquals("execute run 디스코드연동 ***",
                LogRedaction.redactCommand("execute run 디스코드연동 123456789012345678", LABELS));
        // 중첩된 execute도 모든 run 뒤를 보므로 가려진다
        assertEquals("execute run execute run 디스코드연동확인 ***",
                LogRedaction.redactCommand("execute run execute run 디스코드연동확인 1234", LABELS));
    }

    @Test
    void keepsCommandsUnrelatedToExecuteRun() {
        assertEquals("execute as @a run say hi", LogRedaction.redactCommand("execute as @a run say hi", LABELS));
        // execute로 시작하지 않으면 문자열 안의 run은 검사하지 않는다
        assertEquals("say run 디스코드연동확인 1234",
                LogRedaction.redactCommand("say run 디스코드연동확인 1234", LABELS));
    }

    @Test
    void runLabelIndexesFindsEveryLabelAfterRun() {
        String command = "execute run execute run reload confirm";
        assertEquals(List.of(indexOf(command, "execute", 1), indexOf(command, "reload", 0)),
                LogRedaction.runLabelIndexes(command));
        assertEquals(List.of(), LogRedaction.runLabelIndexes("say run reload"));
    }

    private static int indexOf(String text, String token, int occurrence) {
        int index = -1;
        for (int i = 0; i <= occurrence; i++) {
            index = text.indexOf(token, index + 1);
        }
        return index;
    }
}
