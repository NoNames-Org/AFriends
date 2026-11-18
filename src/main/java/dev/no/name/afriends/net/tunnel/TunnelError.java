package dev.no.name.afriends.net.tunnel;

// отказ сервера или обрыв связи; code — код ответа сервера ("rate_limited", "not_friends") или "offline"
public final class TunnelError extends RuntimeException {
    private final String code;

    public TunnelError(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }

    // код из исключения, которым завершился запрос (в том числе обёрнутого CompletionException)
    public static String codeOf(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof TunnelError tunnel) {
                return tunnel.code();
            }
            cause = cause.getCause();
        }
        return "offline";
    }
}
