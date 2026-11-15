package dev.no.name.blooddesigner.net;

import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.net.crypto.KemKey;
import dev.no.name.blooddesigner.net.e2e.E2eException;
import dev.no.name.blooddesigner.net.e2e.PinnedKeys;
import dev.no.name.blooddesigner.net.e2e.PrekeyBundle;
import dev.no.name.blooddesigner.net.e2e.SafetyNumber;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class E2eSessionsTest {
    private static final long NOW = 1_800_000_000_000L;

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private static byte[] first(TestPeer from, TestPeer to, String message, boolean withOneTime) throws Exception {
        return from.sessions.sealFirst(PrekeyBundle.parse(to.bundle(withOneTime), to.identity.id()), text(message));
    }

    @Test
    void conversationGoesBothWays() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        assertEquals("привет", text(bob.sessions.open(alice.keys(), first(alice, bob, "привет", true), NOW)));
        // пока Боб не ответил, Алиса прикладывает предложение сессии к каждому сообщению
        assertEquals("ты тут?", text(bob.sessions.open(alice.keys(), alice.sessions.seal(bob.identity.id(), text("ты тут?")), NOW)));
        assertEquals("тут", text(alice.sessions.open(bob.keys(), bob.sessions.seal(alice.identity.id(), text("тут")), NOW)));
        for (int i = 0; i < 20; i++) {
            assertEquals("a" + i, text(bob.sessions.open(alice.keys(), alice.sessions.seal(bob.identity.id(), text("a" + i)), NOW)));
            assertEquals("b" + i, text(alice.sessions.open(bob.keys(), bob.sessions.seal(alice.identity.id(), text("b" + i)), NOW)));
        }
    }

    @Test
    void worksWithoutOneTimePrekey() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        assertEquals("привет", text(bob.sessions.open(alice.keys(), first(alice, bob, "привет", false), NOW)));
    }

    @Test
    void messagesMayArriveOutOfOrder() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        bob.sessions.open(alice.keys(), first(alice, bob, "0", true), NOW);
        alice.sessions.open(bob.keys(), bob.sessions.seal(alice.identity.id(), text("ok")), NOW);
        byte[] one = alice.sessions.seal(bob.identity.id(), text("1"));
        byte[] two = alice.sessions.seal(bob.identity.id(), text("2"));
        byte[] three = alice.sessions.seal(bob.identity.id(), text("3"));
        assertEquals("3", text(bob.sessions.open(alice.keys(), three, NOW)));
        assertEquals("1", text(bob.sessions.open(alice.keys(), one, NOW)));
        assertEquals("2", text(bob.sessions.open(alice.keys(), two, NOW)));
    }

    @Test
    void replayedAndTamperedMessagesAreRejected() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        bob.sessions.open(alice.keys(), first(alice, bob, "0", true), NOW);
        alice.sessions.open(bob.keys(), bob.sessions.seal(alice.identity.id(), text("ok")), NOW);

        byte[] message = alice.sessions.seal(bob.identity.id(), text("секрет"));
        byte[] tampered = message.clone();
        tampered[tampered.length - 1] ^= 1;
        assertThrows(E2eException.class, () -> bob.sessions.open(alice.keys(), tampered, NOW));
        // подделка не сдвинула храповик: настоящее сообщение всё ещё читается
        assertEquals("секрет", text(bob.sessions.open(alice.keys(), message, NOW)));
        // а второй раз — уже нет: ключ сообщения стёрт
        assertThrows(E2eException.class, () -> bob.sessions.open(alice.keys(), message, NOW));
    }

    @Test
    void strangerCannotReadOrImpersonate() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        TestPeer eve = new TestPeer();
        byte[] message = first(alice, bob, "только Бобу", true);
        assertThrows(E2eException.class, () -> eve.sessions.open(alice.keys(), message, NOW));
        // Ева пишет Бобу, а сервер выдаёт это за сообщение Алисы
        byte[] fromEve = first(eve, bob, "я Алиса", true);
        assertThrows(E2eException.class, () -> bob.sessions.open(alice.keys(), fromEve, NOW));
    }

    @Test
    void bothStartingAtOnceStillConverge() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        byte[] fromAlice = first(alice, bob, "я первая", true);
        byte[] fromBob = first(bob, alice, "я первый", true);
        assertEquals("я первый", text(alice.sessions.open(bob.keys(), fromBob, NOW)));
        assertEquals("я первая", text(bob.sessions.open(alice.keys(), fromAlice, NOW)));
        for (int i = 0; i < 5; i++) {
            assertEquals("a" + i, text(bob.sessions.open(alice.keys(), alice.sessions.seal(bob.identity.id(), text("a" + i)), NOW)));
            assertEquals("b" + i, text(alice.sessions.open(bob.keys(), bob.sessions.seal(alice.identity.id(), text("b" + i)), NOW)));
        }
    }

    @Test
    void stateSurvivesRestart() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        bob.sessions.open(alice.keys(), first(alice, bob, "0", true), NOW);
        alice.sessions.open(bob.keys(), bob.sessions.seal(alice.identity.id(), text("ok")), NOW);
        byte[] inFlight = alice.sessions.seal(bob.identity.id(), text("в пути"));
        alice.restart();
        bob.restart();
        assertEquals("в пути", text(bob.sessions.open(alice.keys(), inFlight, NOW)));
        assertEquals("после", text(bob.sessions.open(alice.keys(), alice.sessions.seal(bob.identity.id(), text("после")), NOW)));
        assertEquals("ответ", text(alice.sessions.open(bob.keys(), bob.sessions.seal(alice.identity.id(), text("ответ")), NOW)));
    }

    @Test
    void friendWhoLostStateCanStartOver() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        bob.sessions.open(alice.keys(), first(alice, bob, "0", true), NOW);
        alice.sessions.open(bob.keys(), bob.sessions.seal(alice.identity.id(), text("ok")), NOW);
        alice.sessions.reset(bob.identity.id());
        assertEquals("заново", text(bob.sessions.open(alice.keys(), first(alice, bob, "заново", true), NOW)));
        assertEquals("принял", text(alice.sessions.open(bob.keys(), bob.sessions.seal(alice.identity.id(), text("принял")), NOW)));
    }

    @Test
    void lengthIsHiddenByBuckets() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        bob.sessions.open(alice.keys(), first(alice, bob, "0", true), NOW);
        alice.sessions.open(bob.keys(), bob.sessions.seal(alice.identity.id(), text("ok")), NOW);
        int shortLength = alice.sessions.seal(bob.identity.id(), text("да")).length;
        int longerLength = alice.sessions.seal(bob.identity.id(), text("да, конечно, давай через час на спавне")).length;
        assertEquals(shortLength, longerLength);
        assertThrows(E2eException.class, () -> alice.sessions.seal(bob.identity.id(), new byte[9000]));
    }

    @Test
    void forgedBundleIsRejected() throws Exception {
        TestPeer bob = new TestPeer();
        TestPeer eve = new TestPeer();
        // сервер подменяет подписанный предключ Боба ключом Евы
        JsonObject forged = bob.bundle(true);
        forged.add("signed", eve.bundle(false).get("signed"));
        assertThrows(E2eException.class, () -> PrekeyBundle.parse(forged, bob.identity.id()));
        // сервер выдаёт связку Евы за связку Боба
        assertThrows(E2eException.class, () -> PrekeyBundle.parse(eve.bundle(true), bob.identity.id()));
    }

    @Test
    void pinnedKeysNoticeSubstitution() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        PinnedKeys pins = new PinnedKeys(alice.store);
        assertEquals(PinnedKeys.Verdict.FIRST_SEEN, pins.check(bob.keys(), true));
        assertEquals(PinnedKeys.Verdict.SAME, new PinnedKeys(alice.store).check(bob.keys(), true));
        assertEquals(PinnedKeys.Verdict.DOWNGRADED, pins.check(bob.keys(), false));
        TestPeer impostor = new TestPeer();
        var swapped = new dev.no.name.blooddesigner.net.e2e.FriendKeys(bob.identity.id(), impostor.identity.signPublic(), impostor.identity.exchangePublic());
        assertEquals(PinnedKeys.Verdict.CHANGED, pins.check(swapped, true));
    }

    @Test
    void safetyNumberIsSameForBothAndDiffersForOthers() throws Exception {
        TestPeer alice = new TestPeer();
        TestPeer bob = new TestPeer();
        TestPeer eve = new TestPeer();
        String forAlice = SafetyNumber.of(alice.identity.signPublic(), alice.identity.exchangePublic(), bob.identity.signPublic(), bob.identity.exchangePublic());
        String forBob = SafetyNumber.of(bob.identity.signPublic(), bob.identity.exchangePublic(), alice.identity.signPublic(), alice.identity.exchangePublic());
        assertEquals(forAlice, forBob);
        assertTrue(forAlice.matches("(\\d{5} ){11}\\d{5}"));
        assertNotEquals(forAlice, SafetyNumber.of(alice.identity.signPublic(), alice.identity.exchangePublic(), eve.identity.signPublic(), eve.identity.exchangePublic()));
    }

    @Test
    void postQuantumPartIsUsedWhenJavaHasIt() throws Exception {
        TestPeer bob = new TestPeer();
        assertEquals(KemKey.available(), PrekeyBundle.parse(bob.bundle(true), bob.identity.id()).hasPostQuantum());
        assertFalse(Runtime.version().feature() >= 24 && !KemKey.available());
    }
}
