package dev.no.name.blooddesigner.net.badges;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.net.model.ClientKind;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.rpc.LinkCalls;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Кто из игроков рядом тоже в сети. Спрашиваем сервер только о новых никах и помним ответ 10 минут,
// поэтому на сервере со 100 игроками это один запрос при заходе и редкие догрузки.
// badge() читается с потока игры, check() зовётся с потока сети
public final class BadgeDirectory {
    private static final long ANSWER_TTL_MS = 600_000;
    private static final int BATCH = 100;

    private record Entry(Badge badge, long checkedAt) {
    }

    private final Map<String, Entry> known = new ConcurrentHashMap<>();
    private boolean inFlight;

    // null — игрок не в сети мода (или ещё не знаем)
    public Badge badge(String name) {
        Entry entry = known.get(key(name));
        return entry == null ? null : entry.badge();
    }

    // свои друзья по сети всегда со значком, без запросов
    public void rememberFriends(List<LinkFriend> friends, long now) {
        for (LinkFriend friend : friends) {
            known.put(key(friend.name()), new Entry(new Badge(friend.client(), friend.verified()), now));
        }
    }

    public void rememberSelf(String name, ClientKind client, boolean verified, long now) {
        if (!name.isEmpty()) {
            known.put(key(name), new Entry(new Badge(client, verified), now));
        }
    }

    public void check(LinkCalls calls, List<String> names, long now) {
        if (inFlight) {
            return;
        }
        List<String> unknown = new ArrayList<>();
        for (String name : names) {
            Entry entry = known.get(key(name));
            if (entry == null || now - entry.checkedAt() > ANSWER_TTL_MS) {
                unknown.add(name);
            }
            if (unknown.size() == BATCH) {
                break;
            }
        }
        if (unknown.isEmpty()) {
            return;
        }
        inFlight = true;
        JsonObject data = new JsonObject();
        JsonArray array = new JsonArray();
        unknown.forEach(array::add);
        data.add("names", array);
        calls.request("lookup", data).whenComplete((json, error) -> {
            inFlight = false;
            if (error != null) {
                return;
            }
            long checked = System.currentTimeMillis();
            // кого не вернули — не в сети мода; запоминаем и это, чтобы не спрашивать снова
            unknown.forEach(name -> known.put(key(name), new Entry(null, checked)));
            for (JsonElement element : json.getAsJsonArray("users")) {
                JsonObject user = element.getAsJsonObject();
                Badge badge = new Badge(ClientKind.fromWire(user.has("client") ? user.get("client").getAsString() : ""),
                        user.get("verified").getAsBoolean());
                known.put(key(user.get("name").getAsString()), new Entry(badge, checked));
            }
        });
    }

    public void clear() {
        known.clear();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
