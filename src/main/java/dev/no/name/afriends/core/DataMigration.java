package dev.no.name.afriends.core;

import dev.no.name.afriends.AFriends;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Переезд данных BloodDesigner → AFriends: настройки и переписки из config/blooddesigner, ключи сети из общей папки
 * BloodDesigner. Копируем, а не перемещаем — старые папки остаются резервной копией.
 * <p>
 * Копируются только файлы, которых в новой папке ещё нет: уже существующее никогда не перезаписывается.
 * Когда всё скопировано, в новой папке появляется метка, и больше переезд не запускается. Если копирование
 * сорвалось (игру закрыли, файл занят), метки нет — в следующий запуск переезд продолжится с того же места.
 */
public final class DataMigration {
    static final String MARKER = ".migrated-from-blooddesigner";
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    // файл блокировки принадлежит работающей копии игры, переносить его нельзя
    private static final Set<String> SKIP = Set.of("link.lock");

    private DataMigration() {
    }

    /** Переносит {@code legacy} в {@code current}; ошибка пишется в лог и не мешает запуску. */
    public static void migrateQuietly(Path legacy, Path current) {
        try {
            int copied = copyMissing(legacy, current);
            if (copied > 0) {
                LOGGER.info("{}: перенесено файлов из {} в {}: {}", AFriends.NAME, legacy, current, copied);
            }
        } catch (IOException e) {
            LOGGER.warn("{}: не удалось перенести данные из {} в {}, попробую при следующем запуске",
                    AFriends.NAME, legacy, current, e);
        }
    }

    /** Сколько файлов скопировано; 0 — переносить нечего или переезд уже был. */
    static int copyMissing(Path legacy, Path current) throws IOException {
        if (!Files.isDirectory(legacy) || Files.exists(current.resolve(MARKER))) {
            return 0;
        }
        int copied = 0;
        try (Stream<Path> files = Files.walk(legacy)) {
            for (Path source : (Iterable<Path>) files::iterator) {
                if (SKIP.contains(source.getFileName().toString())) {
                    continue;
                }
                Path target = current.resolve(legacy.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                    copied++;
                }
            }
        }
        Files.createDirectories(current);
        Files.writeString(current.resolve(MARKER), legacy.toString());
        return copied;
    }
}
