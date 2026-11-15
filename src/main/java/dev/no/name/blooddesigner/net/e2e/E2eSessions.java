package dev.no.name.blooddesigner.net.e2e;

import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.net.identity.Identity;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

// Сквозное шифрование переписки: по сессии на друга. Отсюда выходят готовые конверты для сервера
// и сюда приходят конверты от него. Состояние сохраняется после каждого сообщения.
// Не потокобезопасен: зовётся с одного потока сети
public final class E2eSessions {
    private static final String RING_RECORD = "prekeys";

    private final Identity me;
    private final SessionStore store;
    private final PrekeyRing ring;
    private final Map<String, Session> sessions = new HashMap<>();

    public E2eSessions(Identity me, SessionStore store) throws GeneralSecurityException {
        this.me = me;
        this.store = store;
        JsonObject saved = store.load(RING_RECORD);
        this.ring = saved == null ? new PrekeyRing() : PrekeyRing.fromJson(saved);
    }

    // свои предключи: их открытые половины надо выложить на сервер
    public PrekeyRing ring() {
        return ring;
    }

    // после того как кольцо предключей изменилось (смена, новая пачка одноразовых)
    public void saveRing() {
        store.save(RING_RECORD, ring.toJson());
    }

    public boolean has(String friendId) {
        return session(friendId) != null;
    }

    // первое сообщение другу: сессия ставится по его связке ключей с сервера
    public byte[] sealFirst(PrekeyBundle bundle, byte[] plaintext) throws E2eException {
        byte[] padded = Padding.pad(plaintext);
        SessionSetup.Initiated setup = SessionSetup.initiate(me, bundle);
        byte[] associated = SessionSetup.associated(me.signPublic(), me.exchangePublic(), bundle.keys().signKey(), bundle.keys().exchangeKey());
        Session session = Session.started(Ratchet.initiator(setup.sharedKey(), bundle.signedKey()), associated, setup.offer());
        byte[] envelope = session.seal(padded);
        keep(bundle.keys().id(), session);
        return envelope;
    }

    public byte[] seal(String friendId, byte[] plaintext) throws E2eException {
        Session session = session(friendId);
        if (session == null) {
            throw new E2eException("no_session");
        }
        byte[] envelope = session.seal(Padding.pad(plaintext));
        keep(friendId, session);
        return envelope;
    }

    // Расшифровать конверт от друга. Бросает, если он подделан, повторён или зашифрован не для нас;
    // состояние сессии при этом не меняется
    public byte[] open(FriendKeys sender, byte[] data, long now) throws E2eException {
        Envelope envelope = Envelope.decode(data);
        Session session = session(sender.id());
        byte[] padded;
        if (envelope.offer() == null) {
            if (session == null) {
                throw new E2eException("no_session");
            }
            padded = session.open(envelope.message(), now);
        } else if (session != null && session.knows(envelope.offer())) {
            padded = session.openRepeated(envelope.offer(), envelope.message(), now);
        } else {
            Offer offer = envelope.offer();
            byte[] sharedKey = SessionSetup.respond(me, ring, sender, offer);
            byte[] associated = SessionSetup.associated(sender.signKey(), sender.exchangeKey(), me.signPublic(), me.exchangePublic());
            Ratchet ratchet = Ratchet.responder(sharedKey, ring.signed(offer.signedId()));
            padded = ratchet.decrypt(envelope.message(), associated, now);
            // сообщение настоящее: только теперь одноразовый ключ сгорает
            if (offer.oneTimeId() != PrekeyBundle.NONE) {
                ring.consumeOneTime(offer.oneTimeId());
                saveRing();
            }
            session = Session.accept(session, ratchet, associated, offer, me.id().compareTo(sender.id()) < 0);
        }
        keep(sender.id(), session);
        return Padding.unpad(padded);
    }

    // забыть сессию: следующее сообщение поставит новую
    public void reset(String friendId) {
        sessions.remove(friendId);
        store.delete(friendId);
    }

    private Session session(String friendId) {
        Session session = sessions.get(friendId);
        if (session == null) {
            JsonObject saved = store.load(friendId);
            if (saved != null) {
                try {
                    session = Session.fromJson(saved);
                    sessions.put(friendId, session);
                } catch (GeneralSecurityException | RuntimeException e) {
                    // битое состояние бесполезно: сессия пересоздастся
                    store.delete(friendId);
                }
            }
        }
        return session;
    }

    private void keep(String friendId, Session session) {
        sessions.put(friendId, session);
        store.save(friendId, session.toJson());
    }
}
