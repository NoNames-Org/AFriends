package dev.no.name.blooddesigner.net.chat;

import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import dev.no.name.blooddesigner.net.presence.PresenceKeys;
import dev.no.name.blooddesigner.net.rpc.Failures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Раздаёт друзьям свой ключ статуса сквозными сообщениями. По одному другу раз в полторы секунды:
// при сотне друзей это укладывается в лимиты сервера на отправку. Зовётся только с потока сети
public final class KeyCourier {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    private static final long SPACING_MS = 1_500;
    private static final long FAILED_RETRY_MS = 600_000;

    private final Outbox outbox;
    private final PresenceKeys keys;
    private final ScheduledExecutorService worker;
    private final Deque<LinkFriend> queue = new ArrayDeque<>();
    private final Map<String, Long> failedAt = new HashMap<>();
    private boolean running;

    public KeyCourier(Outbox outbox, PresenceKeys keys, ScheduledExecutorService worker) {
        this.outbox = outbox;
        this.keys = keys;
        this.worker = worker;
    }

    // после синхронизации: всем, у кого нет текущего ключа, — в очередь
    public void plan(LinkSnapshot state, long now) {
        long epoch = keys.own(now).epoch();
        for (LinkFriend friend : state.friends()) {
            boolean queued = queue.stream().anyMatch(item -> item.id().equals(friend.id()));
            boolean resting = now - failedAt.getOrDefault(friend.id(), 0L) < FAILED_RETRY_MS;
            if (!keys.delivered(friend.id(), epoch) && !queued && !resting) {
                queue.add(friend);
            }
        }
        next();
    }

    // связь оборвалась: очередь соберётся заново после следующего sync
    public void clear() {
        queue.clear();
        failedAt.clear();
    }

    private void next() {
        if (running || queue.isEmpty()) {
            return;
        }
        running = true;
        LinkFriend friend = queue.poll();
        PresenceKeys.Key own = keys.own(System.currentTimeMillis());
        outbox.send(friend, new ChatPayload.PresenceKey(own.epoch(), own.secret())).whenComplete((done, error) -> {
            if (error == null) {
                keys.markDelivered(friend.id(), own.epoch());
                failedAt.remove(friend.id());
            } else {
                LOGGER.debug("{}: ключ статуса для {} не ушёл ({})", BloodDesigner.NAME, friend.name(), Failures.codeOf(error));
                failedAt.put(friend.id(), System.currentTimeMillis());
            }
            worker.schedule(() -> {
                running = false;
                next();
            }, SPACING_MS, TimeUnit.MILLISECONDS);
        });
    }
}
