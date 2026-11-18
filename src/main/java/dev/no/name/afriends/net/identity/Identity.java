package dev.no.name.afriends.net.identity;

import dev.no.name.afriends.net.crypto.DhKey;
import dev.no.name.afriends.net.crypto.Hkdf;
import dev.no.name.afriends.net.crypto.SignKey;
import dev.no.name.afriends.net.crypto.SignLabel;

import java.util.Arrays;
import java.util.HexFormat;

// Личность этой установки мода: ключ подписи (из него выводится id) и ключ обмена, подписанный первым.
// Секретные половины не покидают компьютер
public final class Identity {
    private final SignKey sign;
    private final DhKey exchange;

    public Identity(SignKey sign, DhKey exchange) {
        this.sign = sign;
        this.exchange = exchange;
    }

    public static Identity generate() {
        return new Identity(SignKey.generate(), DhKey.generate());
    }

    // id аккаунта: первые 16 байт SHA-256 от открытого ключа подписи, в hex — так же считает сервер
    public String id() {
        return idOf(sign.publicRaw());
    }

    public static String idOf(byte[] signPublic) {
        return HexFormat.of().formatHex(Arrays.copyOf(Hkdf.sha256(signPublic), 16));
    }

    public byte[] signPublic() {
        return sign.publicRaw();
    }

    public byte[] exchangePublic() {
        return exchange.publicRaw();
    }

    public byte[] sign(SignLabel label, byte[] message) {
        return sign.sign(label, message);
    }

    // подпись ключа обмена ключом личности: сервер не сможет подменить его друзьям
    public byte[] keyCardSignature() {
        return sign.sign(SignLabel.KEYCARD, exchange.publicRaw());
    }

    public DhKey exchange() {
        return exchange;
    }

    SignKey signKey() {
        return sign;
    }
}
