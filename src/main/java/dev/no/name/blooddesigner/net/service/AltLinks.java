package dev.no.name.blooddesigner.net.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.crypto.SignLabel;
import dev.no.name.blooddesigner.net.identity.Identity;
import dev.no.name.blooddesigner.net.identity.IdentityStore;
import dev.no.name.blooddesigner.net.rpc.LinkCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Твинки: аккаунты сети, чьи ключи лежат на этом же компьютере. Каждый такой аккаунт подписывает id текущего
 * своим ключом, сервер проверяет подпись его открытым ключом — подделать связь чужого аккаунта нельзя.
 * Шлём при каждом входе в сеть до регистрации ника: если ник держит свой же твинк (у ника сменились ключи),
 * сервер передаст ник текущему аккаунту. Аккаунты, которые ещё ни разу не заходили, сервер не знает и пропускает
 */
final class AltLinks {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    static final int MAX = 20;

    private AltLinks() {
    }

    // готово, когда сервер ответил (или нечего слать); ошибка не мешает дальнейшему входу
    static CompletableFuture<Void> report(LinkCalls calls, Identity current, List<Path> others) {
        JsonArray proofs = new JsonArray();
        byte[] message = current.id().getBytes(StandardCharsets.US_ASCII);
        for (Path folder : others) {
            if (proofs.size() >= MAX) {
                break;
            }
            try {
                Identity other = new IdentityStore(folder.resolve("identity.json")).loadExisting();
                if (other == null || other.id().equals(current.id())) {
                    continue;
                }
                JsonObject proof = new JsonObject();
                proof.addProperty("key", Bytes.base64(other.signPublic()));
                proof.addProperty("sig", Bytes.base64(other.sign(SignLabel.ALT, message)));
                proofs.add(proof);
            } catch (Exception e) {
                LOGGER.debug("{}: ключи в {} не прочитались", BloodDesigner.NAME, folder, e);
            }
        }
        if (proofs.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        JsonObject data = new JsonObject();
        data.add("proofs", proofs);
        return calls.request("alts.link", data).handle((done, error) -> {
            if (error != null) {
                LOGGER.debug("{}: связь аккаунтов не ушла", BloodDesigner.NAME, error);
            }
            return null;
        });
    }
}
