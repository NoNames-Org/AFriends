package dev.no.name.afriends.net.chat;

import com.google.gson.JsonObject;
import dev.no.name.afriends.net.crypto.Bytes;
import dev.no.name.afriends.net.e2e.E2eException;
import dev.no.name.afriends.net.e2e.E2eSessions;
import dev.no.name.afriends.net.e2e.PinnedKeys;
import dev.no.name.afriends.net.e2e.PrekeyBundle;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.rpc.Failures;
import dev.no.name.afriends.net.rpc.LinkCalls;

import java.util.concurrent.CompletableFuture;

// Исходящие конверты. Нет сессии с другом — берём его связку ключей, проверяем и ставим сессию.
// Отправки идут строго по одной: две одновременные первые отправки поставили бы две сессии.
// Зовётся только с потока сети
public final class Outbox {
    private final LinkCalls calls;
    private final E2eSessions sessions;
    private final PinnedKeys pinned;
    private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);

    public Outbox(LinkCalls calls, E2eSessions sessions, PinnedKeys pinned) {
        this.calls = calls;
        this.sessions = sessions;
        this.pinned = pinned;
    }

    public CompletableFuture<Void> send(LinkFriend friend, ChatPayload payload) {
        CompletableFuture<Void> result = tail.handle((done, error) -> null).thenCompose(ignored -> deliver(friend, payload));
        tail = result;
        return result;
    }

    private CompletableFuture<Void> deliver(LinkFriend friend, ChatPayload payload) {
        byte[] plain = payload.encode();
        CompletableFuture<byte[]> envelope;
        if (sessions.has(friend.id())) {
            try {
                envelope = CompletableFuture.completedFuture(sessions.seal(friend.id(), plain));
            } catch (E2eException e) {
                return CompletableFuture.failedFuture(e);
            }
        } else {
            envelope = calls.request("keys.fetch", of("id", friend.id())).thenApply(bundle -> sealFirst(friend, bundle, plain));
        }
        return envelope.thenCompose(body -> {
            JsonObject data = of("to", friend.id());
            data.addProperty("body", Bytes.base64(body));
            return calls.request("msg.send", data);
        }).thenApply(ignored -> null);
    }

    // связка проверена целиком: id из ключа подписи, все подписи, закреплённые ключи
    private byte[] sealFirst(LinkFriend friend, JsonObject json, byte[] plain) {
        try {
            PrekeyBundle bundle = PrekeyBundle.parse(json, friend.id());
            PinnedKeys.Verdict verdict = pinned.check(bundle.keys(), bundle.hasPostQuantum());
            if (verdict == PinnedKeys.Verdict.CHANGED || verdict == PinnedKeys.Verdict.DOWNGRADED) {
                throw new E2eException(verdict == PinnedKeys.Verdict.CHANGED ? "keys_changed" : "downgraded");
            }
            return sessions.sealFirst(bundle, plain);
        } catch (E2eException e) {
            throw Failures.wrap(e);
        }
    }

    private static JsonObject of(String field, String value) {
        JsonObject json = new JsonObject();
        json.addProperty(field, value);
        return json;
    }
}
