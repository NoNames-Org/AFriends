package dev.no.name.afriends.net.model;

import java.util.ArrayList;
import java.util.List;

// всё, что мод знает о сети на момент последней синхронизации; неизменяемый, его читает интерфейс
public record LinkSnapshot(Connection connection, String myName, boolean verified, List<LinkFriend> friends,
                           List<LinkRequest> incoming, List<LinkRequest> outgoing, long syncedAt, List<LinkGroup> groups,
                           LinkProfile profile) {

    public LinkSnapshot(Connection connection, String myName, boolean verified, List<LinkFriend> friends,
                        List<LinkRequest> incoming, List<LinkRequest> outgoing, long syncedAt, List<LinkGroup> groups) {
        this(connection, myName, verified, friends, incoming, outgoing, syncedAt, groups, LinkProfile.EMPTY);
    }

    // без групп: старый сервер их не присылает
    public LinkSnapshot(Connection connection, String myName, boolean verified, List<LinkFriend> friends,
                        List<LinkRequest> incoming, List<LinkRequest> outgoing, long syncedAt) {
        this(connection, myName, verified, friends, incoming, outgoing, syncedAt, List.of());
    }

    public static LinkSnapshot initial() {
        return new LinkSnapshot(Connection.DISABLED, "", false, List.of(), List.of(), List.of(), 0, List.of(), LinkProfile.EMPTY);
    }

    public LinkSnapshot withConnection(Connection value) {
        return new LinkSnapshot(value, myName, verified, friends, incoming, outgoing, syncedAt, groups, profile);
    }

    // статус одного друга пришёл событием, без полной синхронизации
    public LinkSnapshot withFriend(LinkFriend updated) {
        List<LinkFriend> list = new ArrayList<>(friends.size());
        for (LinkFriend friend : friends) {
            list.add(friend.id().equals(updated.id()) ? updated : friend);
        }
        return new LinkSnapshot(connection, myName, verified, List.copyOf(list), incoming, outgoing, syncedAt, groups, profile);
    }

    public boolean synced() {
        return syncedAt > 0;
    }

    public LinkFriend friend(String id) {
        for (LinkFriend friend : friends) {
            if (friend.id().equals(id)) {
                return friend;
            }
        }
        return null;
    }

    // друг, участник общей группы или собеседник личного чата: от него можно принять и ему можно отправить конверт
    public LinkFriend peer(String id) {
        LinkFriend friend = friend(id);
        if (friend != null) {
            return friend;
        }
        LinkFriend chat = profile.chat(id);
        if (chat != null) {
            return chat;
        }
        for (LinkGroup group : groups) {
            LinkFriend member = group.member(id);
            if (member != null) {
                return member;
            }
        }
        return null;
    }

    public LinkGroup group(String id) {
        for (LinkGroup group : groups) {
            if (group.id().equals(id)) {
                return group;
            }
        }
        return null;
    }

    // с кем переписка идёт по сети напрямую: друг или собеседник личного чата
    public LinkFriend directNamed(String name) {
        LinkFriend friend = friendNamed(name);
        return friend != null ? friend : profile.chatNamed(name);
    }

    public LinkFriend friendNamed(String name) {
        for (LinkFriend friend : friends) {
            if (friend.name().equalsIgnoreCase(name)) {
                return friend;
            }
        }
        return null;
    }

    public long onlineCount() {
        return friends.stream().filter(LinkFriend::online).count();
    }
}
