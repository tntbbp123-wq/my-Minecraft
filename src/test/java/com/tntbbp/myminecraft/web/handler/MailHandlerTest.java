package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tntbbp.myminecraft.manager.mail.MailManager;
import com.tntbbp.myminecraft.model.Mail;
import com.tntbbp.myminecraft.model.MailAttachment;
import com.tntbbp.myminecraft.model.MailSenderType;
import com.tntbbp.myminecraft.web.BridgeException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailHandlerTest {

    private static final String UUID_1 = "11111111-1111-1111-1111-111111111111";
    private static final String UUID_2 = "22222222-2222-2222-2222-222222222222";

    private static JsonObject parse(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static JsonObject recipients(String mode) {
        JsonObject r = new JsonObject();
        r.addProperty("mode", mode);
        return r;
    }

    private static JsonObject recipientsUuids(String... uuids) {
        JsonObject r = recipients("uuids");
        JsonArray array = new JsonArray();
        for (String uuid : uuids) {
            array.add(uuid);
        }
        r.add("uuids", array);
        return r;
    }

    private static JsonObject request(JsonObject recipients, String title) {
        JsonObject json = new JsonObject();
        json.add("recipients", recipients);
        json.addProperty("title", title);
        return json;
    }

    private static JsonArray attachmentsArray(int count) {
        JsonArray array = new JsonArray();
        for (int i = 0; i < count; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("material", "STONE");
            item.addProperty("count", 1);
            array.add(item);
        }
        return array;
    }

    private static int statusOf(JsonObject json) {
        return assertThrows(BridgeException.class, () -> MailHandler.parseSendRequest(json)).status();
    }

    // ---------------------------------------------------------------- parseSendRequest: 정상 값

    @Test
    void parsesFullValidRequest() throws BridgeException {
        JsonObject json = parse("""
                {
                  "recipients": {"mode": "uuids", "uuids": ["11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222"]},
                  "title": "안내문",
                  "sender_name": "관리자",
                  "attachments": [
                    {"item_name": "강화석", "count": 3},
                    {"material": "DIAMOND", "count": 10}
                  ],
                  "attached_g": 5000,
                  "expires_days": 30
                }
                """);
        json.addProperty("body", "첫째줄\r\n둘째줄"); // 실제 CRLF 문자 — \n 정규화 확인용

        MailHandler.SendRequest request = MailHandler.parseSendRequest(json);

        assertEquals("uuids", request.mode());
        assertEquals(List.of(UUID.fromString(UUID_1), UUID.fromString(UUID_2)), request.uuids());
        assertEquals("안내문", request.title());
        assertEquals("첫째줄\n둘째줄", request.body());
        assertEquals("관리자", request.senderName());
        assertEquals(2, request.attachments().size());
        assertEquals(0, request.attachments().get(0).index());
        assertEquals("강화석", request.attachments().get(0).itemName());
        assertNull(request.attachments().get(0).material());
        assertEquals(3, request.attachments().get(0).count());
        assertEquals(1, request.attachments().get(1).index());
        assertEquals("DIAMOND", request.attachments().get(1).material());
        assertNull(request.attachments().get(1).itemName());
        assertEquals(10, request.attachments().get(1).count());
        assertEquals(5000L, request.attachedG());
        assertEquals(30, request.expiresDays());
    }

    @Test
    void defaultsWhenOptionalFieldsMissing() throws BridgeException {
        JsonObject json = request(recipientsUuids(UUID_1), "제목");

        MailHandler.SendRequest request = MailHandler.parseSendRequest(json);

        assertEquals("운영자", request.senderName());
        assertEquals("", request.body());
        assertEquals(0L, request.attachedG());
        assertNull(request.expiresDays());
        assertTrue(request.attachments().isEmpty());
    }

    @Test
    void modeAllAndOnlineHaveEmptyUuids() throws BridgeException {
        assertTrue(MailHandler.parseSendRequest(request(recipients("all"), "제목")).uuids().isEmpty());
        assertTrue(MailHandler.parseSendRequest(request(recipients("online"), "제목")).uuids().isEmpty());
    }

    // ---------------------------------------------------------------- recipients

    @Test
    void missingRecipientsIsBadRequest() {
        JsonObject json = new JsonObject();
        json.addProperty("title", "제목");
        assertEquals(400, statusOf(json));
    }

    @Test
    void recipientsNotObjectIsBadRequest() {
        JsonObject json = new JsonObject();
        json.addProperty("recipients", "foo");
        json.addProperty("title", "제목");
        assertEquals(400, statusOf(json));
    }

    @Test
    void unknownModeIsInvalidField() {
        assertEquals(422, statusOf(request(recipients("everyone"), "제목")));
    }

    @Test
    void uuidsMissingIsBadRequest() {
        assertEquals(400, statusOf(request(recipients("uuids"), "제목")));
    }

    @Test
    void uuidsEmptyIsInvalidField() {
        assertEquals(422, statusOf(request(recipientsUuids(), "제목")));
    }

    @Test
    void badUuidStringIsInvalidField() {
        assertEquals(422, statusOf(request(recipientsUuids("not-a-uuid"), "제목")));
    }

    @Test
    void uuidNotStringIsBadRequest() {
        JsonObject r = recipients("uuids");
        JsonArray array = new JsonArray();
        array.add(123);
        r.add("uuids", array);
        assertEquals(400, statusOf(request(r, "제목")));
    }

    // ---------------------------------------------------------------- title / body / sender_name

    @Test
    void missingTitleIsBadRequest() {
        JsonObject json = new JsonObject();
        json.add("recipients", recipientsUuids(UUID_1));
        assertEquals(400, statusOf(json));
    }

    @Test
    void blankTitleIsInvalidField() {
        assertEquals(422, statusOf(request(recipientsUuids(UUID_1), "   ")));
    }

    @Test
    void titleLengthBoundary() {
        String ok = "가".repeat(64);
        String tooLong = "가".repeat(65);
        assertDoesNotThrow(() -> MailHandler.parseSendRequest(request(recipientsUuids(UUID_1), ok)));
        assertEquals(422, statusOf(request(recipientsUuids(UUID_1), tooLong)));
    }

    @Test
    void titleControlCharIsInvalidField() {
        assertEquals(422, statusOf(request(recipientsUuids(UUID_1), "안내\t문")));
    }

    @Test
    void bodyLengthBoundaryAndNewlineAllowed() {
        JsonObject tooLong = request(recipientsUuids(UUID_1), "제목");
        tooLong.addProperty("body", "가".repeat(513));
        assertEquals(422, statusOf(tooLong));

        JsonObject withNewline = request(recipientsUuids(UUID_1), "제목");
        withNewline.addProperty("body", "첫줄\n둘째줄");
        assertDoesNotThrow(() -> MailHandler.parseSendRequest(withNewline));
    }

    @Test
    void senderNameLengthBoundary() {
        JsonObject blank = request(recipientsUuids(UUID_1), "제목");
        blank.addProperty("sender_name", "");
        assertEquals(422, statusOf(blank));

        JsonObject tooLong = request(recipientsUuids(UUID_1), "제목");
        tooLong.addProperty("sender_name", "가".repeat(33));
        assertEquals(422, statusOf(tooLong));
    }

    // ---------------------------------------------------------------- attachments

    @Test
    void attachmentsNotArrayIsBadRequest() {
        JsonObject json = request(recipientsUuids(UUID_1), "제목");
        json.addProperty("attachments", "foo");
        assertEquals(400, statusOf(json));
    }

    @Test
    void attachmentCountBoundary() {
        JsonObject json27 = request(recipientsUuids(UUID_1), "제목");
        json27.add("attachments", attachmentsArray(27));
        assertDoesNotThrow(() -> MailHandler.parseSendRequest(json27));

        JsonObject json28 = request(recipientsUuids(UUID_1), "제목");
        json28.add("attachments", attachmentsArray(28));
        assertEquals(422, statusOf(json28));
    }

    @Test
    void attachmentBothFieldsIsInvalidField() {
        JsonObject item = new JsonObject();
        item.addProperty("item_name", "강화석");
        item.addProperty("material", "DIAMOND");
        item.addProperty("count", 1);
        JsonArray array = new JsonArray();
        array.add(item);
        JsonObject json = request(recipientsUuids(UUID_1), "제목");
        json.add("attachments", array);
        assertEquals(422, statusOf(json));
    }

    @Test
    void attachmentNeitherFieldIsInvalidField() {
        JsonObject item = new JsonObject();
        item.addProperty("count", 1);
        JsonArray array = new JsonArray();
        array.add(item);
        JsonObject json = request(recipientsUuids(UUID_1), "제목");
        json.add("attachments", array);
        assertEquals(422, statusOf(json));
    }

    @Test
    void attachmentCountMissingIsBadRequest() {
        JsonObject item = new JsonObject();
        item.addProperty("material", "STONE");
        JsonArray array = new JsonArray();
        array.add(item);
        JsonObject json = request(recipientsUuids(UUID_1), "제목");
        json.add("attachments", array);
        assertEquals(400, statusOf(json));
    }

    @Test
    void attachmentCountZeroIsInvalidField() {
        JsonObject item = new JsonObject();
        item.addProperty("material", "STONE");
        item.addProperty("count", 0);
        JsonArray array = new JsonArray();
        array.add(item);
        JsonObject json = request(recipientsUuids(UUID_1), "제목");
        json.add("attachments", array);
        assertEquals(422, statusOf(json));
    }

    @Test
    void attachmentCountStringIsBadRequest() {
        JsonObject item = new JsonObject();
        item.addProperty("material", "STONE");
        item.addProperty("count", "3");
        JsonArray array = new JsonArray();
        array.add(item);
        JsonObject json = request(recipientsUuids(UUID_1), "제목");
        json.add("attachments", array);
        assertEquals(400, statusOf(json));
    }

    // ---------------------------------------------------------------- attached_g / expires_days

    @Test
    void attachedGNegativeIsInvalidField() {
        JsonObject json = request(recipientsUuids(UUID_1), "제목");
        json.addProperty("attached_g", -1);
        assertEquals(422, statusOf(json));
    }

    @Test
    void expiresDaysBoundary() {
        JsonObject zero = request(recipientsUuids(UUID_1), "제목");
        zero.addProperty("expires_days", 0);
        assertEquals(422, statusOf(zero));

        JsonObject tooMany = request(recipientsUuids(UUID_1), "제목");
        tooMany.addProperty("expires_days", 91);
        assertEquals(422, statusOf(tooMany));

        JsonObject one = request(recipientsUuids(UUID_1), "제목");
        one.addProperty("expires_days", 1);
        assertDoesNotThrow(() -> MailHandler.parseSendRequest(one));

        JsonObject ninety = request(recipientsUuids(UUID_1), "제목");
        ninety.addProperty("expires_days", 90);
        assertDoesNotThrow(() -> MailHandler.parseSendRequest(ninety));
    }

    // ---------------------------------------------------------------- checkText / parseMailId

    @Test
    void checkTextEnforcesLengthAndControlChars() {
        assertDoesNotThrow(() -> MailHandler.checkText("field", "정상", 1, 10, false));
        assertEquals(422, assertThrows(BridgeException.class,
                () -> MailHandler.checkText("field", "", 1, 10, false)).status());
        assertEquals(422, assertThrows(BridgeException.class,
                () -> MailHandler.checkText("field", "가".repeat(11), 1, 10, false)).status());
        assertEquals(422, assertThrows(BridgeException.class,
                () -> MailHandler.checkText("field", "안녕\t", 1, 10, false)).status());
        assertDoesNotThrow(() -> MailHandler.checkText("field", "안녕\n하세요", 0, 20, true));
    }

    @Test
    void parseMailIdParsesTrimmedNumbers() throws BridgeException {
        assertEquals(12L, MailHandler.parseMailId("12"));
        assertEquals(7L, MailHandler.parseMailId(" 7 "));
    }

    @Test
    void parseMailIdRejectsInvalidInput() {
        assertEquals(400, assertThrows(BridgeException.class, () -> MailHandler.parseMailId("abc")).status());
        assertEquals(400, assertThrows(BridgeException.class, () -> MailHandler.parseMailId(null)).status());
    }

    // ---------------------------------------------------------------- sendResponse / mailJson

    @Test
    void sendResponseShapesJson() {
        UUID heldUuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        MailManager.SendResult result = new MailManager.SendResult(
                List.of(100L, 101L, 102L),
                List.of(UUID.fromString(UUID_1)),
                List.of(heldUuid));

        JsonObject json = MailHandler.sendResponse(result);

        assertTrue(json.get("ok").getAsBoolean());
        assertEquals(1, json.get("sent").getAsInt());
        assertEquals(1, json.get("held").getAsInt());

        JsonArray heldUuidsJson = json.getAsJsonArray("held_uuids");
        assertEquals(1, heldUuidsJson.size());
        assertEquals(heldUuid.toString(), heldUuidsJson.get(0).getAsString());

        JsonArray mailIdsJson = json.getAsJsonArray("mail_ids");
        assertEquals(3, mailIdsJson.size());
        assertEquals(100L, mailIdsJson.get(0).getAsLong());
        assertEquals(101L, mailIdsJson.get(1).getAsLong());
        assertEquals(102L, mailIdsJson.get(2).getAsLong());
    }

    @Test
    void mailJsonIncludesAllFieldsAndNullClaimedAtWhenUnclaimed() {
        MailAttachment special = new MailAttachment("AAAA", "AMETHYST_SHARD", "강화석", "강화석", 3, 1);
        MailAttachment vanilla = new MailAttachment("AAAA", "DIAMOND", "다이아몬드", null, 5, 0);
        Mail mail = new Mail(7, MailSenderType.ADMIN, "운영자", "제목", "내용",
                List.of(special, vanilla), 1000L, false, 10_000L, 20_000L, false, 0L, false);

        JsonObject json = MailHandler.mailJson(mail);

        assertEquals(7, json.get("id").getAsInt());
        assertEquals("admin", json.get("sender_type").getAsString());
        assertEquals("운영자", json.get("sender_name").getAsString());
        assertEquals("제목", json.get("title").getAsString());
        assertEquals("내용", json.get("body").getAsString());
        assertEquals(1000L, json.get("attached_g").getAsLong());
        assertFalse(json.get("g_claimed").getAsBoolean());
        assertEquals(10_000L, json.get("created_at").getAsLong());
        assertEquals(20_000L, json.get("expires_at").getAsLong());
        assertFalse(json.get("claimed").getAsBoolean());
        assertTrue(json.get("claimed_at").isJsonNull());
        assertFalse(json.get("held").getAsBoolean());

        JsonArray attachments = json.getAsJsonArray("attachments");
        assertEquals(2, attachments.size());

        JsonObject specialJson = attachments.get(0).getAsJsonObject();
        assertEquals("AMETHYST_SHARD", specialJson.get("material").getAsString());
        assertEquals("강화석", specialJson.get("display_name").getAsString());
        assertEquals(3, specialJson.get("count").getAsInt());
        assertEquals("강화석", specialJson.get("item_name").getAsString());
        assertEquals(2, specialJson.get("remaining").getAsInt());

        JsonObject vanillaJson = attachments.get(1).getAsJsonObject();
        assertTrue(vanillaJson.get("item_name").isJsonNull());
        assertEquals(5, vanillaJson.get("remaining").getAsInt());
    }

    @Test
    void mailJsonClaimedAtIsNumberWhenClaimed() {
        Mail mail = new Mail(8, MailSenderType.SYSTEM, null, "제목", "내용",
                List.of(), 0L, false, 10_000L, 20_000L, true, 15_000L, false);

        JsonObject json = MailHandler.mailJson(mail);

        assertTrue(json.get("claimed").getAsBoolean());
        assertEquals(15_000L, json.get("claimed_at").getAsLong());
    }
}
