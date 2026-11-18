package dev.no.name.afriends.net.crypto;

// назначения подписей; сами строки — в ProtocolLabels
public enum SignLabel {
    AUTH(ProtocolLabels.SIGN_AUTH),
    KEYCARD(ProtocolLabels.SIGN_KEYCARD),
    SIGNED_PREKEY(ProtocolLabels.SIGN_SIGNED_PREKEY),
    PQ_PREKEY(ProtocolLabels.SIGN_PQ_PREKEY),
    // другой аккаунт этого компьютера подтверждает, что он твинк текущего (подписывает его id)
    ALT(ProtocolLabels.SIGN_ALT);

    private final byte[] bytes;

    SignLabel(String text) {
        this.bytes = Bytes.ascii(text);
    }

    byte[] bytes() {
        return bytes;
    }
}
