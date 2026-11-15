package dev.no.name.blooddesigner.net.e2e;

import com.google.gson.JsonObject;

// где лежат состояния сессий. Отдельный интерфейс, чтобы ядро шифрования не знало про файлы
public interface SessionStore {
    // null — записи нет
    JsonObject load(String name);

    void save(String name, JsonObject state);

    void delete(String name);
}
