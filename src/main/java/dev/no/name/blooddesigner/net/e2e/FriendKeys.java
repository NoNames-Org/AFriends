package dev.no.name.blooddesigner.net.e2e;

import dev.no.name.blooddesigner.net.crypto.DhKey;
import dev.no.name.blooddesigner.net.crypto.SignKey;
import dev.no.name.blooddesigner.net.crypto.SignLabel;
import dev.no.name.blooddesigner.net.identity.Identity;

// Открытые ключи друга, уже проверенные: id выведен из ключа подписи, ключ обмена подписан им.
// Сервер такие ключи подменить не может — подмена не пройдёт одну из двух проверок
public record FriendKeys(String id, byte[] signKey, byte[] exchangeKey) {
    public static FriendKeys verified(String expectedId, byte[] signKey, byte[] exchangeKey, byte[] keyCardSignature) throws E2eException {
        if (signKey == null || signKey.length != SignKey.KEY_BYTES || !Identity.idOf(signKey).equals(expectedId)) {
            throw new E2eException("id_mismatch");
        }
        if (exchangeKey == null || exchangeKey.length != DhKey.KEY_BYTES
                || !SignKey.verify(signKey, SignLabel.KEYCARD, exchangeKey, keyCardSignature)) {
            throw new E2eException("bad_keycard");
        }
        return new FriendKeys(expectedId, signKey.clone(), exchangeKey.clone());
    }
}
