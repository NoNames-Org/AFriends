package dev.no.name.blooddesigner.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageHistoryTest {
    @AfterEach
    void restoreDefault() {
        MessageHistory.set(MessageHistory.DEFAULT);
    }

    @Test
    @DisplayName("лимит: по умолчанию 100, зажат в 8…1024")
    void clamps() {
        assertEquals(100, MessageHistory.DEFAULT);
        MessageHistory.set(3);
        assertEquals(8, MessageHistory.limit());
        MessageHistory.set(5000);
        assertEquals(1024, MessageHistory.limit());
        MessageHistory.set(256);
        assertEquals(256, MessageHistory.limit());
    }

    @Test
    @DisplayName("переписка обрезается до лимита — остаются самые новые; сохраняется тоже не больше лимита")
    void trimsChats() {
        MessageHistory.set(8);
        PrivateMessages messages = new PrivateMessages();
        for (int i = 0; i < 20; i++) {
            messages.add("Bob", new PrivateMessage(false, "m" + i, i));
        }
        assertEquals(8, messages.chat("Bob").size());
        assertEquals("m19", messages.last("Bob").text());

        MessageHistory.set(16);
        for (int i = 20; i < 30; i++) {
            messages.add("Bob", new PrivateMessage(false, "m" + i, i));
        }
        assertEquals(16, messages.chat("Bob").size());
        MessageHistory.set(8);
        messages.trimAll();
        assertEquals(8, messages.chat("Bob").size());

        PrivateMessages loaded = new PrivateMessages();
        loaded.fromJson(messages.toJson());
        assertEquals(8, loaded.chat("Bob").size());
        assertEquals("m29", loaded.last("Bob").text());
    }
}
