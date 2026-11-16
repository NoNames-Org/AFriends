package dev.no.name.blooddesigner.net.announcements;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.net.service.LinkEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

// Объявления модов с портала portal.no-names.com. Это открытый список, поэтому он берётся обычным HTTPS-запросом,
// без туннеля и аккаунта: объявления видны и тем, у кого сеть выключена.
// Свежими они приходят двумя путями: сервер сети шлёт событие «announcements», как только админ что-то
// опубликовал или удалил (refreshNow), а пока открыт экран сообщений, список сверяется раз в 15 секунд (refresh)
public final class AnnouncementFeed {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    private static final AnnouncementFeed INSTANCE = new AnnouncementFeed();
    private static final long REFRESH_MS = 15_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_TEXT = 600;
    private static final String ID_PREFIX = "ann";

    private final AtomicBoolean loading = new AtomicBoolean();
    // событие пришло, пока шёл прошлый запрос: его ответ мог уже устареть, спросим ещё раз
    private final AtomicBoolean again = new AtomicBoolean();
    private volatile List<PrivateMessage> messages = List.of();
    // опросы по id сообщения объявления («ann» + id на сервере)
    private volatile Map<String, AnnouncementPoll> polls = Map.of();
    private final PollVotes votes = new PollVotes();
    private volatile long fetchedAt;
    private HttpClient client;

    private AnnouncementFeed() {
    }

    public static AnnouncementFeed get() {
        return INSTANCE;
    }

    // старые сверху, как в любом чате; пусто, пока не загрузилось или объявлений нет
    public List<PrivateMessage> messages() {
        return messages;
    }

    public PollVotes votes() {
        return votes;
    }

    // опрос этого объявления или null
    public AnnouncementPoll poll(PrivateMessage message) {
        String id = message.id();
        return id == null || !id.startsWith(ID_PREFIX) ? null : polls.get(id);
    }

    // проголосовали — подставляем свежие числа, не дожидаясь следующей загрузки ленты
    public void replacePoll(AnnouncementPoll poll) {
        Map<String, AnnouncementPoll> next = new HashMap<>(polls);
        next.put(ID_PREFIX + poll.announcement(), poll);
        polls = Map.copyOf(next);
    }

    // зовётся каждый кадр экрана сообщений; сам решает, пора ли спрашивать сервер
    public void refresh() {
        if (System.currentTimeMillis() - fetchedAt >= REFRESH_MS) {
            fetch();
        }
    }

    // объявления точно изменились: спросить сразу, без ожидания
    public void refreshNow() {
        again.set(true);
        fetch();
    }

    private void fetch() {
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        again.set(false);
        URI address = address();
        if (address == null) {
            loading.set(false);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(address).timeout(TIMEOUT).header("Accept", "application/json").GET().build();
        client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            try {
                if (error == null && response.statusCode() == 200) {
                    Parsed parsed = parse(response.body(), votes);
                    messages = parsed.messages();
                    polls = parsed.polls();
                    // свой выбор помнит сервер: подтягиваем, чтобы отметить вариант и на другом компьютере
                    votes.loadMine(parsed.polls().values().stream().map(AnnouncementPoll::announcement).toList(),
                            () -> polls = withMine(polls));
                } else {
                    LOGGER.debug("{}: объявления не загрузились ({})", BloodDesigner.NAME,
                            error != null ? error.getClass().getSimpleName() : response.statusCode());
                }
            } catch (RuntimeException e) {
                LOGGER.debug("{}: ответ с объявлениями не разобрался", BloodDesigner.NAME, e);
            } finally {
                // и при сбое ждём обычный интервал, а не долбим сервер каждый кадр
                fetchedAt = System.currentTimeMillis();
                loading.set(false);
                if (again.get()) {
                    fetch();
                }
            }
        });
    }

    private synchronized HttpClient client() {
        if (client == null) {
            // HTTP/1.1: по http:// клиент Java шлёт Upgrade: h2c, и сервер сети принимает это за WebSocket
            client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(TIMEOUT).build();
        }
        return client;
    }

    // тот же сервер, что и у сети: wss://host/v1/tunnel → https://host/v1/announcements
    private static URI address() {
        LinkEndpoint endpoint = LinkEndpoint.configured();
        if (endpoint == null) {
            return null;
        }
        URI tunnel = endpoint.address();
        String scheme = "ws".equalsIgnoreCase(tunnel.getScheme()) ? "http" : "https";
        int port = tunnel.getPort();
        return URI.create(scheme + "://" + tunnel.getHost() + (port > 0 ? ":" + port : "") + "/v1/announcements");
    }

    private record Parsed(List<PrivateMessage> messages, Map<String, AnnouncementPoll> polls) {
    }

    private Map<String, AnnouncementPoll> withMine(Map<String, AnnouncementPoll> current) {
        Map<String, AnnouncementPoll> next = new HashMap<>();
        current.forEach((id, poll) -> next.put(id, poll.withMine(votes.mine(poll.announcement()))));
        return Map.copyOf(next);
    }

    private static Parsed parse(String body, PollVotes votes) {
        List<PrivateMessage> result = new ArrayList<>();
        Map<String, AnnouncementPoll> polls = new HashMap<>();
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("announcements")) {
            return new Parsed(List.of(), Map.of());
        }
        for (JsonElement item : root.getAsJsonObject().getAsJsonArray("announcements")) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject json = item.getAsJsonObject();
            if (!json.has("text") || !json.has("created")) {
                continue;
            }
            String text = json.get("text").getAsString().strip();
            // картинки портала; где они в тексте — метки [img]
            List<MessageImage> images = MessageImage.listFromPortal(json);
            // id объявления нужен опросу: по нему голос уходит на сервер
            long announcement = json.has("id") ? json.get("id").getAsLong() : 0;
            String id = announcement > 0 ? ID_PREFIX + announcement : null;
            AnnouncementPoll poll = announcement > 0
                    ? AnnouncementPoll.parse(announcement, json.get("poll"), votes.mine(announcement)) : null;
            if (poll != null) {
                polls.put(id, poll);
            }
            if (!text.isEmpty() || !images.isEmpty()) {
                result.add(new PrivateMessage(false, text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text,
                        json.get("created").getAsLong(), id, images, null, null));
            }
        }
        result.sort(Comparator.comparingLong(PrivateMessage::time));
        return new Parsed(List.copyOf(result), Map.copyOf(polls));
    }
}
