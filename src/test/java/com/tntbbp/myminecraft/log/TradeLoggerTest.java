package com.tntbbp.myminecraft.log;

import com.google.gson.JsonArray;
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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 웹 관리자가 필드 이름으로 읽으므로, 새 거래 종류의 필드가 약속대로 나가는지 본다. */
class TradeLoggerTest {

    private static final long NOW = Instant.parse("2026-09-23T12:00:00Z").toEpochMilli();
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    Path dir;

    private JsonObject writeOne(java.util.function.Consumer<TradeLogger> action) throws IOException {
        EventLog log = new EventLog(dir.toFile(), 1000, 90, Logger.getLogger("TradeLoggerTest"), () -> NOW);
        log.start();
        action.accept(new TradeLogger(log));
        log.flush();
        Path file = dir.resolve(EventLog.fileName(EventLog.Channel.TRADE, LocalDate.of(2026, 9, 23)));
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        return JsonParser.parseString(lines.get(0)).getAsJsonObject();
    }

    @Test
    void 강화_비용() throws IOException {
        JsonObject event = writeOne(logger -> logger.enhanceCost(
                PLAYER, "철수", "레바테인", 12, 4, true, false));

        assertEquals("enhance_cost", event.get("type").getAsString());
        assertEquals("철수", event.get("actor").getAsString());
        JsonObject data = event.getAsJsonObject("data");
        assertEquals(PLAYER.toString(), data.get("uuid").getAsString());
        assertEquals("철수", data.get("name").getAsString());
        assertEquals("레바테인", data.get("item_name").getAsString());
        assertEquals(12, data.get("from_level").getAsInt());
        assertEquals(false, data.has("amount"), "강화에는 G가 들지 않으므로 금액 필드가 없다");
        assertEquals(4, data.get("stones").getAsInt());
        assertTrue(data.get("used_scroll").getAsBoolean());
        assertEquals(false, data.get("success").getAsBoolean());
    }

    @Test
    void 일괄_약탈() throws IOException {
        JsonObject event = writeOne(logger -> logger.blackmarketLootAll(PLAYER, "영희", "world", 10, 64, -3,
                List.of(new TradeLogger.ItemCount("diamond", 5), new TradeLogger.ItemCount("100G 동전", 3))));

        assertEquals("blackmarket_loot_all", event.get("type").getAsString());
        JsonObject data = event.getAsJsonObject("data");
        assertEquals(PLAYER.toString(), data.get("uuid").getAsString());
        assertEquals("world", data.get("world").getAsString());
        assertEquals(10, data.get("x").getAsInt());
        assertEquals(64, data.get("y").getAsInt());
        assertEquals(-3, data.get("z").getAsInt());
        JsonArray items = data.getAsJsonArray("items");
        assertEquals(2, items.size());
        assertEquals("diamond", items.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(5, items.get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(8, data.get("count").getAsInt(), "모든 아이템 개수의 합");
    }
}
