package dev.no.name.afriends.net.service;

import com.google.gson.JsonObject;
import dev.no.name.afriends.net.identity.Identity;
import dev.no.name.afriends.net.rpc.LinkCalls;
import dev.no.name.afriends.net.tunnel.Tunnel;
import dev.no.name.afriends.net.tunnel.TunnelError;
import dev.no.name.afriends.net.tunnel.TunnelListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// Держит туннель открытым: подключается, после обрыва ждёт и пробует снова, пауза растёт от 15 секунд
// до 5 минут. Все обратные вызовы и ответы на запросы приходят на поток сети
final class LinkConnection implements LinkCalls {
    private static final long RETRY_MIN_MS = 15_000;
    private static final long RETRY_MAX_MS = 300_000;

    interface Handler {
        void ready(boolean registered);

        void event(String event, JsonObject data);

        void down();
    }

    private final LinkEndpoint endpoint;
    private final Identity identity;
    private final ScheduledExecutorService worker;
    private final Executor io;
    private final Handler handler;
    private Tunnel tunnel;
    private boolean wanted;
    private boolean kicked;
    // текущий туннель дошёл до входа
    private boolean reached;
    private long retryDelay = RETRY_MIN_MS;
    private int keyIndex;
    private ScheduledFuture<?> retry;

    LinkConnection(LinkEndpoint endpoint, Identity identity, ScheduledExecutorService worker, Executor io, Handler handler) {
        this.endpoint = endpoint;
        this.identity = identity;
        this.worker = worker;
        this.io = io;
        this.handler = handler;
    }

    void start() {
        if (wanted) {
            return;
        }
        wanted = true;
        retryDelay = RETRY_MIN_MS;
        open();
    }

    void stop() {
        wanted = false;
        cancelRetry();
        if (tunnel != null) {
            Tunnel closing = tunnel;
            tunnel = null;
            closing.close();
        }
    }

    // этим аккаунтом вошли с другого клиента: сразу не возвращаемся, иначе два клиента выбивали бы друг друга
    void kicked() {
        kicked = true;
    }

    @Override
    public CompletableFuture<JsonObject> request(String op, JsonObject data) {
        Tunnel current = tunnel;
        if (current == null) {
            return CompletableFuture.failedFuture(new TunnelError("offline"));
        }
        return current.request(op, data).whenCompleteAsync((json, error) -> { }, worker);
    }

    private void open() {
        cancelRetry();
        reached = false;
        byte[] serverKey = endpoint.serverKeys().get(keyIndex % endpoint.serverKeys().size());
        Tunnel[] holder = new Tunnel[1];
        holder[0] = new Tunnel(endpoint.address(), serverKey, identity, io, new TunnelListener() {
            @Override
            public void onReady(String accountId, boolean registered, long serverTime) {
                worker.execute(() -> {
                    if (tunnel == holder[0]) {
                        retryDelay = RETRY_MIN_MS;
                        reached = true;
                        handler.ready(registered);
                    }
                });
            }

            @Override
            public void onEvent(String event, JsonObject data) {
                worker.execute(() -> {
                    if (tunnel == holder[0]) {
                        handler.event(event, data);
                    }
                });
            }

            @Override
            public void onClosed() {
                worker.execute(() -> closed(holder[0]));
            }
        });
        tunnel = holder[0];
        tunnel.connect();
    }

    private void closed(Tunnel which) {
        if (tunnel != which) {
            return;
        }
        tunnel = null;
        handler.down();
        if (!wanted) {
            return;
        }
        if (!reached) {
            // не дошли до входа: возможно, сервер уже на следующем ключе
            keyIndex++;
        }
        long delay = kicked ? RETRY_MAX_MS : retryDelay;
        kicked = false;
        retryDelay = Math.min(RETRY_MAX_MS, retryDelay * 2);
        retry = worker.schedule(this::open, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelRetry() {
        if (retry != null) {
            retry.cancel(false);
            retry = null;
        }
    }
}
