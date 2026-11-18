package dev.no.name.afriends.net.rpc;

import dev.no.name.afriends.net.e2e.E2eException;
import dev.no.name.afriends.net.tunnel.TunnelError;

import java.util.concurrent.CompletionException;

// единый короткий код причины для журнала и интерфейса: отказ сервера, сбой шифрования или обрыв
public final class Failures {
    private Failures() {
    }

    public static String codeOf(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof E2eException e2e) {
                return e2e.code();
            }
            if (cause instanceof TunnelError tunnel) {
                return tunnel.code();
            }
            cause = cause.getCause();
        }
        return "offline";
    }

    // проверяемое исключение шифрования внутрь цепочки CompletableFuture
    public static CompletionException wrap(E2eException error) {
        return new CompletionException(error);
    }
}
