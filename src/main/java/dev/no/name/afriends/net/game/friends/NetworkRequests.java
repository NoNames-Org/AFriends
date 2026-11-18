package dev.no.name.afriends.net.game.friends;

import dev.no.name.afriends.api.FriendRequests;

import java.util.List;

// заявки в друзья по сети — сверху списка друзей
final class NetworkRequests implements FriendRequests {
    private final FriendSync friends;

    NetworkRequests(FriendSync friends) {
        this.friends = friends;
    }

    @Override
    public List<String> names() {
        return friends.active() ? friends.requests() : List.of();
    }

    @Override
    public String hint() {
        return "Заявка из сети AFriends";
    }

    @Override
    public void respond(String name, boolean accept) {
        friends.respond(name, accept);
    }
}
