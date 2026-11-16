package dev.no.name.blooddesigner.net.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.net.rpc.LinkCalls;
import dev.no.name.blooddesigner.net.service.LinkEndpoint;
import dev.no.name.blooddesigner.net.tunnel.TunnelError;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Зашифрованные вложения на сервере сети. Место берётся через туннель (blob.reserve — сервер знает, чей аккаунт),
 * а сами байты идут обычным HTTPS: PUT с одноразовым токеном и GET по id
 */
public final class BlobClient {
    private static final Duration CONNECT = Duration.ofSeconds(10);
    private static final Duration TRANSFER = Duration.ofSeconds(90);
    // шифротекст: картинка + nonce и тег
    private static final long MAX_BLOB = MessageImage.MAX_SIZE + 1024;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT).build();

    // id залитого вложения; reserve — с потока сети, ответ тоже приходит туда
    public CompletableFuture<String> upload(LinkCalls calls, byte[] sealed) {
        JsonObject data = new JsonObject();
        data.addProperty("size", sealed.length);
        return calls.request("blob.reserve", data).thenCompose(json -> {
            String id = json.get("id").getAsString();
            URI target;
            try {
                target = address("/v1/blobs/", id);
            } catch (TunnelError e) {
                return CompletableFuture.failedFuture(e);
            }
            HttpRequest request = builder(target)
                    .header("Authorization", "Bearer " + json.get("token").getAsString())
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(sealed)).build();
            return http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new TunnelError("upload_failed");
                }
                return id;
            });
        });
    }

    // картинка портала: открытая, без ключа; /v1/media/<id>
    // открытая картинка для чата BloodDesigner: место через туннель (official.media), PUT /v1/media/<id>;
    // ответ — описание картинки, как у картинок портала
    public CompletableFuture<JsonObject> uploadMedia(LinkCalls calls, byte[] plain) {
        JsonObject data = new JsonObject();
        data.addProperty("size", plain.length);
        return calls.request("official.media", data).thenCompose(json -> {
            String id = json.get("id").getAsString();
            URI target;
            try {
                target = address("/v1/media/", id);
            } catch (TunnelError e) {
                return CompletableFuture.failedFuture(e);
            }
            HttpRequest request = builder(target)
                    .header("Authorization", "Bearer " + json.get("token").getAsString())
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(plain)).build();
            return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new TunnelError("upload_failed");
                }
                return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("media");
            });
        });
    }

    public CompletableFuture<byte[]> downloadMedia(String id) {
        return fetch(id, "/v1/media/");
    }

    public CompletableFuture<byte[]> download(String id) {
        return fetch(id, "/v1/blobs/");
    }

    private CompletableFuture<byte[]> fetch(String id, String prefix) {
        URI target;
        try {
            target = address(prefix, id);
        } catch (TunnelError e) {
            return CompletableFuture.failedFuture(e);
        }
        HttpRequest request = builder(target).GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() == 404) {
                // прошло 30 дней или отправитель так и не залил
                throw new TunnelError("image_expired");
            }
            if (response.statusCode() != 200 || response.body().length > MAX_BLOB) {
                throw new TunnelError("download_failed");
            }
            return response.body();
        });
    }

    // по голому http (свой сервер для проверки) HttpClient пробует перейти на HTTP/2 через Upgrade: h2c,
    // а сервер сети принимает Upgrade за вход в туннель — поэтому там явно HTTP/1.1. По https HTTP/2 договаривается сам
    private static HttpRequest.Builder builder(URI target) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target).timeout(TRANSFER);
        return "http".equals(target.getScheme()) ? builder.version(HttpClient.Version.HTTP_1_1) : builder;
    }

    // тот же сервер, что и у сети: wss://host/v1/tunnel → https://host/v1/blobs/<id>
    private static URI address(String prefix, String id) {
        LinkEndpoint endpoint = LinkEndpoint.configured();
        if (endpoint == null) {
            throw new TunnelError("offline");
        }
        URI tunnel = endpoint.address();
        String scheme = "ws".equalsIgnoreCase(tunnel.getScheme()) ? "http" : "https";
        int port = tunnel.getPort();
        return URI.create(scheme + "://" + tunnel.getHost() + (port > 0 ? ":" + port : "") + prefix + id);
    }
}
