package dev.no.name.afriends.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageEditsTest {
    private static final String ID = "0011223344556677";

    private static PrivateMessage withId(boolean fromMe, String text, long time, String id) {
        return new PrivateMessage(fromMe, text, time, id, List.of(), null, null);
    }

    @Test
    @DisplayName("ключ: id, а без него — время и сторона")
    void key() {
        assertEquals(ID, withId(true, "a", 1, ID).key());
        assertEquals("t5m", new PrivateMessage(true, "a", 5).key());
        assertEquals("t5p", new PrivateMessage(false, "a", 5).key());
    }

    @Test
    @DisplayName("ответ хранится в файле переписки и переживает реакцию")
    void replyInJson() {
        MessageReply reply = new MessageReply(ID, "Bob", "привет");
        PrivateMessage message = new PrivateMessage(true, "и тебе", 10, "8899aabbccddeeff", List.of(), null, null, null, reply);
        assertEquals(reply, MessageJson.read(MessageJson.write(message)).reply());
        assertEquals(reply, message.withReaction(true, Reactions.ALL.get(0)).reply());
        assertNull(MessageJson.read(MessageJson.write(new PrivateMessage(true, "x", 1))).reply());
    }

    @Test
    @DisplayName("цитата картинки без подписи — «Изображение»")
    void replyToImage() {
        MessageImage image = new MessageImage("0123456789abcdef0123456789abcdef", "a2V5", "image/png", 10, 10, 100, "ab".repeat(32));
        MessageReply reply = MessageReply.to(new PrivateMessage(false, "", 1, ID, image), "Bob");
        assertEquals("Изображение", reply.snippet());
        assertEquals(ID, reply.id());
    }

    @Test
    @DisplayName("удаление по ключам убирает и закреп")
    void removeAlsoUnpins() {
        PrivateMessages messages = new PrivateMessages();
        messages.add("Bob", withId(false, "раз", 1, ID));
        messages.add("Bob", new PrivateMessage(true, "два", 2));
        assertTrue(messages.pin("bob", ID, true));
        assertEquals(1, messages.pinnedMessages("Bob").size());
        assertTrue(messages.remove("Bob", List.of(ID, "t2m")));
        assertTrue(messages.chat("Bob").isEmpty());
        assertTrue(messages.pinnedMessages("Bob").isEmpty());
        assertFalse(messages.remove("Bob", List.of(ID)));
    }

    @Test
    @DisplayName("заметки в одну миллисекунду различаются: удаление одной не трогает другую")
    void notesAtSameTimeAreDistinct() {
        PrivateMessages messages = new PrivateMessages();
        messages.addNote(new PrivateMessage(true, "раз", 7));
        messages.addNote(new PrivateMessage(true, "два", 7));
        List<PrivateMessage> notes = messages.chat(PrivateMessages.NOTES);
        assertFalse(notes.get(0).key().equals(notes.get(1).key()));
        messages.remove(PrivateMessages.NOTES, List.of(notes.get(0).key()));
        assertEquals(List.of("два"), messages.chat(PrivateMessages.NOTES).stream().map(PrivateMessage::text).toList());
    }

    @Test
    @DisplayName("закрепы по порядку переписки, несуществующее не закрепляется, сохраняются в файл")
    void pins() {
        PrivateMessages messages = new PrivateMessages();
        messages.add("Bob", new PrivateMessage(false, "первое", 1));
        messages.add("Bob", withId(true, "второе", 2, ID));
        messages.pin("Bob", ID, true);
        messages.pin("Bob", "t1p", true);
        assertFalse(messages.pin("Bob", "nope", true));
        assertEquals(List.of("первое", "второе"), messages.pinnedMessages("Bob").stream().map(PrivateMessage::text).toList());

        PrivateMessages loaded = new PrivateMessages();
        loaded.fromJson(messages.toJson());
        assertTrue(loaded.isMessagePinned("bob", ID));
        assertEquals(2, loaded.pinnedMessages("Bob").size());
        assertEquals(List.of("Bob"), loaded.partners());

        loaded.pin("Bob", ID, false);
        assertEquals(List.of("первое"), loaded.pinnedMessages("Bob").stream().map(PrivateMessage::text).toList());
        loaded.clear("Bob");
        assertTrue(loaded.pinnedMessages("Bob").isEmpty());
    }
}
