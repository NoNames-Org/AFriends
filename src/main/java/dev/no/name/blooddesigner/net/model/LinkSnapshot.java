package dev.no.name.blooddesigner.net.model;

import java.util.ArrayList;
import java.util.List;

// всё, что мод знает о сети на момент последней синхронизации; неизменяемый, его читает интерфейс
public record LinkSnapshot(Connection connection, String myName, boolean verified, List<LinkFriend> friends,
                           List<LinkRequest> incoming, List<LinkRequest> outgoing, long syncedAt, List<LinkGroup> groups) {

    // без групп: старый сервер их не присылает
    public LinkSnapshot(Connection connection, String myName, boolean verified, List<LinkFriend> friends,
                        List<LinkRequest> incoming, List<LinkRequest> outgoing, long syncedAt) {
        this(connection, myName, verified, friends, incoming, outgoing, syncedAt, List.of());
    }

    public static LinkSnapshot initial() {
        return new LinkSnapshot(Connection.DISABLED, "", false, List.of(), List.of(), List.of(), 0, List.of());
    }

    public LinkSnapshot withConnection(Connection value) {
        return new LinkSnapshot(value, myName, verified, friends, incoming, outgoing, syncedAt, groups);
    }

    // статус одного друга пришёл событием, без полной синхронизации
    public LinkSnapshot withFriend(LinkFriend updated) {
        List<LinkFriend> list = new ArrayList<>(friends.size());
        for (LinkFriend friend : friends) {
            list.add(friend.id().equals(updated.id()) ? updated : friend);
        }
        return new LinkSnapshot(connection, myName, verified, List.copyOf(list), incoming, outgoing, syncedAt, groups);
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

    // друг или участник общей группы: от него можно принять и ему можно отправить конверт
    public LinkFriend peer(String id) {
        LinkFriend friend = friend(id);
        if (friend != null) {
            return friend;
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
