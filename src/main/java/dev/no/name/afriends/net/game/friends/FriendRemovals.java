package dev.no.name.afriends.net.game.friends;

import dev.no.name.afriends.net.model.Connection;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.model.LinkSnapshot;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

// Друг удалил нас в сети — убираем его и из своего списка друзей, на всех серверах.
// Сравниваются только две синхронизации подряд при живой связи: обрыв, выключение сети
// и первый вход не выглядят как удаление. Поток игры
public final class FriendRemovals {
    private final Supplier<LinkSnapshot> snapshot;
    private final Predicate<String> removedByMe;
    private final Consumer<String> remove;
    private LinkSnapshot seen;

    // removedByMe — этого друга мы удалили сами, и список уже без него; remove — убрать по нику отовсюду
    public FriendRemovals(Supplier<LinkSnapshot> snapshot, Predicate<String> removedByMe, Consumer<String> remove) {
        this.snapshot = snapshot;
        this.removedByMe = removedByMe;
        this.remove = remove;
    }

    public void tick() {
        LinkSnapshot state = snapshot.get();
        if (state == seen) {
            return;
        }
        LinkSnapshot before = seen;
        seen = live(state) ? state : null;
        if (before == null || seen == null) {
            return;
        }
        for (LinkFriend friend : before.friends()) {
            if (state.friend(friend.id()) == null && !removedByMe.test(friend.name())) {
                remove.accept(friend.name());
            }
        }
    }

    // сменился аккаунт: друзья прежнего не сравниваются с друзьями нового
    public void reset() {
        seen = null;
    }

    private static boolean live(LinkSnapshot state) {
        return state.connection() == Connection.ONLINE && state.synced();
    }
}
