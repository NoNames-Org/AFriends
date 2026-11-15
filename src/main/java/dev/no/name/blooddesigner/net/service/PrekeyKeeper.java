package dev.no.name.blooddesigner.net.service;

import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.net.e2e.E2eSessions;
import dev.no.name.blooddesigner.net.e2e.PrekeyRing;
import dev.no.name.blooddesigner.net.identity.Identity;
import dev.no.name.blooddesigner.net.rpc.LinkCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;

// Свои предключи на сервере: подписанный меняется раз в неделю, одноразовые доливаются пачкой,
// когда их осталось мало. Кольцо сохраняется на диск до отправки: иначе после сбоя друг
// зашифровал бы первое сообщение ключом, секрета которого у нас уже нет. Зовётся с потока сети
final class PrekeyKeeper {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);

    private final LinkCalls calls;
    private final E2eSessions sessions;
    private final Identity identity;
    private boolean publishing;

    PrekeyKeeper(LinkCalls calls, E2eSessions sessions, Identity identity) {
        this.calls = calls;
        this.sessions = sessions;
        this.identity = identity;
    }

    // после входа: подписанный ключ (сменить, если пора), одноразовые — по ответу sync
    CompletableFuture<Void> publishSigned(long now) {
        return publish(now, 0);
    }

    // sync сообщил остаток или пришло событие prekeys.low
    void topUp(int left, long now) {
        if (left < PrekeyRing.ONE_TIME_LOW && !publishing) {
            publish(now, PrekeyRing.ONE_TIME_BATCH);
        }
    }

    private CompletableFuture<Void> publish(long now, int oneTime) {
        PrekeyRing ring = sessions.ring();
        try {
            if (ring.needsRotation(now)) {
                ring.rotate(now);
            }
        } catch (GeneralSecurityException e) {
            LOGGER.error("{}: не удалось сделать новый предключ", BloodDesigner.NAME, e);
            return CompletableFuture.failedFuture(e);
        }
        publishing = true;
        var payload = ring.publishPayload(identity, oneTime);
        sessions.saveRing();
        return calls.request("keys.publish", payload).whenComplete((json, error) -> publishing = false).thenApply(json -> null);
    }
}
