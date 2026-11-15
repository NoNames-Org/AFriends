package dev.no.name.blooddesigner.net;

import dev.no.name.blooddesigner.net.model.Presence;
import dev.no.name.blooddesigner.net.presence.PresenceBook;
import dev.no.name.blooddesigner.net.presence.PresenceKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// статусы по ключу отправителя (CRYPTO.md, раздел 7а)
class PresenceTest {
    private static final String ALICE = "a".repeat(32);
    private static final String BOB = "b".repeat(32);
    private static final String CAROL = "c".repeat(32);
    private static final Presence ON_SERVER = new Presence(Presence.Where.SERVER, "play.example.net", "строю", 1000);

    private final PresenceKeys aliceKeys = new PresenceKeys(new TestPeer.MemoryStore());
    private final PresenceBook alice = new PresenceBook(ALICE, aliceKeys);
    private final PresenceKeys bobKeys = new PresenceKeys(new TestPeer.MemoryStore());
    private final PresenceBook bob = new PresenceBook(BOB, bobKeys);

    private void handKeyToBob(long now) {
        PresenceKeys.Key key = aliceKeys.own(now);
        bobKeys.remember(ALICE, key.epoch(), key.secret());
    }

    @Test
    void friendReadsStatusOnceKeyArrived() throws Exception {
        String blob = alice.seal(BOB, ON_SERVER, 0);
        assertNull(bob.open(ALICE, blob), "без ключа статус не читается");
        handKeyToBob(0);
        Presence read = bob.open(ALICE, blob);
        assertNotNull(read);
        assertTrue(ON_SERVER.sameAs(read));
    }

    @Test
    void blobForOneFriendDoesNotOpenForAnother() throws Exception {
        handKeyToBob(0);
        String forCarol = alice.seal(CAROL, ON_SERVER, 0);
        // сервер подсунул Бобу блоб Кэрол: AD не сходится
        assertNull(bob.open(ALICE, forCarol));
    }

    @Test
    void oldKeyStillReadsUntilTwoRotations() throws Exception {
        handKeyToBob(0);
        String beforeRotation = alice.seal(BOB, ON_SERVER, 0);
        aliceKeys.rotate(1);
        handKeyToBob(1);
        assertNotNull(bob.open(ALICE, beforeRotation), "прошлая эпоха ещё хранится");
        aliceKeys.rotate(2);
        handKeyToBob(2);
        assertNull(bob.open(ALICE, beforeRotation), "позапрошлая эпоха стёрта");
    }

    @Test
    void replayedOldKeyIsIgnored() {
        PresenceKeys.Key first = aliceKeys.own(0);
        aliceKeys.rotate(1);
        PresenceKeys.Key second = aliceKeys.own(1);
        assertTrue(bobKeys.remember(ALICE, second.epoch(), second.secret()));
        assertFalse(bobKeys.remember(ALICE, first.epoch(), first.secret()));
    }

    @Test
    void keyRotatesAfterAWeek() {
        long epoch = aliceKeys.own(0).epoch();
        assertEquals(epoch, aliceKeys.own(86_400_000L).epoch());
        assertEquals(epoch + 1, aliceKeys.own(8L * 86_400_000L).epoch());
    }

    @Test
    void stateSurvivesRestart() throws Exception {
        TestPeer.MemoryStore store = new TestPeer.MemoryStore();
        PresenceKeys keys = new PresenceKeys(store);
        PresenceKeys.Key own = keys.own(0);
        keys.markDelivered(BOB, own.epoch());
        PresenceKeys restored = new PresenceKeys(store);
        assertEquals(own.epoch(), restored.own(0).epoch());
        assertTrue(restored.delivered(BOB, own.epoch()));
    }
}
