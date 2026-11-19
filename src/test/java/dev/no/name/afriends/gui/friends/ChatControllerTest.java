package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControllerTest {
    /** Маршрут без сети: запоминает, что ушло и что отмечено прочитанным. */
    private static final class FakeRoute implements MessageRoute {
        private final List<String> sent = new ArrayList<>();
        private final List<List<PrivateMessage>> read = new ArrayList<>();
        private Optional<String> problem = Optional.empty();
        private boolean media;

        @Override
        public Optional<String> send(String partner, String text, Consumer<String> failed) {
            sent.add(partner + ":" + text);
            return problem;
        }

        @Override
        public boolean viaNetwork(String partner) {
            return false;
        }

        @Override
        public String headerLine(String partner) {
            return null;
        }

        @Override
        public void markRead(String chat, List<PrivateMessage> seen) {
            read.add(seen);
        }

        @Override
        public boolean supportsMedia(String partner) {
            return media;
        }

        @Override
        public Optional<String> sendVoice(String partner, byte[] data, int centiseconds, MessageReply reply,
                                          Consumer<String> failed) {
            sent.add(partner + ":voice");
            return Optional.empty();
        }
    }

    private static final class Sounds implements ChatController.Feedback {
        private int warned;
        private int sent;

        @Override
        public void warned() {
            warned++;
        }

        @Override
        public void sent() {
            sent++;
        }
    }

    private final PrivateMessages messages = new PrivateMessages();
    private final FakeRoute route = new FakeRoute();
    private final NotifySettings alerts = new NotifySettings();
    private final Sounds sounds = new Sounds();
    private final long[] now = {1_000_000};
    private final List<PrivateMessage> announcements = new ArrayList<>();
    private final ChatController controller = new ChatController(messages, route, alerts, () -> announcements, sounds,
            () -> now[0]);

    private static PrivateMessage incoming(String text, long time, String id) {
        return new PrivateMessage(false, text, time, id, null);
    }

    @Nested
    class Selection {
        @Test
        @DisplayName("при смене чата черновик уходящего сохраняется, у нового подставляется")
        void draftsFollowChats() {
            messages.add("Bob", incoming("привет", 1, "b1"));
            messages.add("Ann", incoming("хай", 2, "a1"));
            controller.select("Bob", "");

            String annDraft = controller.select("Ann", "недописанное Бобу");

            assertEquals("", annDraft);
            assertEquals("недописанное Бобу", controller.select("Bob", ""));
            assertEquals("Bob", controller.partner());
            assertTrue(controller.isSelected("bob"));
        }

        @Test
        @DisplayName("открытие чата снимает непрочитанное и отмечает прочитанными только последние сообщения")
        void openMarksRead() {
            for (int i = 0; i < 60; i++) {
                messages.add("Bob", incoming("m" + i, i, "id" + i));
            }

            controller.select("Bob", "");

            assertEquals(0, messages.unread("Bob"));
            assertEquals(1, route.read.size());
            assertEquals(ChatController.READ_WINDOW, route.read.get(0).size());
            assertEquals("m59", route.read.get(0).getLast().text());
        }

        @Test
        @DisplayName("объявления: только чтение, пустая лента показывает заглушку, писать нельзя")
        void announcementsAreReadOnly() {
            controller.select(ChatController.ANNOUNCEMENTS, "");

            assertTrue(controller.isReadOnly());
            assertEquals(1, controller.chatMessages(ChatController.ANNOUNCEMENTS).size());
            assertFalse(controller.sendText("привет", null));
            assertTrue(route.read.isEmpty(), "объявления не отмечаются прочитанными");
        }

        @Test
        @DisplayName("вид архива переключается независимо от выбранного чата")
        void archiveView() {
            controller.setArchiveView(true);
            controller.select("Bob", "");

            assertTrue(controller.archiveView());
            controller.setArchiveView(false);
            assertFalse(controller.archiveView());
        }
    }

    @Nested
    class Pending {
        @Test
        @DisplayName("отправленное висит как ожидающее, пока не истечёт таймаут")
        void pendingTimesOut() {
            controller.select("Bob", "");
            assertTrue(controller.sendText("привет", null));

            assertEquals(1, controller.visiblePending(now[0] + 1000).size());
            assertTrue(controller.visiblePending(now[0] + ChatController.PENDING_TIMEOUT_MS + 1).isEmpty());
            assertEquals(1, sounds.sent);
        }

        @Test
        @DisplayName("собеседник ушёл из сети после отправки — ожидающее остаётся и помечено недоставленным")
        void failedStays() {
            controller.select("Bob", "");
            controller.sendText("привет", null);
            messages.markOffline("Bob", now[0] + 10);

            List<ChatController.Pending> pending = controller.visiblePending(now[0] + ChatController.PENDING_TIMEOUT_MS * 2);

            assertEquals(1, pending.size());
            assertTrue(controller.isFailed(pending.get(0)));
        }

        @Test
        @DisplayName("своё сообщение вернулось из сети — ожидающее уходит, анимация идёт с момента отправки")
        void returnedMessageClaimsPending() {
            controller.select("Bob", "");
            long sentAt = now[0];
            controller.sendText("привет", null);
            now[0] += 500;
            messages.add("Bob", new PrivateMessage(true, "привет", now[0], "m1", null));

            assertTrue(controller.trackNewMessages(messages.chat("Bob")));

            assertTrue(controller.visiblePending(now[0]).isEmpty());
            assertEquals(sentAt, controller.appearStart(0));
            assertFalse(controller.trackNewMessages(messages.chat("Bob")), "второй раз нового нет");
        }

        @Test
        @DisplayName("дополнение не пустило сообщение — подпись и звук, ожидающего нет")
        void refusedSend() {
            controller.select("Bob", "");
            route.problem = Optional.of("нельзя");

            assertFalse(controller.sendText("привет", null));

            assertEquals("нельзя", controller.visibleWarning(now[0]));
            assertEquals(1, sounds.warned);
            assertTrue(controller.visiblePending(now[0]).isEmpty());
        }

        @Test
        @DisplayName("в «Избранное» текст ложится заметкой сразу, без ожидания сети")
        void notesAreLocal() {
            controller.select(PrivateMessages.NOTES, "");

            assertTrue(controller.sendText("заметка", null));

            assertEquals(1, messages.chat(PrivateMessages.NOTES).size());
            assertTrue(route.sent.isEmpty());
            assertTrue(controller.visiblePending(now[0]).isEmpty());
        }
    }

    @Nested
    class History {
        @Test
        @DisplayName("лента раскладывает последние сообщения и догружает историю порциями")
        void paging() {
            assertEquals(120, controller.historyFrom(200));

            controller.loadOlder();

            assertEquals(40, controller.historyFrom(200));
            assertEquals(0, controller.historyFrom(50));
        }

        @Test
        @DisplayName("переход к старому сообщению расширяет историю, чтобы оно попало в ленту")
        void revealForJump() {
            List<PrivateMessage> chat = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                chat.add(incoming("m" + i, i, "id" + i));
            }
            controller.requestJump("id10");

            controller.revealForJump(chat, controller.jumpKey());

            assertTrue(controller.historyFrom(chat.size()) <= 10);
            controller.clearJump();
            assertEquals(null, controller.jumpKey());
        }

        @Test
        @DisplayName("новые сообщения не выталкивают из ленты уже загруженные старые")
        void newMessagesGrowWindow() {
            controller.select("Bob", "");
            for (int i = 0; i < 5; i++) {
                messages.add("Bob", incoming("m" + i, i, "id" + i));
            }

            controller.trackNewMessages(messages.chat("Bob"));

            assertEquals(ChatController.HISTORY_PAGE + 5, controller.historyWindow());
        }
    }

    @Nested
    class Labels {
        @Test
        @DisplayName("подсказка-предупреждение гаснет через несколько секунд; info — без звука")
        void warningExpires() {
            controller.info("ищу…");
            assertEquals("ищу…", controller.visibleWarning(now[0] + ChatController.WARNING_MS));
            assertEquals("", controller.visibleWarning(now[0] + ChatController.WARNING_MS + 1));
            assertEquals(0, sounds.warned);
        }

        @Test
        @DisplayName("переслать можно в Избранное, AFriends и любые чаты, кроме открытого")
        void forwardTargets() {
            messages.add("Bob", incoming("1", 1, "b1"));
            messages.add("Ann", incoming("2", 2, "a1"));
            controller.select("Bob", "");

            assertEquals(List.of(PrivateMessages.NOTES, PrivateMessages.OFFICIAL, "Ann"), controller.forwardTargets());
            assertEquals(ChatController.NOTES_TITLE, controller.chatName(PrivateMessages.NOTES));
            assertEquals(ChatController.ANNOUNCEMENTS_TITLE, controller.chatName(ChatController.ANNOUNCEMENTS));
            assertEquals("", controller.chatName(null));
        }

        @Test
        @DisplayName("голосовые — только другу по сети, не в служебные чаты и не в группы")
        void voiceRules() {
            controller.select("Bob", "");
            assertFalse(controller.canSendVoice());

            route.media = true;
            assertTrue(controller.canSendVoice());
            assertTrue(controller.sendVoice(new byte[] {1}, 100, null));

            controller.select(PrivateMessages.groupKey("g1"), "");
            assertFalse(controller.canSendVoice());
        }
    }
}
