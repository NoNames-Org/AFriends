package dev.no.name.afriends.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadCacheTest {
    private static final UUID BOB = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    @DisplayName("профиль со скином запоминается без учёта регистра и переживает сохранение")
    void rememberAndRoundTrip() {
        HeadCache cache = new HeadCache();
        assertTrue(cache.remember("Bob", BOB, "dGV4dHVyZXM=", "c2ln"));

        assertFalse(cache.remember("Bob", BOB, "dGV4dHVyZXM=", "c2ln"));

        HeadCache loaded = new HeadCache();
        loaded.fromJson(cache.toJson());
        HeadCache.Profile profile = loaded.profile("BOB");
        assertEquals(BOB, profile.id());
        assertEquals("Bob", profile.name());
        assertEquals("dGV4dHVyZXM=", profile.textures());
        assertEquals("c2ln", profile.signature());
    }

    @Test
    @DisplayName("без текстуры не запоминаем, подпись может отсутствовать")
    void skipsEmpty() {
        HeadCache cache = new HeadCache();
        assertFalse(cache.remember("Ann", BOB, null, null));
        assertNull(cache.profile("Ann"));
        assertTrue(cache.remember("Ann", BOB, "dmFsdWU=", null));
        HeadCache loaded = new HeadCache();
        loaded.fromJson(cache.toJson());
        assertNull(loaded.profile("ann").signature());
    }
}
