package dev.no.name.afriends.addon;

import dev.no.name.afriends.api.FriendStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonRegistryTest {
    @Test
    @DisplayName("упавший аддон не роняет вызов: возвращается значение по умолчанию")
    void guardReturnsFallback() {
        AddonRegistry registry = new AddonRegistry();

        String value = registry.guard("test", () -> {
            throw new IllegalStateException("boom");
        }, "fallback");

        assertEquals("fallback", value);
    }

    @Test
    @DisplayName("первая проверка отправки с отказом решает; упавшая проверка пропускается")
    void sendProblemTakesFirstRefusal() {
        AddonRegistry registry = new AddonRegistry();
        registry.addSendCheck((partner, command) -> {
            throw new IllegalStateException("broken addon");
        });
        registry.addSendCheck((partner, command) -> Optional.of("нельзя " + partner));
        registry.addSendCheck((partner, command) -> Optional.of("не дойдёт"));

        assertEquals(Optional.of("нельзя Bob"), registry.sendProblem("Bob", "msg Bob hi"));
    }

    @Test
    @DisplayName("без аддонов отправка разрешена и игрок считается достижимым")
    void defaultsWithoutAddons() {
        AddonRegistry registry = new AddonRegistry();

        assertTrue(registry.sendProblem("Bob", "msg Bob hi").isEmpty());
        assertNull(registry.status("Bob"));
        assertTrue(registry.reachable("Bob"));
    }

    @Test
    @DisplayName("подписчики узнают о добавлении и удалении друга")
    void friendListenersNotified() {
        AddonRegistry registry = new AddonRegistry();
        List<String> events = new ArrayList<>();
        registry.addFriendListener(new dev.no.name.afriends.api.FriendListener() {
            @Override
            public void added(String name, java.util.function.Consumer<String> notice) {
                events.add("+" + name);
            }

            @Override
            public void removed(String name) {
                events.add("-" + name);
            }
        });

        registry.added("Bob", notice -> { });
        registry.removed("Bob");

        assertEquals(List.of("+Bob", "-Bob"), events);
    }

    @Test
    @DisplayName("статус друга берётся у первого аддона, который его знает")
    void statusFromFirstKnowingAddon() {
        AddonRegistry registry = new AddonRegistry();
        registry.addFriendStatus(name -> null);
        registry.addFriendStatus(name -> new FriendStatus.Status(true, "в игре", null, false));

        FriendStatus.Status status = registry.status("Bob");

        assertTrue(status.online());
        assertTrue(!registry.reachable("Bob"));
    }
}
