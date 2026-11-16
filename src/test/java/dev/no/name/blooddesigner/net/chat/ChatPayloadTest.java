package dev.no.name.blooddesigner.net.chat;

import dev.no.name.blooddesigner.core.MessageImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatPayloadTest {
    private static final MessageImage IMAGE = new MessageImage("0123456789abcdef0123456789abcdef", "a2V5", "image/gif",
            320, 200, 12345, "ab".repeat(32));

    @Test
    @DisplayName("текст с id доходит с тем же id")
    void textWithId() {
        ChatPayload.Text text = (ChatPayload.Text) ChatPayload.decode(new ChatPayload.Text("привет", "0011223344556677", null).encode());
        assertEquals("привет", text.text());
        assertEquals("0011223344556677", text.id());
        assertNull(text.image());
    }

    @Test
    @DisplayName("сообщение старой версии без id читается как раньше")
    void legacyText() {
        ChatPayload.Text text = (ChatPayload.Text) ChatPayload.decode("{\"k\":\"text\",\"t\":\"hi\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("hi", text.text());
        assertNull(text.id());
    }

    @Test
    @DisplayName("картинка: старая версия видит запасной текст, новая — картинку без него")
    void image() {
        byte[] encoded = new ChatPayload.Text("", "0011223344556677", IMAGE).encode();
        String json = new String(encoded, StandardCharsets.UTF_8);
        assertEquals(true, json.contains(ChatPayload.IMAGE_FALLBACK));
        ChatPayload.Text text = (ChatPayload.Text) ChatPayload.decode(encoded);
        assertEquals("", text.text());
        assertEquals(IMAGE, text.image());
    }

    @Test
    @DisplayName("картинка с подписью: подпись доходит без запасного текста, старая версия видит и подпись, и пометку")
    void imageWithCaption() {
        byte[] encoded = new ChatPayload.Text("смотри какой закат", "0011223344556677", IMAGE).encode();
        String json = new String(encoded, StandardCharsets.UTF_8);
        assertEquals(true, json.contains("смотри какой закат") && json.contains(ChatPayload.IMAGE_FALLBACK));
        ChatPayload.Text text = (ChatPayload.Text) ChatPayload.decode(encoded);
        assertEquals("смотри какой закат", text.text());
        assertEquals(IMAGE, text.image());
    }

    @Test
    @DisplayName("пересланное: пометка доходит, управляющие символы из неё вычищаются")
    void forwarded() {
        ChatPayload.Text text = (ChatPayload.Text) ChatPayload.decode(new ChatPayload.Text("привет", "0011223344556677", null, "Fell__o_o").encode());
        assertEquals("Fell__o_o", text.forwardedFrom());
        ChatPayload.Text dirty = (ChatPayload.Text) ChatPayload.decode(
                "{\"k\":\"text\",\"t\":\"hi\",\"f\":\"A\\nB§c\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("ABc", dirty.forwardedFrom());
        assertNull(((ChatPayload.Text) ChatPayload.decode(new ChatPayload.Text("hi").encode())).forwardedFrom());
    }

    @Test
    @DisplayName("реакция и её снятие; чужая реакция не из набора отбрасывается")
    void reaction() {
        ChatPayload.Reaction set = (ChatPayload.Reaction) ChatPayload.decode(new ChatPayload.Reaction("0011223344556677", "fire").encode());
        assertEquals("fire", set.reaction());
        ChatPayload.Reaction removed = (ChatPayload.Reaction) ChatPayload.decode(new ChatPayload.Reaction("0011223344556677", null).encode());
        assertNull(removed.reaction());
        assertNull(ChatPayload.decode("{\"k\":\"react\",\"to\":\"0011223344556677\",\"r\":\"skull\"}".getBytes(StandardCharsets.UTF_8)));
        assertNull(ChatPayload.decode("{\"k\":\"react\",\"to\":\"../etc\",\"r\":\"fire\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("битая картинка не ломает сообщение: остаётся текст")
    void brokenImage() {
        String json = "{\"k\":\"text\",\"t\":\"смотри\",\"img\":{\"b\":\"nope\"}}";
        ChatPayload payload = ChatPayload.decode(json.getBytes(StandardCharsets.UTF_8));
        ChatPayload.Text text = assertInstanceOf(ChatPayload.Text.class, payload);
        assertEquals("смотри", text.text());
        assertNull(text.image());
    }
}
