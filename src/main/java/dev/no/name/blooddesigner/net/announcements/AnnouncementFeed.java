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
import java.util.List;
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

    private final AtomicBoolean loading = new AtomicBoolean();
    // событие пришло, пока шёл прошлый запрос: его ответ мог уже устареть, спросим ещё раз
    private final AtomicBoolean again = new AtomicBoolean();
    private volatile List<PrivateMessage> messages = List.of();
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
                    messages = parse(response.body());
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
            client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
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

    private static List<PrivateMessage> parse(String body) {
        List<PrivateMessage> result = new ArrayList<>();
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("announcements")) {
            return List.of();
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
            if (!text.isEmpty() || !images.isEmpty()) {
                result.add(new PrivateMessage(false, text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text,
                        json.get("created").getAsLong(), null, images, null, null));
            }
        }
        result.sort(Comparator.comparingLong(PrivateMessage::time));
        return List.copyOf(result);
    }
}
