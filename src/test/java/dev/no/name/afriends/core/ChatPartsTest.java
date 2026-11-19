package dev.no.name.afriends.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPartsTest {
    @Nested
    class ChatIds {
        @Test
        @DisplayName("строка чата разбирается в вид чата и обратно")
        void parsesKinds() {
            assertInstanceOf(ChatId.Notes.class, ChatId.of("$notes"));
            assertInstanceOf(ChatId.Official.class, ChatId.of("$blooddesigner"));
            ChatId group = ChatId.of("$g:abc");
            assertEquals(new ChatId.Group("abc"), group);
            assertEquals("$g:abc", group.key());
            ChatId direct = ChatId.of("Bob");
            assertEquals("bob", direct.key());
            assertEquals("Bob", direct.raw());
        }

        @Test
        @DisplayName("служебные чаты хранятся как есть, ник — в нижнем регистре")
        void reservedKeys() {
            assertTrue(ChatId.isReserved("$g:abc"));
            assertTrue(ChatId.isReserved("$notes"));
            assertFalse(ChatId.isReserved("Bob"));
            assertEquals("$g:ABC", ChatId.storageKey("$g:ABC"));
            assertEquals("bob", ChatId.storageKey("BOB"));
        }
    }

    @Nested
    class Organizer {
        private final ChatOrganizer organizer = new ChatOrganizer();

        @Test
        @DisplayName("закреп и архив снимают друг друга")
        void pinAndArchiveExclude() {
            ChatId bob = ChatId.of("Bob");
            organizer.togglePin(bob);
            organizer.toggleArchived(bob);

            assertFalse(organizer.isPinned("bob"));
            assertTrue(organizer.isArchived("bob"));

            organizer.togglePin(bob);
            assertTrue(organizer.isPinned("bob"));
            assertFalse(organizer.isArchived("bob"));
        }

        @Test
        @DisplayName("чат AFriends не закрепляется, «Избранное» не уходит в архив")
        void serviceChatsAreProtected() {
            assertFalse(organizer.togglePin(ChatId.OFFICIAL));
            assertFalse(organizer.toggleArchived(ChatId.OFFICIAL));
            assertFalse(organizer.toggleArchived(ChatId.NOTES));
            assertTrue(organizer.togglePin(ChatId.NOTES));
        }
    }

    @Nested
    class Pins {
        @Test
        @DisplayName("повторный закреп переносит сообщение в конец, удаление снимает закреп")
        void pinOrderAndRemoval() {
            PinnedMessages pins = new PinnedMessages();
            pins.pin("bob", "a");
            pins.pin("bob", "b");
            pins.pin("bob", "a");

            assertEquals(List.of("b", "a"), pins.keys("bob"));

            pins.unpinAll("bob", List.of("a"));
            assertEquals(List.of("b"), pins.keys("bob"));
        }

        @Test
        @DisplayName("пустой список закрепов в файл не пишется")
        void emptyPinsAreNotWritten() {
            PinnedMessages pins = new PinnedMessages();
            pins.pin("bob", "a");
            pins.unpin("bob", "a");
            JsonObject json = new JsonObject();

            pins.writeTo(json, key -> key);

            assertEquals(0, json.getAsJsonObject(PinnedMessages.JSON_KEY).size());
        }
    }

    @Nested
    class DraftsAndReceipts {
        @Test
        @DisplayName("черновик обрезается по длине, пустой убирает черновик")
        void draftLimits() {
            Drafts drafts = new Drafts();
            assertTrue(drafts.set("bob", "x".repeat(Drafts.MAX_DRAFT + 10)));
            assertEquals(Drafts.MAX_DRAFT, drafts.get("bob").length());

            assertTrue(drafts.set("bob", " "));
            assertEquals("", drafts.get("bob"));
            assertFalse(drafts.set("bob", ""), "убирать нечего — ничего не поменялось");
        }

        @Test
        @DisplayName("отметка о доставке только повышается")
        void receiptsOnlyGoUp() {
            DeliveryReceipts receipts = new DeliveryReceipts();
            assertTrue(receipts.mark("m1", DeliveryReceipts.READ));
            assertFalse(receipts.mark("m1", DeliveryReceipts.DELIVERED));
            assertEquals(DeliveryReceipts.READ, receipts.state("m1"));
            assertEquals(DeliveryReceipts.NONE, receipts.state(null));
        }
    }
}
