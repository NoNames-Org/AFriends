package dev.no.name.afriends.net.game;

import java.util.function.Supplier;
import java.nio.file.Path;
import dev.no.name.afriends.net.media.PreparedImage;
import dev.no.name.afriends.net.rpc.Failures;
import dev.no.name.afriends.net.chat.GroupRights;
import dev.no.name.afriends.net.chat.ChatPayload;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.GroupPoll;
import dev.no.name.afriends.core.MessageIds;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.ServerScope;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.net.model.Connection;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.model.LinkGroup;
import dev.no.name.afriends.net.model.LinkSnapshot;
import dev.no.name.afriends.net.service.LinkService;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Групповые чаты через сеть: сообщение уходит каждому участнику своим сквозным конвертом, поэтому
 * сервер видит только состав группы. Новые группы из sync сразу появляются в списке чатов. Всё на потоке игры
 */
final class GroupRoute implements GroupChats {
    private final SocialData social;
    private final LinkService link;
    private static final int MAX_PREFIX = 16;
    private final Set<String> known = new HashSet<>();
    // группы, из которых вышли: пока свежий sync не уберёт их из снимка, чат не возвращается и сообщения не принимаются
    private final Set<String> left = ConcurrentHashMap.newKeySet();

    GroupRoute(SocialData social, LinkService link) {
        this.social = social;
        this.link = link;
    }

    // раз в тик: группы, в которые нас добавили, появляются в списке чатов
    void tick() {
        if (ServerScope.NONE.equals(ServerScope.current())) {
            return;
        }
        Set<String> current = new HashSet<>();
        for (LinkGroup group : link.snapshot().groups()) {
            current.add(group.id());
            if (!left.contains(group.id()) && known.add(group.id())) {
                social.messages().ensureChat(PrivateMessages.groupKey(group.id()));
            }
        }
        // группы больше нет в снимке: забываем её — если позовут снова, она опять появится в списке
        known.retainAll(current);
        left.retainAll(current);
    }

    // вышли ли мы из этой группы (пока снимок о ней ещё помнит)
    boolean hasLeft(String group) {
        return left.contains(group);
    }

    Optional<String> send(String chat, String text, MessageReply reply, Consumer<String> failed) {
        String group = PrivateMessages.groupId(chat);
        Optional<String> blocked = sendBlocker(group, null);
        if (blocked.isPresent()) {
            return blocked;
        }
        String id = MessageIds.next();
        link.sendGroupText(group, text, id, reply).thenAccept(code -> onGame(() -> {
            if ("ok".equals(code)) {
                social.messages().add(chat, new PrivateMessage(true, text, System.currentTimeMillis(), id, List.of(), null, null,
                        null, reply));
            } else {
                failed.accept(LinkText.sendProblem(code));
            }
        }));
        return Optional.empty();
    }

    // почему нельзя отправить в группу (нет сети, ты не в группе, нет права); пусто — можно.
    // attachment — вложение (картинка, гифка, голосовое) или null для текста
    Optional<String> sendBlocker(String group, MessageImage attachment) {
        if (link.snapshot().connection() != Connection.ONLINE) {
            return Optional.of(LinkText.sendProblem("offline"));
        }
        LinkGroup known = link.snapshot().group(group);
        if (known == null) {
            return Optional.of("Ты больше не в этой группе");
        }
        if (!GroupRights.canSend(known.myPerms(), attachment)) {
            return Optional.of(noRight(attachment));
        }
        return Optional.empty();
    }

    private static String noRight(MessageImage attachment) {
        if (attachment == null) {
            return "Владелец группы запретил тебе писать сюда";
        }
        if (attachment.voice()) {
            return "Владелец группы запретил тебе голосовые";
        }
        return attachment.animated() ? "Владелец группы запретил тебе гифки" : "Владелец группы запретил тебе картинки";
    }

