package dev.no.name.blooddesigner.net.tunnel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.crypto.SignLabel;
import dev.no.name.blooddesigner.net.identity.Identity;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

// Одно соединение с сервером сети: WebSocket по TLS, внутри туннель Noise, внутри JSON-запросы и события.
// Объект одноразовый: после обрыва создаётся новый. Все методы можно звать с любого потока
public final class Tunnel implements WebSocket.Listener {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final long REQUEST_TIMEOUT_SECONDS = 15;
    private static final int MAX_SERVER_MESSAGE = 2 * 1024 * 1024;

    private enum Stage { CONNECTING, HANDSHAKE, LOGIN, READY, CLOSED }

    private final URI address;
    private final Identity identity;
    private final Executor executor;
    private final TunnelListener listener;
    private final NoiseInitiator noise;
    private final Frames.Assembler assembler = new Frames.Assembler(MAX_SERVER_MESSAGE);
    private final ByteArrayOutputStream partial = new ByteArrayOutputStream();
    private final Map<Long, CompletableFuture<JsonObject>> waiting = new ConcurrentHashMap<>();

    private Stage stage = Stage.CONNECTING;
    private WebSocket socket;
    private NoiseInitiator.Transport transport;
    // кадры уходят строго по одному: WebSocket в Java не разрешает вторую отправку, пока не закончилась первая
    private CompletableFuture<?> sendTail = CompletableFuture.completedFuture(null);
    private long nextRequest = 1;

    public Tunnel(URI address, byte[] serverKey, Identity identity, Executor executor, TunnelListener listener) {
        String scheme = address.getScheme();
        boolean local = "ws".equals(scheme) && ("127.0.0.1".equals(address.getHost()) || "localhost".equals(address.getHost()));
        if (!"wss".equals(scheme) && !local) {
            throw new IllegalArgumentException("сеть работает только по wss: " + address);
        }
        this.address = address;
        this.identity = identity;
        this.executor = executor;
        this.listener = listener;
        this.noise = new NoiseInitiator(serverKey);
    }

    public void connect() {
        HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).executor(executor).build()
                .newWebSocketBuilder().connectTimeout(CONNECT_TIMEOUT)
                .buildAsync(address, this)
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        shutdown();
                    }
                });
    }

    public synchronized boolean ready() {
        return stage == Stage.READY;
    }

    // запрос к серверу; завершается телом ответа либо TunnelError с кодом отказа
    public synchronized CompletableFuture<JsonObject> request(String op, JsonObject data) {
        if (stage != Stage.READY) {
            return CompletableFuture.failedFuture(new TunnelError("offline"));
        }
        long index = nextRequest++;
        JsonObject message = new JsonObject();
        message.addProperty("t", "req");
        message.addProperty("i", index);
        message.addProperty("op", op);
        message.add("d", data);
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        waiting.put(index, result);
        result.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((json, error) -> waiting.remove(index));
        try {
            send(message);
        } catch (GeneralSecurityException e) {
            shutdown();
        }
        return result;
    }

    public void close() {
        shutdown();
    }

    // ---- WebSocket ----

    @Override
    public void onOpen(WebSocket webSocket) {
        synchronized (this) {
            socket = webSocket;
            if (stage != Stage.CONNECTING) {
                webSocket.abort();
                return;
            }
            try {
                stage = Stage.HANDSHAKE;
                enqueue(noise.writeHello());
            } catch (GeneralSecurityException e) {
                shutdown();
                return;
            }
        }
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        byte[] chunk = new byte[data.remaining()];
        data.get(chunk);
        byte[] message = null;
        synchronized (this) {
            partial.write(chunk, 0, chunk.length);
            if (last) {
                message = partial.toByteArray();
                partial.reset();
            }
        }
        if (message != null) {
            try {
                received(message);
            } catch (GeneralSecurityException | RuntimeException e) {
                // подделка, порча или нарушение протокола: соединению больше нельзя верить
                shutdown();
                return null;
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        // текстовых кадров в протоколе нет
        shutdown();
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        shutdown();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        shutdown();
    }

    // ---- протокол ----

    private void received(byte[] message) throws GeneralSecurityException {
        JsonObject json;
        synchronized (this) {
            if (stage == Stage.HANDSHAKE) {
                transport = noise.readReply(message);
                stage = Stage.LOGIN;
                send(login());
                return;
            }
            if (stage != Stage.LOGIN && stage != Stage.READY) {
                return;
            }
            String text = assembler.push(transport.receive().decrypt(Bytes.EMPTY, message));
            if (text == null) {
                return;
            }
            json = JsonParser.parseString(text).getAsJsonObject();
            if (stage == Stage.LOGIN) {
                if (!"auth".equals(text(json, "t")) || !json.get("ok").getAsBoolean()) {
                    throw new GeneralSecurityException("сервер не принял вход");
                }
                stage = Stage.READY;
            }
        }
        dispatch(json);
    }

    // вход: подпись хеша рукопожатия. Хеш свой у каждого соединения, поэтому подпись не повторить
    private JsonObject login() {
        JsonObject message = new JsonObject();
        message.addProperty("t", "auth");
        message.addProperty("signKey", Bytes.base64(identity.signPublic()));
        message.addProperty("sig", Bytes.base64(identity.sign(SignLabel.AUTH, transport.handshakeHash())));
        return message;
    }

    // слушатель зовётся без блокировки: из него можно сразу слать новые запросы
    private void dispatch(JsonObject json) {
        switch (text(json, "t")) {
            case "auth" -> listener.onReady(text(json, "id"), json.get("registered").getAsBoolean(), json.get("serverTime").getAsLong());
            case "evt" -> listener.onEvent(text(json, "ev"), json.has("d") && json.get("d").isJsonObject() ? json.getAsJsonObject("d") : new JsonObject());
            case "res" -> {
                CompletableFuture<JsonObject> result = waiting.remove(json.get("i").getAsLong());
                if (result == null) {
                    return;
                }
                if (json.get("ok").getAsBoolean()) {
                    result.complete(json.has("d") && json.get("d").isJsonObject() ? json.getAsJsonObject("d") : new JsonObject());
                } else {
                    result.completeExceptionally(new TunnelError(text(json, "e")));
                }
            }
            default -> throw new IllegalArgumentException("неизвестный тип сообщения");
        }
    }

    // зовётся под блокировкой: шифрование и постановка в очередь идут одним шагом,
    // иначе счётчик nonce разошёлся бы с порядком кадров
    private void send(JsonObject message) throws GeneralSecurityException {
        for (byte[] frame : Frames.encode(message.toString())) {
            enqueue(transport.send().encrypt(Bytes.EMPTY, frame));
        }
    }

    private void enqueue(byte[] frame) {
        WebSocket target = socket;
        sendTail = sendTail.thenCompose(done -> target.sendBinary(ByteBuffer.wrap(frame), true))
                .whenComplete((done, error) -> {
                    if (error != null) {
                        shutdown();
                    }
                });
    }

    private void shutdown() {
        WebSocket open;
        synchronized (this) {
            if (stage == Stage.CLOSED) {
                return;
            }
            stage = Stage.CLOSED;
            open = socket;
        }
        if (open != null) {
            open.abort();
        }
        waiting.values().forEach(result -> result.completeExceptionally(new TunnelError("offline")));
        waiting.clear();
        listener.onClosed();
    }

    private static String text(JsonObject json, String field) {
        return json.has(field) && json.get(field).isJsonPrimitive() ? json.get(field).getAsString() : "";
    }
}
