package dev.no.name.afriends.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataMigrationTest {
    @TempDir
    Path temp;

    private Path legacy() throws IOException {
        Path legacy = temp.resolve("blooddesigner");
        Files.createDirectories(legacy.resolve("link/acc"));
        Files.writeString(legacy.resolve("config.json"), "old");
        Files.writeString(legacy.resolve("link/acc/identity.json"), "id");
        Files.writeString(legacy.resolve("link.lock"), "");
        return legacy;
    }

    @Test
    void copiesLegacyFolderAndLeavesItAsBackup() throws IOException {
        Path legacy = legacy();
        Path current = temp.resolve("afriends");

        assertEquals(2, DataMigration.copyMissing(legacy, current));

        assertEquals("old", Files.readString(current.resolve("config.json")));
        assertEquals("id", Files.readString(current.resolve("link/acc/identity.json")));
        assertFalse(Files.exists(current.resolve("link.lock")));
        assertTrue(Files.exists(current.resolve(DataMigration.MARKER)));
        assertTrue(Files.exists(legacy.resolve("config.json")), "старая папка остаётся резервной копией");
    }

    @Test
    void neverOverwritesFilesThatAlreadyExist() throws IOException {
        Path legacy = legacy();
        Path current = temp.resolve("afriends");
        Files.createDirectories(current);
        Files.writeString(current.resolve("config.json"), "new");

        assertEquals(1, DataMigration.copyMissing(legacy, current));

        assertEquals("new", Files.readString(current.resolve("config.json")));
        assertEquals("id", Files.readString(current.resolve("link/acc/identity.json")),
                "папка уже есть (прошлый переезд сорвался) — недостающее всё равно докопируется");
    }

    @Test
    void runsOnlyOnce() throws IOException {
        Path legacy = legacy();
        Path current = temp.resolve("afriends");
        DataMigration.copyMissing(legacy, current);
        Files.delete(current.resolve("config.json"));

        assertEquals(0, DataMigration.copyMissing(legacy, current));
        assertFalse(Files.exists(current.resolve("config.json")), "после метки удалённое игроком не возвращается");
    }

    @Test
    void doesNothingWithoutLegacyFolder() throws IOException {
        assertEquals(0, DataMigration.copyMissing(temp.resolve("none"), temp.resolve("afriends")));
        assertFalse(Files.exists(temp.resolve("afriends")));
    }
}
