package dev.no.name.afriends.net.crypto;

/**
 * Строки-метки криптопротокола сети. Это байты протокола, а не название мода: они входят в подписи, KDF и
 * рукопожатие, и обязаны совпадать с сервером (nonames-cloud, docs/CRYPTO.md) и со всеми уже выданными
 * ключами. Префиксы остались от первых версий сети; смена любой строки сломает ключи и переписки у всех игроков.
 */
public final class ProtocolLabels {
    public static final String SIGN_AUTH = "smphelper-link-auth-v1\n";
    public static final String SIGN_KEYCARD = "smphelper-keycard-v1\n";
    public static final String SIGN_SIGNED_PREKEY = "smphelper-spk-v1\n";
    public static final String SIGN_PQ_PREKEY = "smphelper-pqspk-v1\n";
    public static final String SIGN_ALT = "smphelper-alt-v1\n";

    public static final String TUNNEL_PROLOGUE = "smphelper-link-tunnel-v1";
    public static final String PQXDH_INFO = "smphelper-pqxdh-v1";
    public static final String E2E_DOMAIN = "smphelper-e2e-v1";
    public static final String SAFETY_DOMAIN = "smphelper-safety-v1";
    public static final String RATCHET_INFO = "smphelper-ratchet-v1";
    public static final String MESSAGE_INFO = "smphelper-msg-v1";
    public static final String PRESENCE = "smphelper-presence-v2";
    public static final String MEDIA = "blooddesigner-media-v1";

    private ProtocolLabels() {
    }
}
