package dev.no.name.afriends.net.game;

import dev.no.name.afriends.AFriends;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Ключи сети — свои у каждого аккаунта Minecraft: link/accounts/&lt;аккаунт&gt;. Так у каждого ника свои друзья
 * в сети, переписки и группы. Ключи версий без аккаунтов (прямо в link) переезжают к первому аккаунту,
 * под которым зайдут после обновления: игрок не теряет ни друзей, ни сеть
 */
final class AccountFolders {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final String ACCOUNTS = "accounts";
    private static final String IDENTITY = "identity.json";

    private final Path link;

    AccountFolders(Path link) {
        this.link = link;
    }

    // папка аккаунта; в самый первый раз туда переезжают старые ключи.
    // previous — как папка этого аккаунта могла называться раньше (UUID в тестовых сборках): тогда она переезжает
    Path folder(String account, String previous) {
        Path folder = link.resolve(ACCOUNTS).resolve(safe(account));
        try {
            Path old = link.resolve(ACCOUNTS).resolve(safe(previous));
            if (!Files.exists(folder) && !old.equals(folder) && Files.exists(old.resolve(IDENTITY))) {
                Files.move(old, folder);
                LOGGER.info("{}: папка ключей {} теперь называется по нику {}", AFriends.NAME, old.getFileName(), folder.getFileName());
            }
            adoptLegacy(folder);
            Files.createDirectories(folder);
        } catch (IOException e) {
            LOGGER.warn("{}: папка ключей аккаунта {} не создалась", AFriends.NAME, folder, e);
        }
        return folder;
    }

    Path folder(String account) {
        return folder(account, account);
    }

    // папки других аккаунтов этого компьютера, у которых уже есть ключи
    List<Path> others(String account) {
        Path root = link.resolve(ACCOUNTS);
        List<Path> folders = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return folders;
        }
        try (Stream<Path> list = Files.list(root)) {
            list.filter(path -> !path.getFileName().toString().equals(safe(account)))
                    .filter(path -> Files.exists(path.resolve(IDENTITY)))
                    .forEach(folders::add);
        } catch (IOException e) {
            LOGGER.warn("{}: не прочитались папки аккаунтов", AFriends.NAME, e);
        }
        return folders;
    }

    /**
     * Старые ключи лежат прямо в link. Если ни у одного аккаунта ещё нет своей папки — переносим их целиком
     * в папку этого аккаунта. Переносим, а не копируем: иначе одни ключи достались бы двум аккаунтам
     */
    private void adoptLegacy(Path folder) throws IOException {
        Path accounts = link.resolve(ACCOUNTS);
        if (!Files.exists(link.resolve(IDENTITY)) || hasAccounts(accounts)) {
            return;
        }
        Files.createDirectories(folder);
        List<Path> entries;
        try (Stream<Path> list = Files.list(link)) {
            entries = list.filter(path -> !path.getFileName().toString().equals(ACCOUNTS)).toList();
        }
        for (Path entry : entries) {
            Files.move(entry, folder.resolve(entry.getFileName().toString()));
        }
        LOGGER.info("{}: ключи сети перенесены в папку аккаунта {}", AFriends.NAME, folder.getFileName());
    }

    private static boolean hasAccounts(Path accounts) throws IOException {
        if (!Files.isDirectory(accounts)) {
            return false;
        }
        try (Stream<Path> list = Files.list(accounts)) {
            return list.anyMatch(path -> Files.exists(path.resolve(IDENTITY)));
        }
    }

    // имя папки: только буквы, цифры, «-» и «_»
    static String safe(String account) {
        String clean = account.replaceAll("[^A-Za-z0-9_-]", "_");
        return clean.isEmpty() ? "unknown" : clean;
    }
}
