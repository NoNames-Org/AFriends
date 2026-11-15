package dev.no.name.blooddesigner.net.e2e;

// сквозное шифрование отказало: ключи не сошлись, сообщение подделано или сессии нет.
// code — короткая причина для журнала и интерфейса
public final class E2eException extends Exception {
    private final String code;

    public E2eException(String code) {
        super(code);
        this.code = code;
    }

    public E2eException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