    // картинка, гифка или голосовое в группу; prepared читается не на потоке игры
    Optional<String> sendMedia(String chat, Supplier<PreparedImage> prepared, MessageImage kind, String caption,
                               MessageReply reply, Consumer<String> failed) {
        String group = PrivateMessages.groupId(chat);
        Optional<String> blocked = sendBlocker(group, kind);
        if (blocked.isPresent()) {
            return blocked;
        }
        String id = MessageIds.next();
        CompletableFuture.supplyAsync(prepared)
                .thenCompose(image -> {
                    MessageImage actual = new MessageImage("", null, image.mime(), 0, 0, 0, "");
                    LinkGroup known = link.snapshot().group(group);
                    if (known == null || !GroupRights.canSend(known.myPerms(), actual)) {
                        return CompletableFuture.failedFuture(new IllegalStateException(noRight(actual)));
                    }
                    return link.sendGroupImage(group, image, id, caption, reply);
                })
                .whenComplete((image, error) -> onGame(() -> {
                    if (error == null) {
                        social.messages().add(chat, new PrivateMessage(true, caption, System.currentTimeMillis(), id,
                                List.of(image), null, null, null, reply));
                    } else {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        failed.accept(cause instanceof IllegalStateException ? cause.getMessage()
                                : LinkText.sendProblem(Failures.codeOf(error)));
                    }
                }));
        return Optional.empty();
    }

    @Override
    public void createPoll(String chat, String question, List<String> options, Consumer<String> problem) {
        String group = PrivateMessages.groupId(chat);
        String text = question.replaceAll("[\\p{Cntrl}§]", " ").replaceAll("\\s+", " ").strip();
        List<String> clean = GroupPoll.cleanOptions(options);
        if (text.isEmpty() || text.length() > GroupPoll.MAX_QUESTION || clean == null) {
            problem.accept("Нужен вопрос и от " + GroupPoll.MIN_OPTIONS + " до " + GroupPoll.MAX_OPTIONS + " разных вариантов");
            return;
        }
        if (link.snapshot().connection() != Connection.ONLINE) {
            problem.accept(LinkText.sendProblem("offline"));
            return;
        }
        if (link.snapshot().group(group) == null) {
            problem.accept("Ты больше не в этой группе");
            return;
        }
        if (!GroupPermissions.has(link.snapshot().group(group).myPerms(), GroupPermissions.POLLS)) {
            problem.accept("Владелец группы запретил тебе создавать опросы");
            return;
        }
        String id = MessageIds.next();
        link.sendGroupPoll(group, text, id, clean).thenAccept(code -> onGame(() -> {
            if ("ok".equals(code)) {
                social.messages().add(chat, new PrivateMessage(true, text, System.currentTimeMillis(), id, List.of(), null, null,
                        null, null, null, GroupPoll.fresh(clean)));
            } else {
                problem.accept(LinkText.sendProblem(code));
            }
        }));
    }

    // голос сразу виден у себя; не дошёл — возвращаем прежний и говорим почему
    @Override
    public void vote(String chat, String messageId, int choice, Consumer<String> problem) {
        String group = PrivateMessages.groupId(chat);
        PrivateMessage message = social.messages().find(chat, messageId);
        if (message == null || message.poll() == null) {
            return;
        }
        if (link.snapshot().connection() != Connection.ONLINE || link.snapshot().group(group) == null) {
            problem.accept(link.snapshot().group(group) == null && link.snapshot().connection() == Connection.ONLINE
                    ? "Ты больше не в этой группе" : "Голос не ушёл: нужна сеть друзей AFriends");
            return;
        }
        int previous = message.poll().mine();
        social.vote(chat, messageId, null, choice);
        link.sendGroupVote(group, messageId, choice).thenAccept(code -> onGame(() -> {
            if (!"ok".equals(code)) {
                social.vote(chat, messageId, null, previous);
                problem.accept("Голос не ушёл: " + LinkText.sendProblem(code));
            }
        }));
    }

