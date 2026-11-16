package dev.no.name.blooddesigner.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageJsonTest {
    private static final MessageImage IMAGE = new MessageImage("0123456789abcdef0123456789abcdef", "a2V5", "image/png",
            10, 10, 100, "cd".repeat(32));

    @Test
    @DisplayName("id, картинка и реакции переживают сохранение переписки")
    void roundTrip() {
        PrivateMessage message = new PrivateMessage(true, "подпись", 42, "0011223344556677", java.util.List.of(IMAGE), "fire", "heart", "Fell__o_o");
        assertEquals(message, MessageJson.read(MessageJson.write(message)));
    }

    @Test
    @DisplayName("старая запись без новых полей читается; отложенные без «me» — входящие")
    void legacy() {
        PrivateMessage old = MessageJson.read(JsonParser.parseString("{\"me\":true,\"text\":\"hi\",\"time\":5}").getAsJsonObject());
        assertTrue(old.fromMe());
        assertNull(old.id());
        PrivateMessage parked = MessageJson.read(JsonParser.parseString("{\"text\":\"hi\",\"time\":5}").getAsJsonObject());
        assertFalse(parked.fromMe());
    }

    @Test
    @DisplayName("реакция ставится по id и снимается; у каждой стороны своя")
    void react() {
        PrivateMessages messages = new PrivateMessages();
        messages.add("Fell", new PrivateMessage(false, "привет", 1, "0011223344556677", null));
        assertTrue(messages.react("fell", "0011223344556677", true, "like"));
        assertTrue(messages.react("Fell", "0011223344556677", false, "laugh"));
        PrivateMessage last = messages.last("Fell");
        assertEquals("like", last.reaction());
        assertEquals("laugh", last.partnerReaction());
        messages.react("Fell", "0011223344556677", true, null);
        assertNull(messages.last("Fell").reaction());
        assertFalse(messages.react("Fell", "ffffffffffffffff", true, "like"));
    }
}
