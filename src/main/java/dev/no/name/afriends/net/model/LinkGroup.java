package dev.no.name.afriends.net.model;

import dev.no.name.afriends.core.GroupPermissions;

import java.util.List;
import java.util.Map;

/**
 * Групповой чат: id с сервера, участники кроме себя (с проверенными ключами) и владелец ли ты.
 * perms и prefixes — права и префиксы участников по id аккаунта (свои — тоже); у старого сервера их нет,
 * тогда у всех права по умолчанию. Название сервер не знает: его рассылает участник сквозным сообщением
 */
public record LinkGroup(String id, String ownerId, boolean owned, List<LinkFriend> members,
                        Map<String, Integer> perms, Map<String, String> prefixes, int myPerms) {
    public static final int MAX_MEMBERS = 32;

    public LinkGroup {
        members = List.copyOf(members);
        perms = Map.copyOf(perms);
        prefixes = Map.copyOf(prefixes);
    }

    public LinkGroup(String id, String ownerId, boolean owned, List<LinkFriend> members) {
        this(id, ownerId, owned, members, Map.of(), Map.of(), owned ? GroupPermissions.ALL : GroupPermissions.DEFAULT);
    }

    public LinkFriend member(String accountId) {
        for (LinkFriend member : members) {
            if (member.id().equals(accountId)) {
                return member;
            }
        }
        return null;
    }

    public LinkFriend memberNamed(String name) {
        for (LinkFriend member : members) {
            if (member.name().equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    /** Права участника; владелец — все. */
    public int permsOf(String accountId) {
        if (accountId.equals(ownerId)) {
            return GroupPermissions.ALL;
        }
        return perms.getOrDefault(accountId, GroupPermissions.DEFAULT);
    }

    public String prefixOf(String accountId) {
        return prefixes.getOrDefault(accountId, "");
    }
}
