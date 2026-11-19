package dev.no.name.afriends.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageEventsTest {
    private static final class Recorder implements MessageEvents {
        private int changes;
        private final List<String> incoming = new ArrayList<>();

        @Override
        public void changed() {
            changes++;
        }

        @Override
        public void incoming(String chat) {
            incoming.add(chat);
        }
    }

    @Test
    @DisplayName("подписчик SocialData узнаёт о новом сообщении в любом аккаунте и об изменении переписки")
    void listenersSeeEveryScope() {
        String[] account = {"main"};
        SocialData social = new SocialData(() -> account[0], () -> "server", SkinMemory.NONE, chat -> false);
        Recorder recorder = new Recorder();
        social.addListener(recorder);

        social.messages().add("Bob", new PrivateMessage(false, "привет", 1));
        account[0] = "twink";
        social.messages().add("Ann", new PrivateMessage(false, "хай", 2));

        assertEquals(List.of("Bob", "Ann"), recorder.incoming);
        assertTrue(recorder.changes >= 2);
    }

    @Test
    @DisplayName("своё сообщение и сообщение в открытом чате не считаются входящими")
    void ownAndOpenAreNotIncoming() {
        Recorder recorder = new Recorder();
        PrivateMessages messages = new PrivateMessages(recorder, chat -> false);

        messages.add("Bob", new PrivateMessage(true, "моё", 1));
        messages.open("Ann");
        messages.add("Ann", new PrivateMessage(false, "открыт", 2));

        assertTrue(recorder.incoming.isEmpty());
        assertEquals(2, recorder.changes);
    }

    @Test
    @DisplayName("заглушённый чат не возвращается из архива новым сообщением")
    void mutedStaysArchived() {
        PrivateMessages messages = new PrivateMessages(MessageEvents.NONE, chat -> chat.equalsIgnoreCase("bob"));
        messages.add("Bob", new PrivateMessage(false, "1", 1));
        messages.toggleArchived("Bob");

        messages.add("Bob", new PrivateMessage(false, "2", 2));

        assertTrue(messages.isArchived("Bob"));
    }
}
