package com.tntbbp.myminecraft.web.handler;

import com.tntbbp.myminecraft.web.BridgeException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerHelpersTest {

    @Test
    void cursorRoundTrip() throws BridgeException {
        String uuid = UUID.randomUUID().toString();
        String cursor = PlayerHandler.encodeCursor(uuid);
        assertFalse(cursor.contains("="), "URL에 그대로 넣을 수 있게 패딩 없이");
        assertEquals(uuid, PlayerHandler.decodeCursor(cursor));
        assertNull(PlayerHandler.decodeCursor(null));
        assertNull(PlayerHandler.decodeCursor(""));
    }

    @Test
    void badCursorIsBadRequest() {
        assertEquals(400, assertThrows(BridgeException.class, () -> PlayerHandler.decodeCursor("%%%")).status());
        assertEquals(400, assertThrows(BridgeException.class,
                () -> PlayerHandler.decodeCursor("eyJvIjoyMDB9")).status()); // {"o":200} — 형식 불일치
    }

    @Test
    void limitDefaultsAndClamps() throws BridgeException {
        assertEquals(200, PlayerHandler.parseLimit(null));
        assertEquals(50, PlayerHandler.parseLimit("50"));
        assertEquals(1000, PlayerHandler.parseLimit("5000"));
        assertThrows(BridgeException.class, () -> PlayerHandler.parseLimit("0"));
        assertThrows(BridgeException.class, () -> PlayerHandler.parseLimit("abc"));
    }

    @Test
    void parseUuid() throws BridgeException {
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, PlayerHandler.parseUuid(uuid.toString()));
        assertEquals(400, assertThrows(BridgeException.class, () -> PlayerHandler.parseUuid("nope")).status());
    }

    @Test
    void countdownAnnouncementsAndFormatting() {
        assertTrue(ServerHandler.isAnnouncePoint(60));
        assertTrue(ServerHandler.isAnnouncePoint(30));
        assertTrue(ServerHandler.isAnnouncePoint(10));
        assertTrue(ServerHandler.isAnnouncePoint(1));
        assertFalse(ServerHandler.isAnnouncePoint(59));
        assertFalse(ServerHandler.isAnnouncePoint(0));
        assertEquals("1분 30초", ServerHandler.formatSeconds(90));
        assertEquals("1분", ServerHandler.formatSeconds(60));
        assertEquals("5초", ServerHandler.formatSeconds(5));
        assertEquals("재시작", ServerHandler.actionLabel("restart"));
        assertEquals("종료", ServerHandler.actionLabel("stop"));
    }
}
