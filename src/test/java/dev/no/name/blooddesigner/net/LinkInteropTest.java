package dev.no.name.blooddesigner.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.e2e.PrekeyBundle;
import dev.no.name.blooddesigner.net.e2e.PrekeyRing;
import dev.no.name.blooddesigner.net.tunnel.Tunnel;
import dev.no.name.blooddesigner.net.tunnel.TunnelError;
import dev.no.name.blooddesigner.net.tunnel.TunnelListener;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Сквозная проверка на настоящем сервере (cloud/server): туннель, вход, друзья, предключи, переписка.
// Запускается только когда заданы LINK_TEST_URL (ws://127.0.0.1:8787/v1/tunnel) и LINK_TEST_KEY (ключ сервера в base64)
class LinkInteropTest {
    private static final long WAIT_SECONDS = 10;

    // участник с открытым туннелем; события сервера складываются в очередь
    private static final class Client implements TunnelListener {
        final TestPeer peer = new TestPeer();
        final String name;
        final BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<Boolean> ready = new CompletableFuture<>();
        final CompletableFuture<Void> closed = new CompletableFuture<>();
        final Tunnel tunnel;

        Client(String name, URI address, byte[] serverKey, ExecutorService executor) throws Exception {
            this.name = name;
            this.tunnel = new Tunnel(address, serverKey, peer.identity, executor, this);
            tunnel.connect();
        }

        @Override
        public void onReady(String accountId, boolean registered, long serverTime) {
            ready.complete(registered);
        }

        @Override
        public void onEvent(String event, JsonObject data) {
            if (event.equals("msg")) {
                messages.add(data);
            }
        }

        @Override
        public void onClosed() {
            closed.complete(null);
            ready.completeExceptionally(new TunnelError("offline"));
        }

