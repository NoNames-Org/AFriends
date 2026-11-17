package dev.no.name.blooddesigner.net.game;

import dev.no.name.blooddesigner.core.GroupChats;
import dev.no.name.blooddesigner.core.GroupPoll;
import dev.no.name.blooddesigner.core.MessageIds;
import dev.no.name.blooddesigner.core.MessageReply;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.PrivateMessages;
import dev.no.name.blooddesigner.core.ServerScope;
import dev.no.name.blooddesigner.core.SocialData;
import dev.no.name.blooddesigner.net.model.Connection;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkGroup;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import dev.no.name.blooddesigner.net.service.LinkService;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Групповые чаты через сеть: сообщение уходит каждому участнику своим сквозным конвертом, поэтому
 * сервер видит только состав группы. Новые группы из sync сразу появляются в списке чатов. Всё на потоке игры
 */
final class GroupRoute implements GroupChats {
    private final SocialData social;
    private final LinkService link;
    private final Set<String> known = new HashSet<>();

    GroupRoute(SocialData social, LinkService link) {
        this.social = social;
        this.link = link;
    }

    // раз в тик: группы, в которые нас добавили, появляются в списке чатов
    void tick() {
        if (ServerScope.NONE.equals(ServerScope.current())) {
            return;
        }
        for (LinkGroup group : link.snapshot().groups()) {
            if (known.add(group.id())) {
                social.messages().ensureChat(PrivateMessages.groupKey(group.id()));
            }
        }
    }

    Optional<String> send(String chat, String text, MessageReply reply, Consumer<String> failed) {
        String group = PrivateMessages.groupId(chat);
        if (link.snapshot().connection() != Connection.ONLINE) {
            return Optional.of(LinkText.sendProblem("offline"));
        }
        if (link.snapshot().group(group) == null) {
            return Optional.of("Ты больше не в этой группе");
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
                    ? "Ты больше не в этой группе" : "Голос не ушёл: нужна сеть друзей BloodDesigner");
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
                social.messages().delete(chat);
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
            link.sendGroupName(group, title);
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
            case "not_owner" -> "Исключать может только владелец группы";
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
