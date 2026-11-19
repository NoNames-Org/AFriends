package dev.no.name.afriends.net.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.net.chat.ChatPayload;
import dev.no.name.afriends.net.media.PreparedImage;
import dev.no.name.afriends.net.tunnel.TunnelError;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Групповые чаты в сети: состав и права — через сервер, а сообщения, название, описание и аватарка —
 * каждому участнику сквозным конвертом
 */
public final class GroupGateway {
    private final LinkCalls calls;

    GroupGateway(LinkCalls calls) {
        this.calls = calls;
    }

    // новая группа из друзей; в ответе id группы или null, если не вышло (тогда code — причина)
    public CompletableFuture<JsonObject> create(List<String> memberIds) {
        JsonObject data = new JsonObject();
        JsonArray members = new JsonArray();
        memberIds.forEach(members::add);
        data.add("members", members);
        return calls.query("group.create", data).thenApply(json -> {
            calls.requestSync();
            return json;
        });
    }

    public CompletableFuture<String> add(String group, String friendId) {
        return calls.action("group.add", member(group, friendId));
    }

    public CompletableFuture<String> leave(String group) {
        JsonObject data = new JsonObject();
        data.addProperty("group", group);
        return calls.action("group.leave", data);
    }

    public CompletableFuture<String> kick(String group, String memberId) {
        return calls.action("group.kick", member(group, memberId));
    }

    // права участника (только владелец); «ok» или код отказа
    public CompletableFuture<String> setPerms(String group, String memberId, int perms) {
        JsonObject data = member(group, memberId);
        data.addProperty("perms", perms);
        return calls.action("group.perms", data);
    }

    // префикс участника (только владелец); пустой — убрать
    public CompletableFuture<String> setPrefix(String group, String memberId, String prefix) {
        JsonObject data = member(group, memberId);
        data.addProperty("prefix", prefix);
        return calls.action("group.prefix", data);
    }

    // «ok» — дошло до всех, иначе код первого отказа
    public CompletableFuture<String> sendText(String group, String text, String id, MessageReply reply) {
        return calls.sendToGroup(group, new ChatPayload.Text(text, id, null, null, reply, group));
    }

    // опрос: вопрос — text, варианты — options; голосуют по id
    public CompletableFuture<String> sendPoll(String group, String text, String id, List<String> options) {
        return calls.sendToGroup(group, new ChatPayload.Text(text, id, null, null, null, group, options));
    }

    // свой голос в опросе всем участникам; choice -1 — отозвать
    public CompletableFuture<String> sendVote(String group, String target, int choice) {
        return calls.sendToGroup(group, new ChatPayload.Vote(group, target, choice));
    }

    public CompletableFuture<String> sendName(String group, String name) {
        return calls.sendToGroup(group, new ChatPayload.GroupName(group, name));
    }

    // название одному участнику — новичку, которого только что позвали
    public CompletableFuture<String> sendNameTo(String peerId, String group, String name) {
        return calls.send(peerId, new ChatPayload.GroupName(group, name));
    }

    // описание и/или аватарка всем участникам; null — поле не меняется
    public CompletableFuture<String> sendMeta(String group, String name, String description, MessageImage avatar) {
        return calls.sendToGroup(group, new ChatPayload.GroupMeta(group, name, description, avatar));
    }

    // картинка или голосовое: заливается один раз, ключ уходит каждому участнику
    public CompletableFuture<MessageImage> sendImage(String group, PreparedImage prepared, String id, String caption,
                                                     MessageReply reply) {
        return calls.upload(prepared).thenCompose(image ->
                calls.sendToGroup(group, new ChatPayload.Text(caption, id, image, null, reply, group))
                        .thenCompose(code -> "ok".equals(code) ? CompletableFuture.completedFuture(image)
                                : CompletableFuture.failedFuture(new TunnelError(code))));
    }

    // аватарка заливается так же, как картинка; описание рассылает sendMeta
    public CompletableFuture<MessageImage> uploadAvatar(PreparedImage prepared) {
        return calls.upload(prepared);
    }

    private static JsonObject member(String group, String memberId) {
        JsonObject data = new JsonObject();
        data.addProperty("group", group);
        data.addProperty("id", memberId);
        return data;
    }
}
