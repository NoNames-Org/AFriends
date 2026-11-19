package dev.no.name.afriends.net.service;

import com.google.gson.JsonObject;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.net.chat.ChatPayload;
import dev.no.name.afriends.net.media.PreparedImage;
import dev.no.name.afriends.net.tunnel.TunnelError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Шлюзы собирают те же запросы и конверты, что раньше собирал LinkService. Транспорт подменён записью. */
class GatewaysTest {
    private static final MessageImage IMAGE = new MessageImage("0123456789abcdef0123456789abcdef", "k", "image/png",
            10, 10, 100, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    private static final class Recording implements LinkCalls {
        private final List<String> ops = new ArrayList<>();
        private final List<JsonObject> data = new ArrayList<>();
        private final List<ChatPayload> payloads = new ArrayList<>();
        private final List<String> targets = new ArrayList<>();
        private String sendResult = "ok";
        private int syncs;

        @Override
        public CompletableFuture<String> action(String op, JsonObject body) {
            ops.add("action:" + op);
            data.add(body);
            return CompletableFuture.completedFuture("ok");
        }

        @Override
        public CompletableFuture<JsonObject> query(String op, JsonObject body) {
            ops.add("query:" + op);
            data.add(body);
            return CompletableFuture.completedFuture(new JsonObject());
        }

        @Override
        public CompletableFuture<String> request(String op, JsonObject body) {
            ops.add("request:" + op);
            data.add(body);
            return CompletableFuture.completedFuture("ok");
        }

        @Override
        public CompletableFuture<String> send(String peerId, ChatPayload payload) {
            targets.add("peer:" + peerId);
            payloads.add(payload);
            return CompletableFuture.completedFuture(sendResult);
        }

        @Override
        public CompletableFuture<String> sendToGroup(String groupId, ChatPayload payload) {
            targets.add("group:" + groupId);
            payloads.add(payload);
            return CompletableFuture.completedFuture(sendResult);
        }

        @Override
        public CompletableFuture<MessageImage> upload(PreparedImage prepared) {
            ops.add("upload");
            return CompletableFuture.completedFuture(IMAGE);
        }

        @Override
        public CompletableFuture<MessageImage> sendOfficialImage(PreparedImage prepared, String caption) {
            ops.add("official-image:" + caption);
            return CompletableFuture.completedFuture(IMAGE);
        }

        @Override
        public void requestSync() {
            syncs++;
        }
    }

    private final Recording calls = new Recording();

    @Test
    @DisplayName("заявки в друзья уходят теми же действиями сервера")
    void friendsActions() {
        FriendsGateway friends = new FriendsGateway(calls);
        friends.request("Bob");
        friends.respond("id1", true);
        friends.remove("id2");

        assertEquals(List.of("action:friends.request", "action:friends.respond", "action:friends.remove"), calls.ops);
        assertEquals("Bob", calls.data.get(0).get("name").getAsString());
        assertTrue(calls.data.get(1).get("accept").getAsBoolean());
        assertEquals("id2", calls.data.get(2).get("id").getAsString());
    }

    @Test
    @DisplayName("текст другу — сквозной конверт со всеми полями OutgoingText")
    void directText() {
        MessageReply reply = new MessageReply("m0", "Bob", "исходное");
        new DirectGateway(calls).sendText("peer1", new OutgoingText("привет", "m1", "Ann", reply));

        assertEquals(List.of("peer:peer1"), calls.targets);
        ChatPayload.Text text = assertInstanceOf(ChatPayload.Text.class, calls.payloads.get(0));
        assertEquals("привет", text.text());
        assertEquals("m1", text.id());
        assertEquals("Ann", text.forwardedFrom());
        assertEquals(reply, text.reply());
        assertNull(text.image());
        assertNull(text.group());
    }

    @Test
    @DisplayName("картинка другу: сначала заливка, потом конверт с картинкой; отказ — исключение с кодом")
    void directImage() throws Exception {
        DirectGateway direct = new DirectGateway(calls);
        MessageImage sent = direct.sendImage("peer1", null, OutgoingText.of("подпись", "m2")).get();

        assertSame(IMAGE, sent);
        assertEquals(List.of("upload"), calls.ops);
        assertSame(IMAGE, assertInstanceOf(ChatPayload.Text.class, calls.payloads.get(0)).image());

        calls.sendResult = "not_friends";
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> direct.sendImage("peer1", null, OutgoingText.of("x")).get());
        assertInstanceOf(TunnelError.class, error.getCause());
    }

    @Test
    @DisplayName("группа: состав — через сервер, сообщения — участникам; создание запрашивает сверку")
    void groupOperations() {
        GroupGateway groups = new GroupGateway(calls);
        groups.create(List.of("a", "b"));
        groups.setPerms("g1", "m1", 7);
        groups.sendText("g1", "всем", "m3", null);
        groups.sendVote("g1", "poll1", 2);
        groups.sendNameTo("peer9", "g1", "Клан");

        assertEquals(List.of("query:group.create", "action:group.perms"), calls.ops);
        assertEquals(1, calls.syncs);
        assertEquals(2, calls.data.get(0).getAsJsonArray("members").size());
        assertEquals(7, calls.data.get(1).get("perms").getAsInt());
        assertEquals(List.of("group:g1", "group:g1", "peer:peer9"), calls.targets);
        assertEquals("g1", assertInstanceOf(ChatPayload.Text.class, calls.payloads.get(0)).group());
        assertEquals(new ChatPayload.Vote("g1", "poll1", 2), calls.payloads.get(1));
        assertEquals(new ChatPayload.GroupName("g1", "Клан"), calls.payloads.get(2));
    }

    @Test
    @DisplayName("профиль и чат AFriends")
    void profileAndOfficial() {
        new ProfileGateway(calls).setDmFromEveryone(false);
        OfficialGateway official = new OfficialGateway(calls);
        official.send("помогите");
        official.sendImage(null, "скрин");

        assertEquals(List.of("action:settings.dm", "request:official.send", "official-image:скрин"), calls.ops);
        assertEquals("friends", calls.data.get(0).get("value").getAsString());
        assertEquals("помогите", calls.data.get(1).get("text").getAsString());
    }
}
