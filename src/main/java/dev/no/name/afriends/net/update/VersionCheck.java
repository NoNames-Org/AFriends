package dev.no.name.afriends.net.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.net.service.LinkEndpoint;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Сверка версии мода с сервером: тот же хост, что и сеть, открытый эндпоинт /v1/version (его же читает портал).
 * Запрос обычный HTTPS, без туннеля и аккаунта — проверка работает и с выключенной сетью. Своя версия и авторы
 * берутся из метаданных мода (fabric.mod.json), последняя — из последнего релиза на GitHub, который сервер
 * раздаёт в /v1/version. Используется панелью «О моде» в настройках
 */
public final class VersionCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final long REFRESH_MS = 600_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final String current = readCurrent();
    private final String authors = readAuthors();
    private volatile String latest;
    private volatile boolean checked;
    private volatile long fetchedAt;
    private boolean loading;
    private HttpClient client;

    public VersionCheck() {
    }

    /** Своя версия без суффикса сборки, например «2.0.0». */
    public String current() {
        return current;
    }

    /** «Fell, Aloiner» — авторы из fabric.mod.json. */
    public String authors() {
        return authors;
    }

    /** Последняя версия с сервера или null, пока не загрузилась. */
    public String latest() {
        return latest;
    }

    /** Ответ с сервера уже получен (чтобы отличать «проверяем» от «актуально»). */
    public boolean checked() {
        return checked;
    }

    /** Текущая версия старше последней — пора обновляться. */
    public boolean outdated() {
        String newest = latest;
        return newest != null && compare(current, newest) < 0;
    }

    /** Зовётся каждый кадр панели «О моде»; сам решает, пора ли спрашивать сервер. */
    public void refresh() {
        if (System.currentTimeMillis() - fetchedAt >= REFRESH_MS) {
            fetch();
        }
    }

    private synchronized void fetch() {
        if (loading) {
            return;
        }
        URI address = address();
        if (address == null) {
            return;
        }
        loading = true;
        HttpRequest request = HttpRequest.newBuilder(address).timeout(TIMEOUT).header("Accept", "application/json").GET().build();
        client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            try {
                if (error == null && response.statusCode() == 200) {
                    String parsed = parseLatest(response.body());
                    if (parsed != null) {
                        latest = parsed;
                        checked = true;
                    }
                } else {
                    LOGGER.debug("{}: версия не проверилась ({})", AFriends.NAME,
                            error != null ? error.getClass().getSimpleName() : response.statusCode());
                }
            } catch (RuntimeException e) {
                LOGGER.debug("{}: ответ /v1/version не разобрался", AFriends.NAME, e);
            } finally {
                fetchedAt = System.currentTimeMillis();
                loading = false;
            }
        });
    }

    private synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(TIMEOUT).build();
        }
        return client;
    }

    // тот же сервер, что и у сети: wss://host/v1/tunnel → https://host/v1/version
    private static URI address() {
        LinkEndpoint endpoint = LinkEndpoint.configured();
        if (endpoint == null) {
            return null;
        }
        URI tunnel = endpoint.address();
        String scheme = "ws".equalsIgnoreCase(tunnel.getScheme()) ? "http" : "https";
        int port = tunnel.getPort();
        return URI.create(scheme + "://" + tunnel.getHost() + (port > 0 ? ":" + port : "") + "/v1/version");
    }

    // { "latest": { "version": "2.1.0", ... }, ... }
    private static String parseLatest(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("latest")) {
            return null;
        }
        JsonElement latest = root.getAsJsonObject().get("latest");
        if (!latest.isJsonObject()) {
            return null;
        }
        JsonObject object = latest.getAsJsonObject();
        return object.has("version") ? object.get("version").getAsString().strip() : null;
    }

    private static String readCurrent() {
        return FabricLoader.getInstance().getModContainer(AFriends.MOD_ID)
                .map(container -> stripBuild(container.getMetadata().getVersion().getFriendlyString()))
                .orElse("?");
    }

    private static String readAuthors() {
        return FabricLoader.getInstance().getModContainer(AFriends.MOD_ID)
                .map(container -> container.getMetadata().getAuthors().stream()
                        .map(Person::getName).collect(Collectors.joining(", ")))
                .filter(text -> !text.isBlank())
                .orElse("");
    }

    // «2.0.0+1.21.11» → «2.0.0»
    private static String stripBuild(String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }

    // сравнение по числам: 1.10.0 новее 1.9.0; неразобранные части считаем нулями
    static int compare(String left, String right) {
        String[] a = left.split("[.+-]");
        String[] b = right.split("[.+-]");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int diff = part(a, i) - part(b, i);
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    private static int part(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
