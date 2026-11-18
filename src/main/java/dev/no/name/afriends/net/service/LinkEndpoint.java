package dev.no.name.afriends.net.service;

import dev.no.name.afriends.net.crypto.Bytes;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

// Куда подключаться и каким ключам сервера верить. Ключи зашиты в мод: туннель Noise не откроется
// к серверу с другим ключом, даже если TLS подделан. Список — текущий и следующий, для смены ключа.
// -Dafriends.link.url и -Dafriends.link.key (base64) — для проверки на своём сервере
public record LinkEndpoint(URI address, List<byte[]> serverKeys) {
    private static final String DEFAULT_URL = "wss://link.no-names.com/v1/tunnel";
    // при смене ключа сервера новый сначала добавляется сюда вторым, и только потом включается на сервере
    private static final List<String> DEFAULT_KEYS = List.of("l4xs+L9TYJQmTTVxUCqAzceNK2tOTuKpX4K4e7aV/wI=");

    // null — сервер не задан или ключ испорчен: сеть не подключается
    public static LinkEndpoint configured() {
        String url = System.getProperty("afriends.link.url", DEFAULT_URL);
        String override = System.getProperty("afriends.link.key");
        List<String> encoded = override != null ? List.of(override) : DEFAULT_KEYS;
        if (url.isBlank() || encoded.isEmpty()) {
            return null;
        }
        List<byte[]> keys = new ArrayList<>();
        for (String key : encoded) {
            byte[] raw = Bytes.fromBase64(key.strip());
            if (raw.length == 32) {
                keys.add(raw);
            }
        }
        return keys.isEmpty() ? null : new LinkEndpoint(URI.create(url), List.copyOf(keys));
    }
}
