package dev.no.name.blooddesigner.net.model;

import java.util.List;

/**
 * Групповой чат: id с сервера, участники кроме себя (с проверенными ключами) и владелец ли ты.
 * Название сервер не знает: его рассылает участник сквозным сообщением, мод хранит его у себя
 */
public record LinkGroup(String id, String ownerId, boolean owned, List<LinkFriend> members) {
    public static final int MAX_MEMBERS = 32;

    public LinkGroup {
        members = List.copyOf(members);
    }

    public LinkFriend member(String accountId) {
        for (LinkFriend member : members) {
            if (member.id().equals(accountId)) {
                return member;
            }
        }
        return null;
    }
}
