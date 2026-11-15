package dev.no.name.blooddesigner.net.crypto;

// назначения подписей; строки обязаны совпадать с сервером (cloud/docs/CRYPTO.md)
public enum SignLabel {
    AUTH("smphelper-link-auth-v1\n"),
    KEYCARD("smphelper-keycard-v1\n"),
    SIGNED_PREKEY("smphelper-spk-v1\n"),
    PQ_PREKEY("smphelper-pqspk-v1\n");

    private final byte[] bytes;

    SignLabel(String text) {
        this.bytes = Bytes.ascii(text);
    }

    byte[] bytes() {
        return bytes;
    }
}
