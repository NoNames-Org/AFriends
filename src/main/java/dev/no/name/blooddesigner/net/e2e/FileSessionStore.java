package dev.no.name.blooddesigner.net.e2e;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.blooddesigner.net.identity.SecretFiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

// состояния в папке мода, по файлу на запись; права только для владельца
public final class FileSessionStore implements SessionStore {
    private final Path directory;

    public FileSessionStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public JsonObject load(String name) {
        Path file = file(name);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            // битый файл — как будто записи нет: сессия пересоздастся
            return null;
        }
    }

    @Override
    public void save(String name, JsonObject state) {
        try {
            SecretFiles.write(file(name), state.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String name) {
        try {
            Files.deleteIfExists(file(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // имена — id аккаунтов (hex) и служебные слова; всё остальное в путь не пускаем
    private Path file(String name) {
        if (!name.matches("[a-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("недопустимое имя записи");
        }
        return directory.resolve(name + ".json");
    }
}
