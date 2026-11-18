package dev.no.name.afriends.net.identity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.afriends.net.crypto.Bytes;
import dev.no.name.afriends.net.crypto.DhKey;
import dev.no.name.afriends.net.crypto.SignKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

// файл с ключами личности. Создаётся при первом запуске; потерять его — значит стать новым аккаунтом
public final class IdentityStore {
    private final Path file;

    public IdentityStore(Path file) {
        this.file = file;
    }

    // ключи, если они уже есть; null — этот аккаунт ещё ни разу не заходил в сеть
    public Identity loadExisting() throws IOException, GeneralSecurityException {
        return Files.exists(file) ? loadOrCreate() : null;
    }

    public Identity loadOrCreate() throws IOException, GeneralSecurityException {
        if (Files.exists(file)) {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return new Identity(
                    SignKey.restore(bytes(json, "signSecret"), bytes(json, "signPublic")),
                    DhKey.restore(bytes(json, "exchangeSecret"), bytes(json, "exchangePublic")));
        }
        Identity identity = Identity.generate();
        JsonObject json = new JsonObject();
        json.addProperty("signSecret", Bytes.base64(identity.signKey().encodedSecret()));
        json.addProperty("signPublic", Bytes.base64(identity.signPublic()));
        json.addProperty("exchangeSecret", Bytes.base64(identity.exchange().encodedSecret()));
        json.addProperty("exchangePublic", Bytes.base64(identity.exchangePublic()));
        SecretFiles.write(file, json.toString());
        return identity;
    }

    private static byte[] bytes(JsonObject json, String field) {
        return Bytes.fromBase64(json.get(field).getAsString());
    }
}
