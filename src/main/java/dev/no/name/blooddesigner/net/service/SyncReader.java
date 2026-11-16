package dev.no.name.blooddesigner.net.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.e2e.E2eException;
import dev.no.name.blooddesigner.net.e2e.FriendKeys;
import dev.no.name.blooddesigner.net.model.ClientKind;
import dev.no.name.blooddesigner.net.model.Connection;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkGroup;
import dev.no.name.blooddesigner.net.model.LinkRequest;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import dev.no.name.blooddesigner.net.model.Presence;
import dev.no.name.blooddesigner.net.presence.PresenceBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ответ sync в снимок: разбор полей, проверка ключей друзей и расшифровка их статусов
final class SyncReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
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
        return new LinkSnapshot(Connection.ONLINE, string(me, "name"), me.get("verified").getAsBoolean(),
                List.copyOf(friends), requests(json, "incoming"), requests(json, "outgoing"), now,
                groups(json, string(me, "id"), friends));
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
            LOGGER.warn("{}: ключи друга {} не прошли проверку", BloodDesigner.NAME, name);
            return null;
        }
        LinkFriend old = before.friend(id);
        Presence status = status(json, id);
        if (status == null && old != null) {
            // ключ этой эпохи ещё в пути: пока показываем прежний статус
            status = old.presence();
        }
        return new LinkFriend(id, name, json.get("verified").getAsBoolean(), json.get("accent").getAsInt(),
                ClientKind.fromWire(string(json, "client")), json.get("online").getAsBoolean(),
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
                for (JsonElement item : group.getAsJsonArray("members")) {
                    LinkFriend member = member(item.getAsJsonObject(), friends);
                    if (member != null && !member.id().equals(myId)) {
                        members.add(member);
                    }
                }
                groups.add(new LinkGroup(string(group, "id"), owner, owner.equals(myId), members));
            } catch (RuntimeException e) {
                LOGGER.warn("{}: группа из sync не разобралась", BloodDesigner.NAME, e);
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
            return new LinkFriend(id, name, json.get("verified").getAsBoolean(), 0, ClientKind.BLOODDESIGNER, false, 0, 0, keys, null);
        } catch (E2eException | RuntimeException e) {
            LOGGER.warn("{}: ключи участника группы {} не прошли проверку", BloodDesigner.NAME, name);
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
