package dev.no.name.afriends.net.model;

import java.util.List;
import java.util.Map;

/**
 * Профиль и личные чаты из sync: своё описание, кто может писать (все или только друзья), собеседники личных
 * чатов без дружбы (с проверенными ключами), заблокированные и описания профилей по id аккаунта.
 * Старый сервер этих полей не присылает — тогда {@link #EMPTY}.
 */
public record LinkProfile(String about, boolean dmEveryone, List<LinkFriend> chats, List<String> blocked,
                          Map<String, String> abouts) {
    public static final LinkProfile EMPTY = new LinkProfile("", true, List.of(), List.of(), Map.of());

    public LinkProfile {
        about = about == null ? "" : about;
        chats = List.copyOf(chats);
        blocked = List.copyOf(blocked);
        abouts = Map.copyOf(abouts);
    }

    public LinkFriend chat(String id) {
        for (LinkFriend peer : chats) {
            if (peer.id().equals(id)) {
                return peer;
            }
        }
        return null;
    }

    public LinkFriend chatNamed(String name) {
        for (LinkFriend peer : chats) {
            if (peer.name().equalsIgnoreCase(name)) {
                return peer;
            }
        }
        return null;
    }

    /** Описание профиля аккаунта; пусто — нет или не видно. */
    public String aboutOf(String accountId) {
        return abouts.getOrDefault(accountId, "");
    }

    public boolean isBlocked(String accountId) {
        return blocked.contains(accountId);
    }
}
