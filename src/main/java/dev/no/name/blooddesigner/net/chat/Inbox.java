package dev.no.name.blooddesigner.net.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.e2e.E2eException;
import dev.no.name.blooddesigner.net.e2e.E2eSessions;
import dev.no.name.blooddesigner.net.e2e.PinnedKeys;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import dev.no.name.blooddesigner.net.presence.PresenceKeys;
import dev.no.name.blooddesigner.net.rpc.LinkCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

// Входящие конверты. Ящик сервер отдаёт сразу при входе, раньше первой синхронизации, поэтому
// конверты от ещё неизвестных друзей ждут ближайшего sync. Каждый конверт подтверждается, даже
// нечитаемый: иначе сервер слал бы его вечно. Зовётся только с потока сети
public final class Inbox {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);

    private static final class Waiting {
        final JsonObject event;
        boolean seenSync;

        Waiting(JsonObject event) {
            this.event = event;
        }
    }

    private final LinkCalls calls;
    private final E2eSessions sessions;
    private final PinnedKeys pinned;
    private final PresenceKeys presenceKeys;
    private final Supplier<LinkSnapshot> snapshot;
    private final InboxListener listener;
    private final Runnable resync;
    private final List<Waiting> waiting = new ArrayList<>();

    public Inbox(LinkCalls calls, E2eSessions sessions, PinnedKeys pinned, PresenceKeys presenceKeys,
                 Supplier<LinkSnapshot> snapshot, InboxListener listener, Runnable resync) {
        this.calls = calls;
        this.sessions = sessions;
        this.pinned = pinned;
        this.presenceKeys = presenceKeys;
        this.snapshot = snapshot;
        this.listener = listener;
        this.resync = resync;
    }

    // событие msg {id, from, body, ts}
    public void received(JsonObject event) {
        LinkSnapshot state = snapshot.get();
        LinkFriend from = state.friend(event.get("from").getAsString());
        if (from == null) {
            // друг, о котором мы ещё не знаем: только что принял заявку и сразу написал
            waiting.add(new Waiting(event));
            if (state.synced()) {
                resync.run();
            }
            return;
        }
        open(from, event);
    }

    // после каждой синхронизации: кто стал известен — читаем; кто так и не нашёлся после sync — выбрасываем
    public void drain() {
        LinkSnapshot state = snapshot.get();
        Iterator<Waiting> iterator = waiting.iterator();
        while (iterator.hasNext()) {
            Waiting item = iterator.next();
            LinkFriend from = state.friend(item.event.get("from").getAsString());
            if (from != null) {
                iterator.remove();
                open(from, item.event);
            } else if (item.seenSync) {
                iterator.remove();
                acknowledge(item.event);
            } else {
                item.seenSync = true;
            }
        }
    }

    private void open(LinkFriend from, JsonObject event) {
        long time = event.get("ts").getAsLong();
        try {
            if (pinned.changed(from.keys())) {
                throw new E2eException("keys_changed");
            }
            byte[] plain = sessions.open(from.keys(), Bytes.fromBase64(event.get("body").getAsString()), System.currentTimeMillis());
            dispatch(from, ChatPayload.decode(plain), time);
        } catch (E2eException | IllegalArgumentException e) {
            LOGGER.warn("{}: конверт от {} не расшифровался ({})", BloodDesigner.NAME, from.name(),
                    e instanceof E2eException e2e ? e2e.code() : "base64");
            listener.unreadable(from, time);
        }
        acknowledge(event);
    }

    private void dispatch(LinkFriend from, ChatPayload payload, long time) {
        switch (payload) {
            case ChatPayload.Text text -> listener.text(from, text.text(), time);
            case ChatPayload.PresenceKey key -> {
                // новый ключ: статус, который уже лежит на сервере, теперь читается
                if (presenceKeys.remember(from.id(), key.epoch(), key.key())) {
                    resync.run();
                }
            }
            case ChatPayload.Unknown unknown -> { }
            case null -> listener.unreadable(from, time);
        }
    }

    private void acknowledge(JsonObject event) {
        JsonObject data = new JsonObject();
        JsonArray ids = new JsonArray();
        ids.add(event.get("id").getAsLong());
        data.add("ids", ids);
        // не дошло — сервер пришлёт снова при следующем входе, расшифровка тогда честно откажет как повтор
        calls.request("msg.ack", data);
    }
}
