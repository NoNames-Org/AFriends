package dev.no.name.blooddesigner.net.game.friends;

import dev.no.name.blooddesigner.api.FriendListener;

import java.util.function.Consumer;

// друга добавили по нику — если у него есть мод, зовём и в сеть; удалили — убираем и из сети
final class NetworkFriendEvents implements FriendListener {
    private final FriendSync friends;

    NetworkFriendEvents(FriendSync friends) {
        this.friends = friends;
    }

    @Override
    public void added(String name, Consumer<String> notice) {
        friends.added(name, notice);
    }

    @Override
    public void removed(String name) {
        friends.removed(name);
    }
}