        JsonObject call(String op, JsonObject data) throws Exception {
            return tunnel.request(op, data).get(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        String refusal(String op, JsonObject data) {
            return TunnelError.codeOf(assertThrows(Exception.class, () -> call(op, data)));
        }

        void register() throws Exception {
            JsonObject data = new JsonObject();
            data.addProperty("name", name);
            data.addProperty("boxKey", Bytes.base64(peer.identity.exchangePublic()));
            data.addProperty("keySig", Bytes.base64(peer.identity.keyCardSignature()));
            data.addProperty("client", "blooddesigner");
            call("register", data);
            call("keys.publish", peer.sessions.ring().publishPayload(peer.identity, PrekeyRing.ONE_TIME_BATCH));
            peer.sessions.saveRing();
        }

        void send(Client to, byte[] envelope) throws Exception {
            JsonObject data = new JsonObject();
            data.addProperty("to", to.peer.identity.id());
            data.addProperty("body", Bytes.base64(envelope));
            call("msg.send", data);
        }

        // следующий конверт от сервера: расшифровать и подтвердить получение
        String receive(Client from) throws Exception {
            JsonObject event = messages.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(event, "конверт не пришёл");
            assertEquals(from.peer.identity.id(), event.get("from").getAsString());
            byte[] plain = peer.sessions.open(from.peer.keys(), Bytes.fromBase64(event.get("body").getAsString()), System.currentTimeMillis());
            JsonObject ack = new JsonObject();
            JsonArray ids = new JsonArray();
            ids.add(event.get("id").getAsLong());
            ack.add("ids", ids);
            call("msg.ack", ack);
            return new String(plain, StandardCharsets.UTF_8);
        }
    }

    private static JsonObject of(String field, String value) {
        JsonObject json = new JsonObject();
        json.addProperty(field, value);
        return json;
    }

    private static String randomName(String prefix) {
        return prefix + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
    }

    @Test
    void twoPlayersTalkThroughTheRealServer() throws Exception {
        String url = System.getenv("LINK_TEST_URL");
        String key = System.getenv("LINK_TEST_KEY");
        assumeTrue(url != null && key != null, "сервер для проверки не задан");
        URI address = URI.create(url);
        byte[] serverKey = Bytes.fromBase64(key);
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            Client alice = new Client(randomName("Al_"), address, serverKey, executor);
            Client bob = new Client(randomName("Bo_"), address, serverKey, executor);
            assertFalse(alice.ready.get(WAIT_SECONDS, TimeUnit.SECONDS), "новый аккаунт ещё без ника");
            bob.ready.get(WAIT_SECONDS, TimeUnit.SECONDS);

            // до регистрации операции закрыты
            assertEquals("not_registered", alice.refusal("sync", new JsonObject()));
            alice.register();
            bob.register();

            // не друзьям ни ключей, ни сообщений
            assertEquals("not_friends", alice.refusal("keys.fetch", of("id", bob.peer.identity.id())));
            assertEquals("requested", alice.call("friends.request", of("name", bob.name)).get("status").getAsString());
            JsonObject accept = of("id", alice.peer.identity.id());
            accept.addProperty("accept", true);
            assertEquals("friends", bob.call("friends.respond", accept).get("status").getAsString());

            JsonObject sync = alice.call("sync", new JsonObject());
            JsonObject friend = sync.getAsJsonArray("friends").get(0).getAsJsonObject();
            assertEquals(bob.name, friend.get("name").getAsString());
            assertTrue(friend.get("online").getAsBoolean());
            assertEquals("blooddesigner", friend.get("client").getAsString());

            // Алиса берёт связку Боба с сервера и пишет первой
            PrekeyBundle bundle = PrekeyBundle.parse(alice.call("keys.fetch", of("id", bob.peer.identity.id())), bob.peer.identity.id());
            assertTrue(bundle.hasOneTime());
            alice.send(bob, alice.peer.sessions.sealFirst(bundle, "привет через сервер".getBytes(StandardCharsets.UTF_8)));
            assertEquals("привет через сервер", bob.receive(alice));
            bob.send(alice, bob.peer.sessions.seal(alice.peer.identity.id(), "вижу".getBytes(StandardCharsets.UTF_8)));
            assertEquals("вижу", alice.receive(bob));

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void offlineMessageWaitsOnTheServer() throws Exception {
        String url = System.getenv("LINK_TEST_URL");
        String key = System.getenv("LINK_TEST_KEY");
        assumeTrue(url != null && key != null, "сервер для проверки не задан");
        URI address = URI.create(url);
        byte[] serverKey = Bytes.fromBase64(key);
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            Client alice = new Client(randomName("Al_"), address, serverKey, executor);
            Client bob = new Client(randomName("Bo_"), address, serverKey, executor);
            alice.ready.get(WAIT_SECONDS, TimeUnit.SECONDS);
            bob.ready.get(WAIT_SECONDS, TimeUnit.SECONDS);
            alice.register();
            bob.register();
            alice.call("friends.request", of("name", bob.name));
            JsonObject accept = of("id", alice.peer.identity.id());
            accept.addProperty("accept", true);
            bob.call("friends.respond", accept);

            bob.tunnel.close();
            bob.closed.get(WAIT_SECONDS, TimeUnit.SECONDS);
            PrekeyBundle bundle = PrekeyBundle.parse(alice.call("keys.fetch", of("id", bob.peer.identity.id())), bob.peer.identity.id());
            alice.send(bob, alice.peer.sessions.sealFirst(bundle, "пока тебя не было".getBytes(StandardCharsets.UTF_8)));

            // Боб входит снова тем же ключом: сервер сразу отдаёт отложенный конверт
            BlockingQueue<JsonObject> inbox = new LinkedBlockingQueue<>();
            CompletableFuture<Boolean> ready = new CompletableFuture<>();
            Tunnel again = new Tunnel(address, serverKey, bob.peer.identity, executor, new TunnelListener() {
                @Override
                public void onReady(String accountId, boolean registered, long serverTime) {
                    ready.complete(registered);
                }

                @Override
                public void onEvent(String event, JsonObject data) {
                    if (event.equals("msg")) {
                        inbox.add(data);
                    }
                }

                @Override
                public void onClosed() {
                }
            });
            again.connect();
            assertTrue(ready.get(WAIT_SECONDS, TimeUnit.SECONDS), "аккаунт уже с ником");
            JsonObject event = inbox.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(event, "отложенный конверт не пришёл");
            byte[] plain = bob.peer.sessions.open(alice.peer.keys(), Bytes.fromBase64(event.get("body").getAsString()), System.currentTimeMillis());
            assertEquals("пока тебя не было", new String(plain, StandardCharsets.UTF_8));
            again.close();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void wrongServerKeyNeverConnects() throws Exception {
        String url = System.getenv("LINK_TEST_URL");
        assumeTrue(url != null, "сервер для проверки не задан");
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            // ключ не тот, что у сервера: так выглядит попытка подменить сервер
            byte[] wrongKey = dev.no.name.blooddesigner.net.crypto.DhKey.generate().publicRaw();
            Client client = new Client(randomName("Ev_"), URI.create(url), wrongKey, executor);
            assertThrows(Exception.class, () -> client.ready.get(WAIT_SECONDS, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

}
