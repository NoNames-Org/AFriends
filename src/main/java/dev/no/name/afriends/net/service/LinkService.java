package dev.no.name.afriends.net.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.net.announcements.AnnouncementFeed;
import dev.no.name.afriends.net.badges.BadgeDirectory;
import dev.no.name.afriends.net.chat.ChatPayload;
import dev.no.name.afriends.net.chat.InboxListener;
import dev.no.name.afriends.net.chat.OfficialInbox;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.net.crypto.Bytes;
import dev.no.name.afriends.net.media.BlobClient;
import dev.no.name.afriends.net.media.MediaCache;
import dev.no.name.afriends.net.media.MediaCrypto;
import dev.no.name.afriends.net.media.PreparedImage;
import dev.no.name.afriends.net.e2e.SafetyNumber;
import dev.no.name.afriends.net.model.Connection;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.model.LinkGroup;
import dev.no.name.afriends.net.model.LinkSnapshot;
import dev.no.name.afriends.net.model.Presence;
import dev.no.name.afriends.net.rpc.Failures;
import dev.no.name.afriends.net.tunnel.TunnelError;
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

// Сеть AFriends целиком. Наружу — снимок для интерфейса и действия, всё остальное на своём потоке:
// туннель, ключи и сессии сквозного шифрования не потокобезопасны и трогаются только оттуда.
// Методы можно звать с потока игры; будущие завершаются на потоке сети
public final class LinkService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final long NAME_TAKEN_RETRY_MS = 600_000;

    // папка ключей текущего аккаунта Minecraft; меняется, когда игрок сменил аккаунт прямо в игре
    private Path folder;
    // папки ключей других аккаунтов этого компьютера: из них подтверждаем твинков
    private List<Path> siblings = List.of();
    private final Supplier<PlayerAccount> playerSource;
    private PlayerAccount player;
    private final InboxListener listener;
    private OfficialInbox official;
    private final BlobClient blobs = new BlobClient();
    private final MediaCache media;
    private final AnnouncementFeed announcements;
    private final FriendsGateway friends;
    private final DirectGateway direct;
    private final GroupGateway groups;
    private final ProfileGateway profile;
    private final OfficialGateway officialGateway;
    private final BadgeDirectory badges = new BadgeDirectory();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> daemon(runnable, " link"));
    private final ExecutorService io = Executors.newCachedThreadPool(runnable -> daemon(runnable, " link io"));
    private volatile LinkSnapshot snapshot = LinkSnapshot.initial();
    private LinkRuntime runtime;
    private boolean wanted;
    private volatile LinkEndpoint endpoint;

    // mediaFolder — кэш картинок переписки, общий для всех аккаунтов: он зашифрован и ничей
    // announcements — лента объявлений: голоса в опросах идут через сеть, событие сервера обновляет ленту
    public LinkService(Path folder, Path mediaFolder, Supplier<PlayerAccount> player, InboxListener listener,
                       AnnouncementFeed announcements) {
        this.folder = folder;
        this.playerSource = player;
        this.listener = listener;
        this.official = new OfficialInbox(folder.resolve("official-last.txt"), listener);
        this.media = new MediaCache(mediaFolder, blobs);
        this.announcements = announcements;
        announcements.votes().setRequester(this::query);
        LinkCalls calls = new Calls();
        this.friends = new FriendsGateway(calls);
        this.direct = new DirectGateway(calls);
        this.groups = new GroupGateway(calls);
        this.profile = new ProfileGateway(calls);
        this.officialGateway = new OfficialGateway(calls);
    }

    public LinkSnapshot snapshot() {
        return snapshot;
    }

    public BadgeDirectory badges() {
        return badges;
    }

    // сервер задан в моде или через -Dafriends.link.url
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

    /**
     * Игрок сменил аккаунт Minecraft: отключаемся от сети старым аккаунтом и, если сеть была включена,
     * подключаемся заново ключами нового. others — папки ключей других аккаунтов этого компьютера
     */
    public void switchAccount(Path next, List<Path> others) {
        worker.execute(() -> {
            siblings = List.copyOf(others);
            if (next.equals(folder)) {
                return;
            }
            boolean was = wanted;
            wanted = false;
            if (runtime != null) {
                runtime.connection.stop();
                runtime.sync.reset();
                runtime.courier.clear();
            }
            runtime = null;
            badges.clear();
            snapshot = LinkSnapshot.initial();
            folder = next;
            official = new OfficialInbox(folder.resolve("official-last.txt"), listener);
            if (was) {
                start();
            }
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
            LOGGER.error("{}: сеть не запустилась, ключи не прочитались", AFriends.NAME, e);
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

    // ---- операции, по смыслу ----

    public FriendsGateway friends() {
        return friends;
    }

    public DirectGateway direct() {
        return direct;
    }

    public GroupGateway groups() {
        return groups;
    }

    public ProfileGateway profile() {
        return profile;
    }

    public OfficialGateway official() {
        return officialGateway;
    }

    // ---- транспорт для шлюзов: всё на потоке сети, при открытой связи ----

    private CompletableFuture<String> sendToGroup(String groupId, ChatPayload payload) {
        return call(runtime -> {
            LinkGroup group = snapshot.group(groupId);
            if (group == null) {
                return CompletableFuture.failedFuture(new TunnelError("not_member"));
            }
            CompletableFuture<?>[] sends = group.members().stream()
                    .map(member -> runtime.outbox.send(member, payload)).toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(sends);
        });
    }

    private CompletableFuture<String> sendPayload(String friendId, ChatPayload payload) {
        return call(runtime -> {
            // друг, собеседник личного чата или участник общей группы — сервер пропустит всех троих
            LinkFriend friend = snapshot.peer(friendId);
            if (friend == null) {
                return CompletableFuture.failedFuture(new TunnelError("not_friends"));
            }
            return runtime.outbox.send(friend, payload);
        });
    }

    // шифруем своим ключом и заливаем на сервер; готово — описание картинки с ключом (кладём и в свой кэш)
    private CompletableFuture<MessageImage> uploadImage(PreparedImage prepared) {
        CompletableFuture<MessageImage> result = new CompletableFuture<>();
        worker.execute(() -> {
            if (runtime == null || snapshot.connection() != Connection.ONLINE) {
                result.completeExceptionally(new TunnelError("offline"));
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
            blobs.upload(runtime.connection, sealed).whenComplete((blob, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                    return;
                }
                MessageImage image = new MessageImage(blob, Bytes.base64(key), prepared.mime(), prepared.width(),
                        prepared.height(), prepared.bytes().length, prepared.sha256());
                media.store(image, prepared.bytes());
                result.complete(image);
            });
        });
        return result;
    }

    // Картинка в чат AFriends: её смотрят админы на портале, поэтому она не шифруется — заливается открыто
    // (official.media даёт место, PUT /v1/media/<id>) и уходит в official.send вместе с подписью
    private CompletableFuture<MessageImage> sendOfficialImage(PreparedImage prepared, String caption) {
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
                JsonArray ids = new JsonArray();
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

    // запрос с ответом: null, если связи нет или сервер отказал (опросы объявлений)
    private CompletableFuture<JsonObject> query(String op, JsonObject data) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        worker.execute(() -> {
            if (runtime == null || snapshot.connection() != Connection.ONLINE) {
                result.complete(null);
                return;
            }
            runtime.connection.request(op, data).whenComplete((json, error) -> result.complete(error != null ? null : json));
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

    /** Транспорт, который видят шлюзы: только отправка и запросы, без жизненного цикла связи. */
    private final class Calls implements LinkCalls {
        @Override
        public CompletableFuture<String> action(String op, JsonObject data) {
            return LinkService.this.action(op, data);
        }

        @Override
        public CompletableFuture<JsonObject> query(String op, JsonObject data) {
            return LinkService.this.query(op, data);
        }

        @Override
        public CompletableFuture<String> request(String op, JsonObject data) {
            return call(runtime -> runtime.connection.request(op, data));
        }

        @Override
        public CompletableFuture<String> send(String peerId, ChatPayload payload) {
            return sendPayload(peerId, payload);
        }

        @Override
        public CompletableFuture<String> sendToGroup(String groupId, ChatPayload payload) {
            return LinkService.this.sendToGroup(groupId, payload);
        }

        @Override
        public CompletableFuture<MessageImage> upload(PreparedImage prepared) {
            return uploadImage(prepared);
        }

        @Override
        public CompletableFuture<MessageImage> sendOfficialImage(PreparedImage prepared, String caption) {
            return LinkService.this.sendOfficialImage(prepared, caption);
        }

        @Override
        public void requestSync() {
            onWorker(runtime -> runtime.sync.request());
        }
    }

    private final class Hooks implements LinkRuntime.Hooks {
        @Override
        public void ready(boolean registered) {
            // при каждом подключении: пока были не в сети, могли опубликовать новое
            announcements.refreshNow();
            // сначала твинки: ник, занятый своим же твинком, тогда перейдёт к этому аккаунту, а не выйдет «занят»
            AltLinks.report(runtime.connection, runtime.identity, siblings)
                    .thenCompose(linked -> runtime.account.register()).whenComplete((outcome, error) -> {
                if (error != null) {
                    LOGGER.debug("{}: регистрация не прошла ({})", AFriends.NAME, Failures.codeOf(error));
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
                // аккаунт есть — забираем сообщения AFriends, пришедшие, пока нас не было
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
                case "announcements" -> announcements.refreshNow();
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
            badges.rememberSelf(after.myName(), after.verified(), now);
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
        Thread thread = new Thread(runnable, AFriends.NAME + suffix);
        thread.setDaemon(true);
        return thread;
    }
}
