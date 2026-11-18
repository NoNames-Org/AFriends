package dev.no.name.afriends.net.presence;

import dev.no.name.afriends.net.crypto.Bytes;
import dev.no.name.afriends.net.e2e.E2eException;
import dev.no.name.afriends.net.model.Presence;

import java.nio.charset.StandardCharsets;

// Шифрует свой статус для друзей и читает их статусы. Сами ключи — в PresenceKeys
public final class PresenceBook {
    private final String myId;
    private final PresenceKeys keys;

    public PresenceBook(String myId, PresenceKeys keys) {
        this.myId = myId;
        this.keys = keys;
    }

    public PresenceKeys keys() {
        return keys;
    }

    // блоб статуса для одного друга, base64 для поля presence в sync
    public String seal(String friendId, Presence presence, long now) throws E2eException {
        PresenceKeys.Key own = keys.own(now);
        byte[] plain = PresenceCodec.encode(presence).getBytes(StandardCharsets.UTF_8);
        return Bytes.base64(PresenceCipher.seal(own.secret(), own.epoch(), myId, friendId, plain));
    }

    // null — ключа этой эпохи ещё нет (придёт сообщением), блоб подделан или формат чужой
    public Presence open(String friendId, String blob) {
        try {
            PresenceCipher.Sealed sealed = PresenceCipher.parse(Bytes.fromBase64(blob));
            byte[] key = keys.friendKey(friendId, sealed.epoch());
            if (key == null) {
                return null;
            }
            byte[] plain = PresenceCipher.open(key, sealed, friendId, myId);
            return PresenceCodec.decode(new String(plain, StandardCharsets.UTF_8));
        } catch (E2eException | IllegalArgumentException e) {
            return null;
        }
    }
}
