package dev.no.name.blooddesigner.net.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountFoldersTest {
    @Test
    @DisplayName("старые ключи переезжают к первому аккаунту целиком, второму достаётся пустая папка")
    void legacyKeysGoToFirstAccount(@TempDir Path link) throws Exception {
        Files.writeString(link.resolve("identity.json"), "old");
        Files.createDirectories(link.resolve("sessions"));
        Files.writeString(link.resolve("sessions").resolve("peer.json"), "s");
        Files.writeString(link.resolve("parked.json"), "[]");
        AccountFolders folders = new AccountFolders(link);

        Path main = folders.folder("main");
        assertEquals("old", Files.readString(main.resolve("identity.json")));
        assertTrue(Files.exists(main.resolve("sessions").resolve("peer.json")));
        assertTrue(Files.exists(main.resolve("parked.json")));
        assertFalse(Files.exists(link.resolve("identity.json")));

        Path twink = folders.folder("twink");
        assertFalse(Files.exists(twink.resolve("identity.json")));
        assertEquals("old", Files.readString(folders.folder("main").resolve("identity.json")));
    }

    @Test
    @DisplayName("другие аккаунты компьютера — только те, у кого уже есть ключи")
    void othersWithKeys(@TempDir Path link) throws Exception {
        AccountFolders folders = new AccountFolders(link);
        Files.writeString(folders.folder("main").resolve("identity.json"), "a");
        Files.writeString(folders.folder("twink").resolve("identity.json"), "b");
        folders.folder("fresh");
        assertEquals(List.of("twink"), folders.others("main").stream().map(path -> path.getFileName().toString()).toList());
    }

    @Test
    @DisplayName("папка, названная по UUID в тестовой сборке, переезжает к нику со всеми ключами")
    void uuidFolderMovesToName(@TempDir Path link) throws Exception {
        AccountFolders folders = new AccountFolders(link);
        Files.writeString(folders.folder("0123abcd").resolve("identity.json"), "keys");
        Path byName = folders.folder("fell__o_o", "0123abcd");
        assertEquals("keys", Files.readString(byName.resolve("identity.json")));
        assertFalse(Files.exists(link.resolve("accounts").resolve("0123abcd")));
        // уже есть папка по нику — ничего не трогаем
        Files.writeString(folders.folder("other").resolve("identity.json"), "x");
        assertEquals("keys", Files.readString(folders.folder("fell__o_o", "other").resolve("identity.json")));
        assertTrue(Files.exists(link.resolve("accounts").resolve("other").resolve("identity.json")));
    }

    @Test
    @DisplayName("имя папки аккаунта без опасных символов")
    void safeNames() {
        assertEquals("0123abcd", AccountFolders.safe("0123abcd"));
        assertEquals("name-steve", AccountFolders.safe("name-steve"));
        assertEquals("______etc", AccountFolders.safe("../../etc"));
    }
}
