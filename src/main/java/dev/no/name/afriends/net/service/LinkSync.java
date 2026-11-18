package dev.no.name.afriends.net.service;

import com.google.gson.JsonObject;
import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.net.e2e.E2eException;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.model.LinkSnapshot;
import dev.no.name.afriends.net.model.Presence;
import dev.no.name.afriends.net.presence.PresenceBook;
import dev.no.name.afriends.net.rpc.Failures;
import dev.no.name.afriends.net.rpc.LinkCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

// Синхронизация: свой статус друзьям, взамен друзья, заявки и их статусы. Не по таймеру, а по поводу:
// вход, событие dirty, смена статуса. Не чаще раза в 2 секунды; просьбы, пришедшие во время запроса,
// склеиваются в один следующий. Статус шлётся только тем друзьям, у кого он устарел. Поток сети
final class LinkSync {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final long MIN_GAP_MS = 2_000;

    interface Result {
        void synced(LinkSnapshot before, LinkSnapshot after, int oneTimeKeys);

        void failed(String code);
    }

    // что этот друг уже получил от нас
    private record Sent(Presence presence, long epoch) {
    }

    private final LinkCalls calls;
    private final PresenceBook book;
    private final SyncReader reader;
    private final ScheduledExecutorService worker;
    private final Supplier<LinkSnapshot> snapshot;
    private final Result result;
    private final Map<String, Sent> sent = new HashMap<>();
    private LocalProfile profile;
    private boolean running;
    private boolean again;
    private boolean scheduled;
    private long lastStart;

    LinkSync(LinkCalls calls, PresenceBook book, ScheduledExecutorService worker, Supplier<LinkSnapshot> snapshot, Result result) {
        this.calls = calls;
        this.book = book;
        this.reader = new SyncReader(book);
        this.worker = worker;
        this.snapshot = snapshot;
        this.result = result;
    }

    // статус сменился: друзьям уходит сразу, но не чаще раза в 2 секунды
    void profile(LocalProfile value) {
        LocalProfile old = profile;
        profile = value;
        if (old == null || !value.presence().sameAs(old.presence()) || value.visible() != old.visible()
                || value.accent() != old.accent()) {
            request();
        }
    }

    void request() {
        if (running) {
            again = true;
            return;
        }
        long wait = lastStart + MIN_GAP_MS - System.currentTimeMillis();
        if (wait > 0) {
            if (!scheduled) {
                scheduled = true;
                worker.schedule(() -> {
                    scheduled = false;
                    request();
                }, wait, TimeUnit.MILLISECONDS);
            }
            return;
        }
        run();
    }

    // связь оборвалась: после нового входа статус уйдёт всем заново
    void reset() {
        sent.clear();
        again = false;
    }

    // друг удалён или сменился ключ статуса: этим друзьям — заново
    void forget(String friendId) {
        sent.remove(friendId);
    }

    private void run() {
        running = true;
        lastStart = System.currentTimeMillis();
        LinkSnapshot before = snapshot.get();
        LocalProfile current = profile;
        Map<String, Sent> sending = new HashMap<>();
        JsonObject data = new JsonObject();
        if (current != null) {
            data.addProperty("accent", current.accent());
            data.addProperty("visible", current.visible());
            data.add("presence", blobs(before, current.presence(), sending));
        }
        calls.request("sync", data).whenComplete((json, error) -> {
            running = false;
            if (error != null) {
                result.failed(Failures.codeOf(error));
                return;
            }
            sent.putAll(sending);
            LinkSnapshot after = reader.read(json, before, System.currentTimeMillis());
            sent.keySet().removeIf(id -> after.friend(id) == null);
            // у нового друга ещё нет нашего статуса: досылаем следующим заходом. Только новым:
            // если статус кому-то не зашифровался, повтор каждые 2 секунды ничего бы не дал
            boolean newcomers = after.friends().stream().anyMatch(friend -> before.friend(friend.id()) == null);
            result.synced(before, after, json.has("oneTimeKeys") ? json.get("oneTimeKeys").getAsInt() : Integer.MAX_VALUE);
            if (again || (newcomers && current != null)) {
                again = false;
                request();
            }
        });
    }

    private JsonObject blobs(LinkSnapshot known, Presence presence, Map<String, Sent> sending) {
        long now = System.currentTimeMillis();
        long epoch = book.keys().own(now).epoch();
        JsonObject blobs = new JsonObject();
        for (LinkFriend friend : known.friends()) {
            Sent last = sent.get(friend.id());
            if (last != null && last.epoch() == epoch && presence.sameAs(last.presence())) {
                continue;
            }
            try {
                blobs.addProperty(friend.id(), book.seal(friend.id(), presence, now));
                sending.put(friend.id(), new Sent(presence, epoch));
            } catch (E2eException e) {
                LOGGER.warn("{}: статус для {} не зашифровался", AFriends.NAME, friend.name());
            }
        }
        return blobs;
    }
}
