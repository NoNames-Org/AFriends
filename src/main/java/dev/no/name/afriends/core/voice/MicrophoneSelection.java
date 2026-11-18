package dev.no.name.afriends.core.voice;

/**
 * Выбранный микрофон для записи голосовых. Одно общее место: настройки звука его задают (и берут из config.json),
 * а запись ({@code VoiceRecorder}) читает отсюда, не зная про настройки. Пусто — устройство по умолчанию
 */
public final class MicrophoneSelection {
    private static volatile String device = "";

    private MicrophoneSelection() {
    }

    /** Имя устройства OpenAL или null — писать с микрофона по умолчанию. */
    public static String name() {
        String current = device;
        return current == null || current.isBlank() ? null : current;
    }

    public static void select(String value) {
        device = value == null ? "" : value;
    }
}
