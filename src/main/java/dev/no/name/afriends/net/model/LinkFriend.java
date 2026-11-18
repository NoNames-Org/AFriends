package dev.no.name.afriends.net.model;

import dev.no.name.afriends.net.e2e.FriendKeys;

// Друг по сети: виден на любом сервере, а не только там, где вы встретились.
// keys уже проверены (id выведен из ключа подписи); presence == null — статус ещё не пришёл или не расшифровался
public record LinkFriend(String id, String name, boolean verified, int accent, boolean online,
                         long lastSeen, long since, FriendKeys keys, Presence presence) {
    public LinkFriend withPresence(Presence value) {
        return new LinkFriend(id, name, verified, accent, online, lastSeen, since, keys, value);
    }
}
