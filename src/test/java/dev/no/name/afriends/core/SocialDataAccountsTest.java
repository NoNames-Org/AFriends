package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialDataAccountsTest {
    private static final String SERVER = "bloodstone.boo";

    private final AtomicReference<String> account = new AtomicReference<>("main");
    private final SocialData social = new SocialData(account::get, () -> SERVER);

    // add() спрашивает игру, не свой ли это ник; в тесте игры нет — кладём друзей через fromJson
    private static void befriend(SocialData data, String name) {
        com.google.gson.JsonArray list = data.friends().toJson().getAsJsonArray();
        JsonObject friend = new JsonObject();
        friend.addProperty("name", name);
        list.add(friend);
        data.friends().fromJson(list);
    }

    @Test
    @DisplayName("у каждого аккаунта свои друзья и переписки на одном и том же сервере")
    void accountsAreSeparate() {
        befriend(social, "Steve");
        social.messages().add("Steve", new PrivateMessage(false, "привет", 1));
        account.set("twink");
        assertFalse(social.friends().find("Steve") != null);
        assertTrue(social.messages().chat("Steve").isEmpty());
        befriend(social, "Alex");
        account.set("main");
        assertTrue(social.friends().find("Steve") != null);
        assertFalse(social.friends().find("Alex") != null);
        assertEquals(1, social.messages().chat("Steve").size());
    }

    @Test
    @DisplayName("друг удалил нас в сети — пропадает только у текущего аккаунта")
    void removalStaysInAccount() {
        befriend(social, "Steve");
        account.set("twink");
        befriend(social, "Steve");
        assertTrue(social.accountWide().removeFriend("Steve"));
        assertFalse(social.friends().find("Steve") != null);
        account.set("main");
        assertTrue(social.friends().find("Steve") != null);
    }

    @Test
    @DisplayName("сохранение и загрузка: ключи «аккаунт|сервер» читаются обратно")
    void roundTrip() {
        befriend(social, "Steve");
        account.set("twink");
        befriend(social, "Alex");
        JsonElement saved = social.friendsJson();
        assertTrue(saved.getAsJsonObject().has("main|" + SERVER));
        assertTrue(saved.getAsJsonObject().has("twink|" + SERVER));

        AtomicReference<String> other = new AtomicReference<>("twink");
        SocialData loaded = new SocialData(other::get, () -> SERVER);
        loaded.friendsFromJson(saved);
        assertTrue(loaded.friends().find("Alex") != null);
        assertFalse(loaded.friends().find("Steve") != null);
    }

    @Test
    @DisplayName("данные версии без аккаунтов достаются первому аккаунту, второму — нет")
    void oldDataGoesToFirstAccount() {
        JsonObject old = JsonParser.parseString("{\"" + SERVER + "\":[{\"name\":\"Steve\"}]}").getAsJsonObject();
        SocialData loaded = new SocialData(account::get, () -> SERVER);
        loaded.friendsFromJson(old);
        assertTrue(loaded.friendsJson().getAsJsonObject().has(SERVER));
        assertTrue(loaded.friends().find("Steve") != null);
        assertTrue(loaded.friendsJson().getAsJsonObject().has("main|" + SERVER));
        account.set("twink");
        assertFalse(loaded.friends().find("Steve") != null);
    }
}
