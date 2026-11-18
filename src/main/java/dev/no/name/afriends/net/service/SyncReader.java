package dev.no.name.afriends.net.service;

import java.util.Map;
import java.util.HashMap;
import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.net.model.LinkProfile;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.net.crypto.Bytes;
import dev.no.name.afriends.net.e2e.E2eException;
import dev.no.name.afriends.net.e2e.FriendKeys;
import dev.no.name.afriends.net.model.Connection;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.model.LinkGroup;
import dev.no.name.afriends.net.model.LinkRequest;
import dev.no.name.afriends.net.model.LinkSnapshot;
import dev.no.name.afriends.net.model.Presence;
import dev.no.name.afriends.net.presence.PresenceBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ответ sync в снимок: разбор полей, проверка ключей друзей и расшифровка их статусов
final class SyncReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    // в сети сверху, внутри — по нику
    private static final Comparator<LinkFriend> ORDER = Comparator.comparing((LinkFriend f) -> !f.online())
            .thenComparing(f -> f.name().toLowerCase());

    private final PresenceBook presence;

    SyncReader(PresenceBook presence) {
        this.presence = presence;
    }

    LinkSnapshot read(JsonObject json, LinkSnapshot before, long now) {
        JsonObject me = json.getAsJsonObject("me");
        List<LinkFriend> friends = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("friends")) {
            LinkFriend friend = friend(element.getAsJsonObject(), before);
            if (friend != null) {
                friends.add(friend);
            }
        }
        friends.sort(ORDER);
        List<LinkGroup> groups = groups(json, string(me, "id"), friends);
        return new LinkSnapshot(Connection.ONLINE, string(me, "name"), me.get("verified").getAsBoolean(),
                List.copyOf(friends), requests(json, "incoming"), requests(json, "outgoing"), now,
                groups, profile(json, me, friends));
    }

    // описание, кто может писать, личные чаты и блокировки; старый сервер полей не пришлёт
    private static LinkProfile profile(JsonObject json, JsonObject me, List<LinkFriend> friends) {
        Map<String, String> abouts = new HashMap<>();
        collectAbouts(json.get("friends"), abouts);
        if (json.has("groups") && json.get("groups").isJsonArray()) {
            for (JsonElement group : json.getAsJsonArray("groups")) {
                if (group.isJsonObject()) {
                    collectAbouts(group.getAsJsonObject().get("members"), abouts);
                }
            }
        }
        collectAbouts(json.get("chats"), abouts);
        List<LinkFriend> chats = new ArrayList<>();
        if (json.has("chats") && json.get("chats").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("chats")) {
                try {
                    LinkFriend peer = member(element.getAsJsonObject(), friends);
                    if (peer != null) {
                        chats.add(peer);
                    }
                } catch (RuntimeException e) {
                    LOGGER.warn("{}: личный чат из sync не разобрался", AFriends.NAME, e);
                }
            }
        }
        List<String> blocked = new ArrayList<>();
        if (json.has("blocked") && json.get("blocked").isJsonArray()) {
            json.getAsJsonArray("blocked").forEach(id -> blocked.add(id.getAsString()));
        }
        boolean everyone = !json.has("dm") || !"friends".equals(json.get("dm").getAsString());
        return new LinkProfile(string(me, "about"), everyone, chats, blocked, abouts);
    }

    private static void collectAbouts(JsonElement list, Map<String, String> abouts) {
        if (list == null || !list.isJsonArray()) {
            return;
        }
        for (JsonElement element : list.getAsJsonArray()) {
            if (element.isJsonObject()) {
                JsonObject card = element.getAsJsonObject();
                String about = string(card, "about");
                if (!about.isEmpty() && card.has("id")) {
                    abouts.put(card.get("id").getAsString(), about);
                }
            }
        }
    }

    private LinkFriend friend(JsonObject json, LinkSnapshot before) {
        String id = json.get("id").getAsString();
        String name = string(json, "name");
        if (name.isEmpty()) {
            return null;
        }
        FriendKeys keys;
        try {
            // сервер не может подсунуть чужие ключи: id выведен из ключа подписи, ключ обмена подписан им
            keys = FriendKeys.verified(id, bytes(json, "signKey"), bytes(json, "boxKey"), bytes(json, "keySig"));
        } catch (E2eException | RuntimeException e) {
            LOGGER.warn("{}: ключи друга {} не прошли проверку", AFriends.NAME, name);
            return null;
        }
        LinkFriend old = before.friend(id);
        Presence status = status(json, id);
        if (status == null && old != null) {
            // ключ этой эпохи ещё в пути: пока показываем прежний статус
            status = old.presence();
        }
        return new LinkFriend(id, name, json.get("verified").getAsBoolean(), json.get("accent").getAsInt(),
                json.get("online").getAsBoolean(),
                json.get("lastSeen").getAsLong(), json.get("since").getAsLong(), keys, status);
    }

    // группы из sync; старый сервер поля не пришлёт. Участник-друг берётся из списка друзей (со статусом),
    // остальные — по карточке ключей, которую мод проверяет так же, как у друзей
    private static List<LinkGroup> groups(JsonObject json, String myId, List<LinkFriend> friends) {
        if (!json.has("groups") || !json.get("groups").isJsonArray()) {
            return List.of();
        }
        List<LinkGroup> groups = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("groups")) {
            try {
                JsonObject group = element.getAsJsonObject();
                String owner = string(group, "owner");
                List<LinkFriend> members = new ArrayList<>();
                Map<String, Integer> perms = new HashMap<>();
                Map<String, String> prefixes = new HashMap<>();
                for (JsonElement item : group.getAsJsonArray("members")) {
                    JsonObject card = item.getAsJsonObject();
                    String memberId = card.get("id").getAsString();
                    if (card.has("perms") && card.get("perms").isJsonPrimitive()) {
                        perms.put(memberId, card.get("perms").getAsInt() & GroupPermissions.ALL);
                    }
                    String prefix = string(card, "prefix");
                    if (!prefix.isEmpty()) {
                        prefixes.put(memberId, prefix);
                    }
                    LinkFriend member = member(card, friends);
                    if (member != null && !member.id().equals(myId)) {
                        members.add(member);
                    }
                }
                int myPerms = owner.equals(myId) ? GroupPermissions.ALL : perms.getOrDefault(myId, GroupPermissions.DEFAULT);
                groups.add(new LinkGroup(string(group, "id"), owner, owner.equals(myId), members, perms, prefixes, myPerms));
            } catch (RuntimeException e) {
                LOGGER.warn("{}: группа из sync не разобралась", AFriends.NAME, e);
            }
        }
        return List.copyOf(groups);
    }

    private static LinkFriend member(JsonObject json, List<LinkFriend> friends) {
        String id = json.get("id").getAsString();
        for (LinkFriend friend : friends) {
            if (friend.id().equals(id)) {
                return friend;
            }
        }
        String name = string(json, "name");
        if (name.isEmpty()) {
            return null;
        }
        try {
            FriendKeys keys = FriendKeys.verified(id, bytes(json, "signKey"), bytes(json, "boxKey"), bytes(json, "keySig"));
            return new LinkFriend(id, name, json.get("verified").getAsBoolean(), 0, false, 0, 0, keys, null);
        } catch (E2eException | RuntimeException e) {
            LOGGER.warn("{}: ключи участника группы {} не прошли проверку", AFriends.NAME, name);
            return null;
        }
    }

    private Presence status(JsonObject json, String friendId) {
        JsonElement blob = json.get("presence");
        return blob == null || blob.isJsonNull() ? null : presence.open(friendId, blob.getAsString());
    }

    private static List<LinkRequest> requests(JsonObject json, String field) {
        List<LinkRequest> list = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(field)) {
            JsonObject row = element.getAsJsonObject();
            String name = string(row, "name");
            if (!name.isEmpty()) {
                list.add(new LinkRequest(row.get("id").getAsString(), name,
                        row.has("verified") && row.get("verified").getAsBoolean(), row.get("created").getAsLong()));
            }
        }
        return List.copyOf(list);
    }

    private static byte[] bytes(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? null : Bytes.fromBase64(value.getAsString());
    }

    private static String string(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
