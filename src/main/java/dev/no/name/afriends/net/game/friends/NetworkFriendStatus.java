package dev.no.name.afriends.net.game.friends;

import dev.no.name.afriends.api.FriendStatus;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.net.game.LinkText;
import dev.no.name.afriends.net.model.LinkFriend;

// Строка под ником друга по сети: где он сейчас или когда был. Позвать в пати можно, только если он здесь;
// написать — всегда, это решает мессенджер. Для остальных друзей молчим: пусть скажут другие дополнения
final class NetworkFriendStatus implements FriendStatus {
    private final FriendSync friends;

    NetworkFriendStatus(FriendSync friends) {
        this.friends = friends;
    }

    @Override
    public Status status(String name) {
        LinkFriend friend = friends.active() ? friends.friend(name) : null;
        if (friend == null) {
            return null;
        }
        boolean here = FriendManager.onlinePlayerNames().stream().anyMatch(name::equalsIgnoreCase);
        boolean online = LinkText.online(friend, here);
        String status = friend.online() && friend.presence() != null ? friend.presence().status() : "";
        return new Status(online, LinkText.line(friend, System.currentTimeMillis(), here), status.isEmpty() ? null : status, here);
    }
}
