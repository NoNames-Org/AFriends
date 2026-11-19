package dev.no.name.afriends.net.game;

import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.ServerScope;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.model.LinkGroup;
import dev.no.name.afriends.net.model.LinkSnapshot;
import dev.no.name.afriends.net.service.LinkService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Состав групп: создание, приглашение, выход и исключение; новые группы из sync — в список чатов. */
final class GroupMembership {
    private final SocialData social;
    private final LinkService link;
    private final Set<String> known = new HashSet<>();
    // группы, из которых вышли: пока свежий sync не уберёт их из снимка, чат не возвращается и сообщения не принимаются
    private final Set<String> left = ConcurrentHashMap.newKeySet();

    GroupMembership(SocialData social, LinkService link) {
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

    void create(List<String> friendNames, String title, Consumer<String> done, Consumer<String> problem) {
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
        link.groups().create(ids).thenAccept(json -> GameThread.run(() -> {
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

    void add(String chat, String friendName, Consumer<String> problem) {
        LinkFriend friend = link.snapshot().friendNamed(friendName);
        String group = PrivateMessages.groupId(chat);
        if (friend == null) {
            problem.accept("Позвать можно только друга по сети");
            return;
        }
        link.groups().add(group, friend.id()).thenAccept(code -> GameThread.run(() -> {
            if (!"ok".equals(code)) {
                problem.accept(GroupTexts.problem(code));
                return;
            }
            // новичку — название группы; он попадёт в наш снимок только после sync, до этого конверт ему не уйдёт
            sendTitleWhenReady(group, friend.id(), social.messages().displayName(chat), 0);
        }));
    }

    void rename(String chat, String title, Consumer<String> problem) {
        social.messages().setTitle(chat, title);
        link.groups().sendName(PrivateMessages.groupId(chat), title).thenAccept(code -> GameThread.run(() -> {
            if (!"ok".equals(code)) {
                problem.accept("Название не дошло до всех: " + LinkText.sendProblem(code));
            }
        }));
    }

    void leave(String chat, Consumer<String> problem) {
        link.groups().leave(PrivateMessages.groupId(chat)).thenAccept(code -> GameThread.run(() -> {
            if ("ok".equals(code)) {
                left.add(PrivateMessages.groupId(chat));
                social.accountWide().deleteChat(chat);
            } else {
                problem.accept(GroupTexts.problem(code));
            }
        }));
    }

    void kick(String chat, String memberName, Consumer<String> problem) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        LinkFriend member = group == null ? null : group.members().stream()
                .filter(item -> item.name().equalsIgnoreCase(memberName)).findFirst().orElse(null);
        if (member == null) {
            problem.accept("Такого участника нет");
            return;
        }
        link.groups().kick(group.id(), member.id()).thenAccept(code -> GameThread.run(() -> {
            if (!"ok".equals(code)) {
                problem.accept(GroupTexts.problem(code));
            }
        }));
    }

    // участники видны после sync: несколько попыток с паузой, пока группа (и новичок, если он есть) не появится в снимке
    private void sendTitleWhenReady(String group, String newcomer, String title, int attempt) {
        LinkGroup known = link.snapshot().group(group);
        boolean ready = known != null && (newcomer == null || known.member(newcomer) != null);
        if (ready || attempt >= 10) {
            if (newcomer == null) {
                link.groups().sendName(group, title);
            } else {
                // только новичку: у остальных название уже есть, а права на переименование у позвавшего может не быть
                link.groups().sendNameTo(newcomer, group, title);
            }
            return;
        }
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
                .execute(() -> GameThread.run(() -> sendTitleWhenReady(group, newcomer, title, attempt + 1)));
    }
}
