package dev.no.name.blooddesigner.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateMessagesTest {
    @Test
    @DisplayName("чаты по свежести, закреплённые сверху, ник в исходном регистре")
    void orderAndPin() {
        PrivateMessages messages = new PrivateMessages();
        messages.add("Bob", new PrivateMessage(false, "старое", 100));
        messages.add("Ann", new PrivateMessage(false, "новое", 200));
        assertEquals(List.of("Ann", "Bob"), messages.partners());
        messages.togglePin("bob");
        assertTrue(messages.isPinned("BOB"));
        assertEquals(List.of("Bob", "Ann"), messages.partners());
        assertEquals("новое", messages.last("ann").text());
    }

    @Test
    @DisplayName("очистить — чат остаётся пустым, удалить — пропадает; прочитать всё")
    void clearDeleteAndReadAll() {
        PrivateMessages messages = new PrivateMessages();
        messages.add("Bob", new PrivateMessage(false, "1", 1));
        messages.add("Ann", new PrivateMessage(false, "2", 2));
        assertEquals(2, messages.totalUnread());
        messages.markAllRead();
        assertEquals(0, messages.totalUnread());
        messages.clear("Bob");
        assertTrue(messages.chat("Bob").isEmpty());
        assertTrue(messages.partners().contains("Bob"));
        messages.delete("Ann");
        assertFalse(messages.partners().contains("Ann"));
    }

    @Test
    @DisplayName("заметки «Избранное» не попадают в список собеседников и сохраняются вместе с закреплёнными")
    void notesAndJson() {
        PrivateMessages messages = new PrivateMessages();
        messages.addNote("купить алмазы", 5);
        messages.add("Bob", new PrivateMessage(true, "привет", 6));
        messages.togglePin("Bob");
        assertEquals(List.of("Bob"), messages.partners());

        PrivateMessages loaded = new PrivateMessages();
        loaded.fromJson(messages.toJson());
        assertEquals("купить алмазы", loaded.chat(PrivateMessages.NOTES).get(0).text());
        assertTrue(loaded.isPinned("bob"));
        assertEquals(List.of("Bob"), loaded.partners());
    }

    @Test
    @DisplayName("группа: закреп, название и черновик переживают перезапуск, даже если название как ник")
    void groupSurvivesReload() {
        PrivateMessages messages = new PrivateMessages();
        String group = PrivateMessages.groupKey("0123456789abcdef");
        messages.add("Bob", new PrivateMessage(false, "x", 1));
        messages.ensureChat(group);
        messages.setTitle(group, "Bob");
        messages.togglePin(group);
        messages.setDraft(group, "черновик");
        PrivateMessages loaded = new PrivateMessages();
        loaded.fromJson(messages.toJson());
        assertTrue(loaded.isPinned(group));
        assertFalse(loaded.isPinned("bob"));
        assertEquals("Bob", loaded.displayName(group));
        assertEquals("черновик", loaded.draft(group));
    }
}