    String headerLine(String chat) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        if (group == null) {
            return "Ты не в группе";
        }
        int count = group.members().size() + 1;
        return count + " " + members(count) + " · сквозное шифрование";
    }

    @Override
    public boolean available() {
        return link.snapshot().connection() == Connection.ONLINE;
    }

    @Override
    public List<String> friends() {
        return link.snapshot().friends().stream().map(LinkFriend::name).toList();
    }

    @Override
    public List<String> members(String chat) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        return group == null ? List.of() : group.members().stream().map(LinkFriend::name).toList();
    }

    @Override
    public boolean owned(String chat) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        return group != null && group.owned();
    }

    @Override
    public int myPerms(String chat) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        return group == null ? 0 : group.myPerms();
    }

    @Override
    public int permsOf(String chat, String memberName) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        LinkFriend member = group == null ? null : group.memberNamed(memberName);
        return member == null ? 0 : group.permsOf(member.id());
    }

    @Override
    public String prefixOf(String chat, String memberName) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        LinkFriend member = group == null ? null : group.memberNamed(memberName);
        return member == null ? "" : group.prefixOf(member.id());
    }

    @Override
    public String ownerName(String chat) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        LinkFriend owner = group == null ? null : group.member(group.ownerId());
        return owner == null ? null : owner.name();
    }

    @Override
    public void setPerms(String chat, String memberName, int perms, Consumer<String> problem) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        LinkFriend member = group == null ? null : group.memberNamed(memberName);
        if (member == null) {
            problem.accept("Такого участника нет");
            return;
        }
        link.setGroupPerms(group.id(), member.id(), perms & GroupPermissions.ALL).thenAccept(code -> onGame(() -> {
            if (!"ok".equals(code)) {
                problem.accept(groupProblem(code));
            }
        }));
    }

    @Override
    public void setPrefix(String chat, String memberName, String prefix, Consumer<String> problem) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        String clean = prefix == null ? "" : prefix.replaceAll("[\\p{Cntrl}§]", " ").replaceAll("\\s+", " ").strip();
        if (clean.codePointCount(0, clean.length()) > MAX_PREFIX) {
            problem.accept("Префикс — до " + MAX_PREFIX + " символов");
            return;
        }
        LinkFriend member = group == null ? null : group.memberNamed(memberName);
        String memberId = member != null ? member.id() : group != null && group.owned()
                && memberName.equalsIgnoreCase(link.snapshot().myName()) ? group.ownerId() : null;
        if (memberId == null) {
            problem.accept("Такого участника нет");
            return;
        }
        link.setGroupPrefix(group.id(), memberId, clean).thenAccept(code -> onGame(() -> {
            if (!"ok".equals(code)) {
                problem.accept(groupProblem(code));
            }
        }));
    }

    @Override
    public void setDescription(String chat, String text, Consumer<String> problem) {
        String group = PrivateMessages.groupId(chat);
        if (!GroupPermissions.has(myPerms(chat), GroupPermissions.DESCRIPTION)) {
            problem.accept("Владелец группы запретил тебе менять описание");
            return;
        }
        String clean = text == null ? "" : text.replaceAll("[\\p{Cntrl}§]", " ").replaceAll("\\s+", " ").strip();
        if (clean.length() > ChatPayload.MAX_GROUP_DESCRIPTION) {
            clean = clean.substring(0, ChatPayload.MAX_GROUP_DESCRIPTION);
        }
        social.messages().setDescription(chat, clean);
        link.sendGroupMeta(group, null, clean, null).thenAccept(code -> onGame(() -> {
            if (!"ok".equals(code)) {
                problem.accept("Описание дошло не до всех: " + LinkText.sendProblem(code));
            }
        }));
    }

    @Override
    public void setAvatar(String chat, Path file, Consumer<String> problem) {
        String group = PrivateMessages.groupId(chat);
        if (!GroupPermissions.has(myPerms(chat), GroupPermissions.AVATAR)) {
            problem.accept("Владелец группы запретил тебе менять аватарку");
            return;
        }
        CompletableFuture.supplyAsync(() -> PreparedImage.read(file))
                .thenCompose(link::uploadGroupAvatar)
                .thenCompose(image -> {
                    onGame(() -> social.messages().setAvatar(chat, image));
                    return link.sendGroupMeta(group, null, null, image);
                })
                .whenComplete((code, error) -> onGame(() -> {
                    if (error != null) {
                        problem.accept("Аватарка не загрузилась: " + LinkText.sendProblem(Failures.codeOf(error)));
                    } else if (!"ok".equals(code)) {
                        problem.accept("Аватарка дошла не до всех: " + LinkText.sendProblem(code));
                    }
                }));
    }

    @Override
    public void create(List<String> friendNames, String title, Consumer<String> done, Consumer<String> problem) {
        LinkSnapshot state = link.snapshot();
        List<String> ids = new ArrayList<>();
        for (String name : friendNames) {
            LinkFriend friend = state.friendNamed(name);
            if (friend != null) {
                ids.add(friend.id());
            }
        }
        if (ids.isEmpty() || ids.size() + 1 > LinkGroup.MAX_MEMBERS) {
            problem.accept("Выбери от 1 до " + (LinkGroup.MAX_MEMBERS - 1) + " друзей");
            return;
        }
        link.createGroup(ids).thenAccept(json -> onGame(() -> {
            if (json == null || !json.has("id")) {
                problem.accept("Группа не создалась: нет связи с сервером сети");
                return;
            }
            String group = json.get("id").getAsString();
            String chat = PrivateMessages.groupKey(group);
            known.add(group);
            social.messages().setTitle(chat, title);
            social.messages().ensureChat(chat);
            // название доходит до участников, когда sync отдаст им группу: шлём, как только она есть у нас
            sendTitleWhenReady(group, null, title, 0);
            done.accept(chat);
        }));
    }

    @Override
    public void add(String chat, String friendName, Consumer<String> problem) {
        LinkFriend friend = link.snapshot().friendNamed(friendName);
        String group = PrivateMessages.groupId(chat);
        if (friend == null) {
            problem.accept("Позвать можно только друга по сети");
            return;
        }
        link.addToGroup(group, friend.id()).thenAccept(code -> onGame(() -> {
            if (!"ok".equals(code)) {
                problem.accept(groupProblem(code));
                return;
            }
            // новичку — название группы; он попадёт в наш снимок только после sync, до этого конверт ему не уйдёт
            sendTitleWhenReady(group, friend.id(), social.messages().displayName(chat), 0);
        }));
    }

    @Override
    public void rename(String chat, String title, Consumer<String> problem) {
        social.messages().setTitle(chat, title);
        link.sendGroupName(PrivateMessages.groupId(chat), title).thenAccept(code -> onGame(() -> {
            if (!"ok".equals(code)) {
                problem.accept("Название не дошло до всех: " + LinkText.sendProblem(code));
            }
        }));
    }

    @Override
    public void leave(String chat, Consumer<String> problem) {
        link.leaveGroup(PrivateMessages.groupId(chat)).thenAccept(code -> onGame(() -> {
            if ("ok".equals(code)) {
                left.add(PrivateMessages.groupId(chat));
                social.deleteChatEverywhere(chat);
            } else {
                problem.accept(groupProblem(code));
            }
        }));
    }

    @Override
    public void kick(String chat, String memberName, Consumer<String> problem) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        LinkFriend member = group == null ? null : group.members().stream()
                .filter(item -> item.name().equalsIgnoreCase(memberName)).findFirst().orElse(null);
        if (member == null) {
            problem.accept("Такого участника нет");
            return;
        }
        link.kickFromGroup(group.id(), member.id()).thenAccept(code -> onGame(() -> {
            if (!"ok".equals(code)) {
                problem.accept(groupProblem(code));
            }
        }));
    }

    // участники видны после sync: несколько попыток с паузой, пока группа (и новичок, если он есть) не появится в снимке
    private void sendTitleWhenReady(String group, String newcomer, String title, int attempt) {
        LinkGroup known = link.snapshot().group(group);
        boolean ready = known != null && (newcomer == null || known.member(newcomer) != null);
        if (ready || attempt >= 10) {
            if (newcomer == null) {
                link.sendGroupName(group, title);
            } else {
                // только новичку: у остальных название уже есть, а права на переименование у позвавшего может не быть
                link.sendGroupNameTo(newcomer, group, title);
            }
            return;
        }
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
                .execute(() -> onGame(() -> sendTitleWhenReady(group, newcomer, title, attempt + 1)));
    }

    private static String groupProblem(String code) {
        return switch (code) {
            case "offline" -> "Нет связи с сервером сети";
            case "not_friends" -> "Позвать можно только друга по сети";
            case "group_full" -> "В группе уже " + LinkGroup.MAX_MEMBERS + " участника";
            case "too_many_groups" -> "Слишком много групп";
            case "member_too_many_groups" -> "У друга уже слишком много групп";
            case "not_owner" -> "Это может только владелец группы";
            case "no_permission" -> "Владелец группы не дал тебе на это права";
            case "bad_target" -> "С владельцем группы так нельзя";
            case "bad_prefix" -> "Префикс — до " + MAX_PREFIX + " символов, без переносов";
            case "not_member" -> "Ты больше не в этой группе";
            case "rate_limited" -> "Слишком часто — подожди немного";
            default -> "Не вышло (" + code + ")";
        };
    }

    private static String members(int count) {
        int tens = count % 100;
        int ones = count % 10;
        return tens >= 11 && tens <= 14 ? "участников" : ones == 1 ? "участник" : ones >= 2 && ones <= 4 ? "участника" : "участников";
    }

    private static void onGame(Runnable task) {
        Minecraft.getInstance().execute(task);
    }
}
