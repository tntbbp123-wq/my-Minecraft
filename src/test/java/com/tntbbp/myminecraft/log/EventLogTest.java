package com.tntbbp.myminecraft.log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogTest {

    private static final long NOW = Instant.parse("2026-09-18T12:00:00Z").toEpochMilli();
    private static final Logger LOGGER = Logger.getLogger("EventLogTest");

    @TempDir
    Path dir;

    @Test
    void serializeUsesEnvelopeOrderAndKeepsKorean() {
        JsonObject data = new JsonObject();
        data.addProperty("uuid", "u-1");
        data.addProperty("message", "안녕 <b>\"hi\"</b>\n둘째 줄");
        String line = EventLog.serialize(7, 1000L, "chat", "Gonkor1018", data);
        assertEquals("{\"v\":1,\"ts\":1000,\"seq\":7,\"type\":\"chat\",\"actor\":\"Gonkor1018\","
                + "\"data\":{\"uuid\":\"u-1\",\"message\":\"안녕 <b>\\\"hi\\\"</b>\\n둘째 줄\"}}", line);
        assertFalse(line.contains("\n"), "한 이벤트는 한 줄이어야 한다");
    }

    @Test
    void serializeDefaultsActorAndData() {
        String line = EventLog.serialize(1, 5L, "log_dropped", null, null);
        assertEquals("{\"v\":1,\"ts\":5,\"seq\":1,\"type\":\"log_dropped\",\"actor\":\"system\",\"data\":{}}", line);
    }

    @Test
    void serializeKeepsNullFields() {
        JsonObject data = new JsonObject();
        data.addProperty("killer", (String) null);
        String line = EventLog.serialize(1, 5L, "death", "a", data);
        assertTrue(line.contains("\"killer\":null"));
    }

    @Test
    void writesPerChannelFilesWithMonotonicSeq() throws IOException {
        EventLog log = new EventLog(dir.toFile(), 1000, 90, LOGGER, () -> NOW);
        log.start();
        for (int i = 0; i < 5; i++) {
            JsonObject data = new JsonObject();
            data.addProperty("i", i);
            log.write(EventLog.Channel.ACTIVITY, "join", "p" + i, data);
        }
        log.write(EventLog.Channel.TRADE, "coin_buy", "p", new JsonObject());
        log.write(EventLog.Channel.ADMIN, "server_save", "admin", new JsonObject());
        log.flush();

        List<String> activity = read(EventLog.fileName(EventLog.Channel.ACTIVITY, LocalDate.of(2026, 9, 18)));
        assertEquals(5, activity.size());
        long previous = 0;
        for (int i = 0; i < activity.size(); i++) {
            JsonObject event = JsonParser.parseString(activity.get(i)).getAsJsonObject();
            assertEquals(1, event.get("v").getAsInt());
            assertEquals(NOW, event.get("ts").getAsLong());
            assertEquals("join", event.get("type").getAsString());
            assertEquals(i, event.getAsJsonObject("data").get("i").getAsInt());
            long seq = event.get("seq").getAsLong();
            assertTrue(seq > previous);
            previous = seq;
        }
        assertEquals(1, read("trade-2026-09-18.jsonl").size());
        assertEquals(1, read("admin-2026-09-18.jsonl").size());
    }

    @Test
    void dropsWhenQueueFullAndReportsLogDropped() throws IOException {
        // 쓰기 스레드를 돌리지 않으면 큐가 비워지지 않는다 → 상한(최소 100)을 넘는 것은 버려진다
        EventLog log = new EventLog(dir.toFile(), 100, 90, LOGGER, () -> NOW);
        for (int i = 0; i < 105; i++) {
            log.write(EventLog.Channel.ACTIVITY, "chat", "p", new JsonObject());
        }
        assertEquals(5, log.droppedCount());
        log.flush();

        assertEquals(100, read("activity-2026-09-18.jsonl").size());
        List<String> admin = read("admin-2026-09-18.jsonl");
        assertEquals(1, admin.size());
        JsonObject dropped = JsonParser.parseString(admin.get(0)).getAsJsonObject();
        assertEquals("log_dropped", dropped.get("type").getAsString());
        assertEquals("system", dropped.get("actor").getAsString());
        assertEquals("activity", dropped.getAsJsonObject("data").get("channel").getAsString());
        assertEquals(5, dropped.getAsJsonObject("data").get("count").getAsLong());
    }

    @Test
    void ignoresWritesAfterFlush() throws IOException {
        EventLog log = new EventLog(dir.toFile(), 100, 90, LOGGER, () -> NOW);
        log.write(EventLog.Channel.ACTIVITY, "join", "a", new JsonObject());
        log.flush();
        log.write(EventLog.Channel.ACTIVITY, "join", "b", new JsonObject());
        assertEquals(1, read("activity-2026-09-18.jsonl").size());
    }

    @Test
    void rollsOverToNewFileWhenUtcDateChanges() throws IOException {
        AtomicLong clock = new AtomicLong(Instant.parse("2026-09-18T23:59:59Z").toEpochMilli());
        EventLog log = new EventLog(dir.toFile(), 100, 90, LOGGER, clock::get);
        log.write(EventLog.Channel.ACTIVITY, "join", "a", new JsonObject());
        log.flush();
        clock.set(Instant.parse("2026-09-19T00:00:01Z").toEpochMilli());
        EventLog next = new EventLog(dir.toFile(), 100, 90, LOGGER, clock::get);
        next.write(EventLog.Channel.ACTIVITY, "join", "b", new JsonObject());
        next.flush();
        assertEquals(1, read("activity-2026-09-18.jsonl").size());
        assertEquals(1, read("activity-2026-09-19.jsonl").size());
    }

    @Test
    void insertsNewlineBeforeAppendingToFileWithoutTrailingNewline() throws IOException {
        // 비정상 종료로 마지막 줄이 개행 없이 끊긴 상황을 흉내낸다.
        Path file = dir.resolve("activity-2026-09-18.jsonl");
        Files.writeString(file, "{\"v\":1,\"ts\":1,\"seq\":1,\"type\":\"join\",\"actor\":\"a\",\"data\":{}}");
        assertTrue(EventLog.fileEndsWithoutNewline(file.toFile()));

        EventLog log = new EventLog(dir.toFile(), 100, 90, LOGGER, () -> NOW);
        log.write(EventLog.Channel.ACTIVITY, "join", "b", new JsonObject());
        log.flush();

        List<String> lines = read("activity-2026-09-18.jsonl");
        assertEquals(2, lines.size());
        assertEquals("a", JsonParser.parseString(lines.get(0)).getAsJsonObject().get("actor").getAsString());
        assertEquals("b", JsonParser.parseString(lines.get(1)).getAsJsonObject().get("actor").getAsString());
    }

    @Test
    void doesNotInsertNewlineWhenFileAlreadyEndsWithOne() throws IOException {
        Path file = dir.resolve("activity-2026-09-18.jsonl");
        Files.writeString(file, "{\"v\":1,\"ts\":1,\"seq\":1,\"type\":\"join\",\"actor\":\"a\",\"data\":{}}\n");
        assertFalse(EventLog.fileEndsWithoutNewline(file.toFile()));

        EventLog log = new EventLog(dir.toFile(), 100, 90, LOGGER, () -> NOW);
        log.write(EventLog.Channel.ACTIVITY, "join", "b", new JsonObject());
        log.flush();

        assertEquals(2, read("activity-2026-09-18.jsonl").size());
    }

    @Test
    void fileEndsWithoutNewlineIsFalseForMissingOrEmptyFile() {
        assertFalse(EventLog.fileEndsWithoutNewline(dir.resolve("no-such-file.jsonl").toFile()));
    }

    @Test
    void retentionOnlyMatchesLogFiles() {
        LocalDate today = LocalDate.of(2026, 9, 18);
        assertTrue(EventLog.isExpired("activity-2026-06-19.jsonl", today, 90));
        assertFalse(EventLog.isExpired("activity-2026-06-20.jsonl", today, 90));
        assertTrue(EventLog.isExpired("trade-2025-01-01.jsonl", today, 90));
        assertFalse(EventLog.isExpired("admin-2025-01-01.jsonl", today, 0));
        assertFalse(EventLog.isExpired("economy.yml", today, 90));
        assertFalse(EventLog.isExpired("activity-2025-13-40.jsonl", today, 90));
        assertFalse(EventLog.isExpired("other-2025-01-01.jsonl", today, 90));
    }

    @Test
    void startDeletesExpiredFiles() throws IOException {
        Path old = dir.resolve("activity-2020-01-01.jsonl");
        Path keep = dir.resolve("notes-" + UUID.randomUUID() + ".txt");
        Files.writeString(old, "{}\n");
        Files.writeString(keep, "x");
        EventLog log = new EventLog(dir.toFile(), 100, 90, LOGGER, () -> NOW);
        log.start();
        log.flush();
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(keep));
    }

    private List<String> read(String name) throws IOException {
        Path file = dir.resolve(name);
        if (!Files.exists(file)) {
            return List.of();
        }
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> !line.isEmpty()).toList();
    }
}
