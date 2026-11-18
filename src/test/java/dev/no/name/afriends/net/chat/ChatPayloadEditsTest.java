package dev.no.name.afriends.net.chat;

import dev.no.name.afriends.core.MessageReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPayloadEditsTest {
    private static final String ID = "0011223344556677";
    private static final String OTHER = "8899aabbccddeeff";

    @Test
    @DisplayName("ответ доходит с id, автором и началом текста")
    void reply() {
        MessageReply reply = new MessageReply(OTHER, "Bob", "привет");
        ChatPayload.Text text = (ChatPayload.Text) ChatPayload.decode(new ChatPayload.Text("и тебе", ID, null, null, reply).encode());
        assertEquals("и тебе", text.text());
        assertEquals(reply, text.reply());
    }

    @Test
    @DisplayName("ответ со странным id отбрасывается, само сообщение остаётся")
    void replyWithBadId() {
        String json = "{\"k\":\"text\",\"t\":\"hi\",\"id\":\"" + ID + "\",\"re\":{\"id\":\"t123m\",\"a\":\"x\",\"s\":\"y\"}}";
        ChatPayload.Text text = (ChatPayload.Text) ChatPayload.decode(json.getBytes(StandardCharsets.UTF_8));
        assertEquals("hi", text.text());
        assertNull(text.reply());
    }

    @Test
    @DisplayName("автор и текст ответа без переводов строк и не длиннее предела")
    void replyIsCleaned() {
        MessageReply reply = new MessageReply(ID, "a\nb§c", "x".repeat(500));
        assertEquals("a b c", reply.author());
        assertEquals(MessageReply.MAX_SNIPPET, reply.snippet().length());
    }

    @Test
    @DisplayName("удаление у обоих: список id туда и обратно, битый id — весь конверт отбрасывается")
    void delete() {
        ChatPayload.Delete delete = (ChatPayload.Delete) ChatPayload.decode(new ChatPayload.Delete(List.of(ID, OTHER)).encode());
        assertEquals(List.of(ID, OTHER), delete.targets());
        String bad = "{\"k\":\"del\",\"to\":[\"" + ID + "\",\"nope\"]}";
        assertNull(ChatPayload.decode(bad.getBytes(StandardCharsets.UTF_8)));
        assertNull(ChatPayload.decode(new ChatPayload.Delete(Collections.nCopies(ChatPayload.MAX_DELETE + 1, ID)).encode()));
    }

    @Test
    @DisplayName("закреп и открепление у обоих")
    void pin() {
        ChatPayload.Pin pin = (ChatPayload.Pin) ChatPayload.decode(new ChatPayload.Pin(ID, true).encode());
        assertEquals(ID, pin.target());
        assertTrue(pin.pinned());
        ChatPayload unpin = ChatPayload.decode(new ChatPayload.Pin(ID, false).encode());
        assertInstanceOf(ChatPayload.Pin.class, unpin);
        assertEquals(false, ((ChatPayload.Pin) unpin).pinned());
    }
}
