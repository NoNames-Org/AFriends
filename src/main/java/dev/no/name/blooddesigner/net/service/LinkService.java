package dev.no.name.blooddesigner.net.service;

import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.net.announcements.AnnouncementFeed;
import dev.no.name.blooddesigner.net.badges.BadgeDirectory;
import dev.no.name.blooddesigner.net.chat.ChatPayload;
import dev.no.name.blooddesigner.net.chat.InboxListener;
import dev.no.name.blooddesigner.net.chat.OfficialInbox;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.media.BlobClient;
import dev.no.name.blooddesigner.net.media.MediaCache;
import dev.no.name.blooddesigner.net.media.MediaCrypto;
import dev.no.name.blooddesigner.net.media.PreparedImage;
import dev.no.name.blooddesigner.net.e2e.SafetyNumber;
import dev.no.name.blooddesigner.net.model.Connection;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import dev.no.name.blooddesigner.net.model.Presence;
import dev.no.name.blooddesigner.net.rpc.Failures;
import dev.no.name.blooddesigner.net.tunnel.TunnelError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Сеть BloodDesigner целиком. Наружу — снимок для интерфейса и действия, всё остальное на своём потоке:
// туннель, ключи и сессии сквозного шифрования не потокобезопасны и трогаются только оттуда.
// Методы можно звать с потока игры; будущие завершаются на потоке сети
public final class LinkService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    private static final long NAME_TAKEN_RETRY_MS = 600_000;

    private final Path folder;
    private final Supplier<PlayerAccount> playerSource;
    private PlayerAccount player;
    private final InboxListener listener;
    private final OfficialInbox official;
    private final BlobClient blobs = new BlobClient();
    private final MediaCache media;
    private final BadgeDirectory badges = new BadgeDirectory();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> daemon(runnable, " link"));
    private final ExecutorService io = Executors.newCachedThreadPool(runnable -> daemon(runnable, " link io"));
    private volatile LinkSnapshot snapshot = LinkSnapshot.initial();
    private LinkRuntime runtime;
    private boolean wanted;
    private volatile LinkEndpoint endpoint;

    public LinkService(Path folder, Supplier<PlayerAccount> player, InboxListener listener) {
        this.folder = folder;
        this.playerSource = player;
        this.listener = listener;
        this.official = new OfficialInbox(folder.resolve("official-last.txt"), listener);
        // картинки переписки лежат рядом с папкой сети: config/blooddesigner/media
        this.media = new MediaCache(folder.resolveSibling("media"), blobs);
    }

    public LinkSnapshot snapshot() {
        return snapshot;
    }

    public BadgeDirectory badges() {
        return badges;
    }

    // сервер задан в моде или через -Dblooddesigner.link.url
    public boolean configured() {
        return LinkEndpoint.configured() != null;
    }

    // ---- жизненный цикл ----

    public void start() {
        worker.execute(() -> {
            if (wanted) {
                return;
            }
            LinkEndpoint target = LinkEndpoint.configured();
            if (target == null) {
                return;
            }
            wanted = true;
            endpoint = target;
            if (runtime == null && !load()) {
                wanted = false;
                return;
            }
            snapshot = snapshot.withConnection(Connection.CONNECTING);
            runtime.connection.start();
        });
    }

    public void stop() {
        worker.execute(() -> {
            wanted = false;
            if (runtime != null) {
                runtime.connection.stop();
                runtime.sync.reset();
                runtime.courier.clear();
            }
            badges.clear();
            snapshot = LinkSnapshot.initial();
        });
    }

    // при выходе из игры: туннель закрывается сразу, друзья увидят «не в сети» без задержки
    public void close() {
        stop();
        worker.shutdown();
        try {
            worker.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        io.shutdownNow();
    }

    private boolean load() {
        try {
            // ник берём при первом подключении: при создании мода игрок ещё не вошёл в лаунчер
            player = playerSource.get();
            runtime = LinkRuntime.load(folder, endpoint, player, listener, () -> snapshot, worker, io, new Hooks(),
                    () -> runtime.sync.request());
            return true;
        } catch (Exception e) {
            LOGGER.error("{}: сеть не запустилась, ключи не прочитались", BloodDesigner.NAME, e);
            return false;
        }
    }

    // ---- для интерфейса ----

    // статус, цвет и видимость; зовётся с потока игры при любом изменении
    public void updateProfile(LocalProfile profile) {
        onWorker(runtime -> runtime.sync.profile(profile));
    }

    // ники из таба: у кого из них тоже есть мод
    public void lookup(List<String> names) {
        onWorker(runtime -> {
            if (snapshot.connection() == Connection.ONLINE) {
                badges.check(runtime.connection, names, System.currentTimeMillis());
            }
        });
    }

    public CompletableFuture<String> requestFriend(String name) {
        JsonObject data = new JsonObject();
        data.addProperty("name", name);
        return action("friends.request", data);
    }

    public CompletableFuture<String> respond(String friendId, boolean accept) {
        JsonObject data = new JsonObject();
        data.addProperty("id", friendId);
        data.addProperty("accept", accept);
        return action("friends.respond", data);
    }

    public CompletableFuture<String> removeFriend(String friendId) {
        JsonObject data = new JsonObject();
        data.addProperty("id", friendId);
        return action("friends.remove", data);
    }

    // сообщение без номера: на него не поставить реакцию (для проверок сети)
    public CompletableFuture<String> sendText(String friendId, String text) {
        return sendText(friendId, text, null);
    }

    // сообщение другу по сети; id — для реакций; "ok" или код причины
    public CompletableFuture<String> sendText(String friendId, String text, String id) {
        return sendText(friendId, text, id, null);
    }

    // forwardedFrom — чьё сообщение пересылаем (null — своё)
    public CompletableFuture<String> sendText(String friendId, String text, String id, String forwardedFrom) {
        return sendPayload(friendId, new ChatPayload.Text(text, id, null, forwardedFrom));
    }

    // реакция на сообщение с этим id; reaction == null — снять свою
    public CompletableFuture<String> sendReaction(String friendId, String target, String reaction) {
        return sendPayload(friendId, new ChatPayload.Reaction(target, reaction));
    }

    private CompletableFuture<String> sendPayload(String friendId, ChatPayload payload) {
        return call(runtime -> {
            LinkFriend friend = snapshot.friend(friendId);
            if (friend == null) {
                return CompletableFuture.failedFuture(new TunnelError("not_friends"));
            }
            return runtime.outbox.send(friend, payload);
        });
    }

    // Картинка другу: шифруем своим ключом, заливаем на сервер, ключ и ссылку — сквозным сообщением.
    // Готово — придёт описание картинки (для своей переписки); отказ — исключение с кодом
    public CompletableFuture<MessageImage> sendImage(String friendId, PreparedImage prepared, String id, String caption) {
        return sendImage(friendId, prepared, id, caption, null);
    }

    public CompletableFuture<MessageImage> sendImage(String friendId, PreparedImage prepared, String id, String caption,
                                                     String forwardedFrom) {
        CompletableFuture<MessageImage> result = new CompletableFuture<>();
        worker.execute(() -> {
            LinkFriend friend = snapshot.friend(friendId);
            if (runtime == null || snapshot.connection() != Connection.ONLINE || friend == null) {
                result.completeExceptionally(new TunnelError(friend == null ? "not_friends" : "offline"));
                return;
            }
            byte[] key = MediaCrypto.newKey();
            byte[] sealed;
            try {
                sealed = MediaCrypto.seal(key, prepared.bytes());
            } catch (java.security.GeneralSecurityException e) {
                result.completeExceptionally(new TunnelError("image_crypto"));
                return;
            }
            LinkRuntime current = runtime;
            blobs.upload(current.connection, sealed).thenComposeAsync(blob -> {
                MessageImage image = new MessageImage(blob, Bytes.base64(key), prepared.mime(), prepared.width(),
                        prepared.height(), prepared.bytes().length, prepared.sha256());
                media.store(image, prepared.bytes());
                return current.outbox.send(friend, new ChatPayload.Text(caption, id, image, forwardedFrom)).thenApply(done -> image);
            }, worker).whenComplete((image, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(image);
                }
            });
        });
        return result;
    }

    // Картинка в чат BloodDesigner: её смотрят админы на портале, поэтому она не шифруется — заливается открыто
    // (official.media даёт место, PUT /v1/media/<id>) и уходит в official.send вместе с подписью
    public CompletableFuture<MessageImage> sendOfficialImage(PreparedImage prepared, String caption) {
        CompletableFuture<MessageImage> result = new CompletableFuture<>();
        worker.execute(() -> {
            if (runtime == null || snapshot.connection() != Connection.ONLINE) {
                result.completeExceptionally(new TunnelError("offline"));
                return;
            }
            LinkRuntime current = runtime;
            blobs.uploadMedia(current.connection, prepared.bytes()).thenComposeAsync(meta -> {
                MessageImage image = MessageImage.fromPortal(meta);
                if (image == null || !image.sha256().equals(prepared.sha256())) {
                    return CompletableFuture.failedFuture(new TunnelError("upload_failed"));
                }
                media.store(image, prepared.bytes());
                JsonObject data = new JsonObject();
                data.addProperty("text", caption);
                com.google.gson.JsonArray ids = new com.google.gson.JsonArray();
                ids.add(image.blob());
                data.add("images", ids);
                return current.connection.request("official.send", data).thenApply(done -> image);
            }, worker).whenComplete((image, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(image);
                }
            });
        });
        return result;
    }

    // файлы картинок переписки: кеш на диске, при надобности скачивает и расшифровывает
    public MediaCache media() {
        return media;
    }

    // ответ в чат «BloodDesigner»: уходит админам на портал; "ok" или код причины
    public CompletableFuture<String> sendOfficial(String text) {
        JsonObject data = new JsonObject();
        data.addProperty("text", text);
        return call(runtime -> runtime.connection.request("official.send", data));
    }

    // отпечаток пары для сверки голосом; null — друг неизвестен или ключи ещё не загружены.
    // Связь с сервером не нужна: всё считается из уже проверенных ключей
    public CompletableFuture<String> safetyNumber(String friendId) {
        CompletableFuture<String> result = new CompletableFuture<>();
        worker.execute(() -> {
            LinkFriend friend = snapshot.friend(friendId);
            result.complete(runtime == null || friend == null ? null : SafetyNumber.of(runtime.identity.signPublic(),
                    runtime.identity.exchangePublic(), friend.keys().signKey(), friend.keys().exchangeKey()));
        });
        return result;
    }

    private CompletableFuture<String> action(String op, JsonObject data) {
        return call(runtime -> runtime.connection.request(op, data).whenComplete((json, error) -> runtime.sync.request()));
    }

    // действие на потоке сети при открытой связи; результат — "ok" или код отказа
    private CompletableFuture<String> call(Function<LinkRuntime, CompletableFuture<?>> body) {
        CompletableFuture<String> result = new CompletableFuture<>();
        worker.execute(() -> {
            if (runtime == null || snapshot.connection() != Connection.ONLINE) {
                result.complete("offline");
                return;
            }
            body.apply(runtime).whenComplete((value, error) ->
                    result.complete(error != null ? Failures.codeOf(error) : "ok"));
        });
        return result;
    }

    private void onWorker(Consumer<LinkRuntime> body) {
        if (worker.isShutdown()) {
            return;
        }
        worker.execute(() -> {
            if (runtime != null && wanted) {
                body.accept(runtime);
            }
        });
    }

    // ---- события туннеля, поток сети ----

    private final class Hooks implements LinkRuntime.Hooks {
        @Override
        public void ready(boolean registered) {
            // при каждом подключении: пока были не в сети, могли опубликовать новое
            AnnouncementFeed.get().refreshNow();
            runtime.account.register().whenComplete((outcome, error) -> {
                if (error != null) {
                    LOGGER.debug("{}: регистрация не прошла ({})", BloodDesigner.NAME, Failures.codeOf(error));
                    return;
                }
                if (outcome == AccountFlow.Outcome.NAME_TAKEN) {
                    snapshot = snapshot.withConnection(Connection.NAME_TAKEN);
                    worker.schedule(() -> {
                        if (wanted && snapshot.connection() == Connection.NAME_TAKEN) {
                            ready(false);
                        }
                    }, NAME_TAKEN_RETRY_MS, TimeUnit.MILLISECONDS);
                    return;
                }
                runtime.prekeys.publishSigned(System.currentTimeMillis())
                        .whenComplete((done, publishError) -> runtime.sync.request());
                // аккаунт есть — забираем сообщения BloodDesigner, пришедшие, пока нас не было
                official.fetch(runtime.connection);
            });
        }

        @Override
        public void event(String event, JsonObject data) {
            switch (event) {
                case "msg" -> runtime.inbox.received(data);
                case "presence" -> presence(data);
                case "dirty" -> runtime.sync.request();
                case "prekeys.low" -> runtime.prekeys.topUp(data.has("oneTimeKeys") ? data.get("oneTimeKeys").getAsInt() : 0,
                        System.currentTimeMillis());
                case "kicked" -> runtime.connection.kicked();
                // админ опубликовал или удалил объявление на портале
                case "announcements" -> AnnouncementFeed.get().refreshNow();
                // админ написал этому игроку с портала
                case "official" -> official.fetch(runtime.connection);
                default -> { }
            }
        }

        @Override
        public void down() {
            runtime.sync.reset();
            runtime.courier.clear();
            if (wanted) {
                snapshot = snapshot.withConnection(snapshot.connection() == Connection.NAME_TAKEN ? Connection.NAME_TAKEN : Connection.OFFLINE);
            }
        }

        @Override
        public void synced(LinkSnapshot before, LinkSnapshot after, int oneTimeKeys) {
            long now = System.currentTimeMillis();
            boolean removed = false;
            for (LinkFriend old : before.friends()) {
                if (after.friend(old.id()) == null) {
                    removed = true;
                    runtime.presence.keys().forget(old.id());
                    runtime.sync.forget(old.id());
                }
            }
            if (removed) {
                // бывший друг не должен читать новые статусы
                runtime.presence.keys().rotate(now);
                runtime.sync.request();
            }
            snapshot = after;
            badges.rememberFriends(after.friends(), now);
            badges.rememberSelf(after.myName(), player.client(), after.verified(), now);
            runtime.prekeys.topUp(oneTimeKeys, now);
            runtime.courier.plan(after, now);
            runtime.inbox.drain();
        }

        @Override
        public void failed(String code) {
            if ("not_registered".equals(code)) {
                ready(false);
            }
        }

        private void presence(JsonObject data) {
            String from = data.get("from").getAsString();
            LinkFriend friend = snapshot.friend(from);
            Presence status = friend == null ? null : runtime.presence.open(from, data.get("blob").getAsString());
            if (status != null) {
                snapshot = snapshot.withFriend(friend.withPresence(status));
            }
        }
    }

    private static Thread daemon(Runnable runnable, String suffix) {
        Thread thread = new Thread(runnable, BloodDesigner.NAME + suffix);
        thread.setDaemon(true);
        return thread;
    }
}
