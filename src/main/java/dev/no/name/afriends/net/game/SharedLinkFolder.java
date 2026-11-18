package dev.no.name.afriends.net.game;

import dev.no.name.afriends.AFriends;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Ключи сети живут в одной папке на компьютер, а не в папке каждой игры: так все профили лаунчера
 * (основной, отдельные версии, dev-запуск) — один аккаунт сети. Одновременно сетью владеет
 * только одна копия игры: она держит блокировку файла, остальные ждут.
 */
public final class SharedLinkFolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final String APP_FOLDER = AFriends.NAME;
    private static final String LINK_FOLDER = "link";
    private static final String LOCK_FILE = "link.lock";
    private static final String IDENTITY = "identity.json";

    private static volatile boolean busy;

    private SharedLinkFolder() {
    }

    /** Занята ли сеть другой копией игры (для подписи в настройках). */
    public static boolean busy() {
        return busy;
    }

    static void setBusy(boolean value) {
        busy = value;
    }

    /** Общая папка приложения для текущей ОС. */
    public static Path root() {
        return root(System.getProperty("os.name", ""), System.getProperty("user.home", "."),
                System.getenv("APPDATA"), System.getenv("XDG_DATA_HOME"));
    }

    static Path root(String os, String home, String appData, String xdgDataHome) {
        String name = os.toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            Path base = appData != null && !appData.isBlank() ? Path.of(appData) : Path.of(home, "AppData", "Roaming");
            return base.resolve(APP_FOLDER);
        }
        if (name.contains("mac")) {
            return Path.of(home, "Library", "Application Support", APP_FOLDER);
        }
        Path base = xdgDataHome != null && !xdgDataHome.isBlank() ? Path.of(xdgDataHome) : Path.of(home, ".local", "share");
        return base.resolve(APP_FOLDER);
    }

    /** Общая папка BloodDesigner — откуда переезжают ключи при первом запуске AFriends. */
    public static Path legacyRoot(Path root) {
        return root.resolveSibling(AFriends.LEGACY_NAME);
    }

    public static Path linkFolder(Path root) {
        return root.resolve(LINK_FOLDER);
    }

    public static Path lockFile(Path root) {
        return root.resolve(LOCK_FILE);
    }

    /**
     * Один раз переносит ключи из папки игры в общую: только если общей ещё нет, а в папке игры ключи есть.
     * Копируем, а не перемещаем — старая папка остаётся резервной копией.
     */
    static boolean migrate(Path local, Path shared) throws IOException {
        if (Files.exists(shared.resolve(IDENTITY)) || !Files.exists(local.resolve(IDENTITY))) {
            return false;
        }
        try (Stream<Path> files = Files.walk(local)) {
            for (Path source : (Iterable<Path>) files::iterator) {
                Path target = shared.resolve(local.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return true;
    }

    /** Переезд с записью в лог; ошибка не мешает запуску — тогда просто создадутся новые ключи. */
    static void migrateQuietly(Path local, Path shared) {
        try {
            if (migrate(local, shared)) {
                LOGGER.info("{}: ключи сети перенесены в общую папку {}", AFriends.NAME, shared);
            }
        } catch (IOException e) {
            LOGGER.warn("{}: не удалось перенести ключи сети из {} в {}", AFriends.NAME, local, shared, e);
        }
    }

    /**
     * Блокировка сети на всё время работы игры. {@code null}, если её уже держит другая копия игры.
     * Канал не закрываем: блокировка снимается сама, когда процесс завершается.
     */
    static FileLock tryLock(Path lockFile) {
        try {
            Files.createDirectories(lockFile.getParent());
            FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
            }
            return lock;
        } catch (OverlappingFileLockException e) {
            return null;
        } catch (IOException e) {
            LOGGER.warn("{}: не удалось занять {}", AFriends.NAME, lockFile, e);
            return null;
        }
    }
}
