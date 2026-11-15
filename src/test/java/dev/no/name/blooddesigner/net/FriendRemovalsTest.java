package dev.no.name.blooddesigner.net;

import dev.no.name.blooddesigner.net.e2e.FriendKeys;
import dev.no.name.blooddesigner.net.game.friends.FriendRemovals;
import dev.no.name.blooddesigner.net.model.ClientKind;
import dev.no.name.blooddesigner.net.model.Connection;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

// друг удалил нас в сети — он пропадает и из нашего списка, но обрывы связи за удаление не считаются
class FriendRemovalsTest {
    private final List<String> removed = new ArrayList<>();
    private LinkSnapshot current = LinkSnapshot.initial();
    private Set<String> mine = Set.of();
    private final FriendRemovals removals = new FriendRemovals(() -> current, mine::contains, removed::add);

    private static LinkFriend friend(String name) {
        return new LinkFriend(name.toLowerCase() + "0".repeat(32 - name.length()), name, false, 0, ClientKind.BLOODDESIGNER,
                true, 0, 0, new FriendKeys("x", new byte[32], new byte[32]), null);
    }

    private void show(Connection connection, long syncedAt, String... names) {
        current = new LinkSnapshot(connection, "Me", false, List.of(names).stream().map(FriendRemovalsTest::friend).toList(),
                List.of(), List.of(), syncedAt);
        removals.tick();
    }

    @Test
    void friendWhoRemovedUsIsRemovedLocally() {
        show(Connection.ONLINE, 1, "Kent", "Bob");
        show(Connection.ONLINE, 2, "Bob");
        assertEquals(List.of("Kent"), removed);
    }

    @Test
    void disconnectAndReconnectRemoveNobody() {
        show(Connection.ONLINE, 1, "Kent");
        show(Connection.OFFLINE, 1, "Kent");
        current = LinkSnapshot.initial();
        removals.tick();
        show(Connection.ONLINE, 5, "Kent");
        assertEquals(List.of(), removed);
    }

    @Test
    void firstSyncAfterStartIsNotARemoval() {
        show(Connection.CONNECTING, 0);
        show(Connection.ONLINE, 1, "Kent");
        assertEquals(List.of(), removed);
    }
}
