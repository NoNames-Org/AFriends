package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Формат файла переписок не должен меняться при рефакторинге: сохранённые у игроков данные
 * читаются новой версией, а данные новой версии — старой. Тест сверяет JSON и порядок ключей
 */
class PrivateMessagesFormatTest {
    private static final String GROUP = PrivateMessages.groupKey("g42");
    private static final MessageImage AVATAR = new MessageImage("0123456789abcdef0123456789abcdef", "key1", "image/png", 64, 64, 1234,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    private static PrivateMessages sample() {
        PrivateMessages messages = new PrivateMessages();
        messages.add("Bob", new PrivateMessage(false, "hello", 1, "m1", (MessageImage) null));
        messages.add("Bob", new PrivateMessage(true, "hi", 2, "m2", (MessageImage) null));
        messages.add("Ann", new PrivateMessage(false, "ann", 3, "m3", (MessageImage) null));
        messages.add(GROUP, new PrivateMessage(false, "in group", 4, "m4", (MessageImage) null));
        messages.addNote(new PrivateMessage(true, "note", 5, "n1", (MessageImage) null));
        messages.setTitle(GROUP, "Clan");
        messages.setDescription(GROUP, "About");
        messages.setAvatar(GROUP, AVATAR);
        messages.markReceipt("m2", PrivateMessages.RECEIPT_READ);
        messages.setDraft("Bob", "draft");
        messages.togglePin("Ann");
        messages.toggleArchived(GROUP);
        messages.pin("Bob", messages.find("Bob", "m1").key(), true);
        return messages;
    }

    @Test
    @DisplayName("JSON переписок совпадает с эталоном версии 2.0.4")
    void jsonMatchesGolden() {
        JsonElement json = sample().toJson();

        assertEquals(GOLDEN, json.toString());
    }

    @Test
    @DisplayName("сохранённое читается обратно без потерь")
    void roundTrip() {
        PrivateMessages loaded = new PrivateMessages();
        loaded.fromJson(JsonParser.parseString(GOLDEN));

        assertEquals(GOLDEN, loaded.toJson().toString());
        assertEquals("Clan", loaded.displayName(GROUP));
        assertEquals("About", loaded.description(GROUP));
        assertEquals(AVATAR, loaded.avatar(GROUP));
        assertEquals(PrivateMessages.RECEIPT_READ, loaded.receipt("m2"));
        assertEquals("draft", loaded.draft("bob"));
        assertEquals(List.of("Ann", "Bob"), loaded.partners());
        assertEquals(List.of(GROUP), loaded.archivedPartners());
        assertEquals(1, loaded.pinnedMessages("Bob").size());
        assertEquals(1, loaded.chat(PrivateMessages.NOTES).size());
    }

    private static final String GOLDEN = "{\"$titles\":{\"$g:g42\":\"Clan\"},\"$gdesc\":{\"$g:g42\":\"About\"},\"$gavatar\":{\"$g:g42\":{\"b\":\"0123456789abcdef0123456789abcdef\",\"k\":\"key1\",\"m\":\"image/png\",\"w\":64,\"h\":64,\"s\":1234,\"sha\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}},\"$receipts\":{\"m2\":2},\"$drafts\":{\"Bob\":\"draft\"},\"$pinned\":[\"Ann\"],\"$archived\":[\"$g:g42\"],\"$pins\":{\"Bob\":[\"m1\"]},\"Ann\":[{\"me\":false,\"text\":\"ann\",\"time\":3,\"id\":\"m3\"}],\"Bob\":[{\"me\":false,\"text\":\"hello\",\"time\":1,\"id\":\"m1\"},{\"me\":true,\"text\":\"hi\",\"time\":2,\"id\":\"m2\"}],\"$g:g42\":[{\"me\":false,\"text\":\"in group\",\"time\":4,\"id\":\"m4\"}],\"$notes\":[{\"me\":true,\"text\":\"note\",\"time\":5,\"id\":\"n1\"}]}";
}
