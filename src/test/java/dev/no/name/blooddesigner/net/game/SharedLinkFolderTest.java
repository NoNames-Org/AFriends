package dev.no.name.blooddesigner.net.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedLinkFolderTest {
    @Test
    @DisplayName("общая папка: Windows — APPDATA, macOS — Application Support, Linux — XDG или ~/.local/share")
    void rootPerOs() {
        assertEquals(Path.of("C:/Users/u/AppData/Roaming", "BloodDesigner"),
                SharedLinkFolder.root("Windows 11", "C:/Users/u", "C:/Users/u/AppData/Roaming", null));
        assertEquals(Path.of("C:/Users/u", "AppData", "Roaming", "BloodDesigner"),
                SharedLinkFolder.root("Windows 11", "C:/Users/u", "", null));
        assertEquals(Path.of("/Users/u", "Library", "Application Support", "BloodDesigner"),
                SharedLinkFolder.root("Mac OS X", "/Users/u", null, null));
        assertEquals(Path.of("/data", "BloodDesigner"), SharedLinkFolder.root("Linux", "/home/u", null, "/data"));
        assertEquals(Path.of("/home/u", ".local", "share", "BloodDesigner"),
                SharedLinkFolder.root("Linux", "/home/u", null, null));
    }

    @Test
    @DisplayName("переезд копирует ключи со вложенными папками и оставляет старые на месте")
    void migrateCopiesOnce(@TempDir Path temp) throws Exception {
        Path local = temp.resolve("game/config/blooddesigner/link");
        Path shared = temp.resolve("shared/link");
        Files.createDirectories(local.resolve("sessions"));
        Files.writeString(local.resolve("identity.json"), "{\"id\":1}");
        Files.writeString(local.resolve("sessions/prekeys.json"), "[]");

        assertTrue(SharedLinkFolder.migrate(local, shared));
        assertEquals("{\"id\":1}", Files.readString(shared.resolve("identity.json")));
        assertEquals("[]", Files.readString(shared.resolve("sessions/prekeys.json")));
        assertTrue(Files.exists(local.resolve("identity.json")));

        Files.writeString(local.resolve("identity.json"), "{\"id\":2}");
        assertFalse(SharedLinkFolder.migrate(local, shared));
        assertEquals("{\"id\":1}", Files.readString(shared.resolve("identity.json")));
    }

    @Test
    @DisplayName("нечего переносить — общая папка не создаётся")
    void migrateWithoutLocalKeys(@TempDir Path temp) throws Exception {
        Path shared = temp.resolve("shared/link");
        assertFalse(SharedLinkFolder.migrate(temp.resolve("empty"), shared));
        assertFalse(Files.exists(shared));
    }

    @Test
    @DisplayName("вторая попытка занять сеть, пока первая держит блокировку, не проходит")
    void lockIsExclusive(@TempDir Path temp) throws Exception {
        Path lockFile = temp.resolve("BloodDesigner/link.lock");
        FileLock first = SharedLinkFolder.tryLock(lockFile);
        assertNotNull(first);
        assertNull(SharedLinkFolder.tryLock(lockFile));
        first.release();
        first.channel().close();
        FileLock again = SharedLinkFolder.tryLock(lockFile);
        assertNotNull(again);
        again.release();
        again.channel().close();
    }
}
