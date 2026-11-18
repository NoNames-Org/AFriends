package dev.no.name.afriends.net.game;

import dev.no.name.afriends.core.ProfileRoute;
import dev.no.name.afriends.net.model.Connection;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.model.LinkGroup;
import dev.no.name.afriends.net.model.LinkSnapshot;
import dev.no.name.afriends.net.service.LinkService;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Профиль и личные чаты без дружбы: всё через сервер сети, ответы — на потоке игры. */
final class NetworkProfile implements ProfileRoute {
    // карточка собеседника приходит следующим sync: ждём её столько попыток по 250 мс
    private static final int CHAT_WAIT_ATTEMPTS = 12;

    private final LinkService link;

    NetworkProfile(LinkService link) {
        this.link = link;
    }

    @Override
    public boolean available() {
        LinkSnapshot state = link.snapshot();
        return state.connection() == Connection.ONLINE && state.synced();
    }

    @Override
    public String about() {
        return link.snapshot().profile().about();
    }

    @Override
    public void setAbout(String text, Consumer<String> problem) {
        String clean = text == null ? "" : text.replaceAll("[\\p{Cntrl}§]", " ").replaceAll("\\s+", " ").strip();
        if (clean.codePointCount(0, clean.length()) > MAX_ABOUT) {
            problem.accept("Описание — до " + MAX_ABOUT + " символов");
            return;
        }
        link.setAbout(clean).thenAccept(code -> onGame(() -> report(code, problem)));
    }

    @Override
    public boolean dmFromEveryone() {
        return link.snapshot().profile().dmEveryone();
    }

    @Override
    public void setDmFromEveryone(boolean everyone, Consumer<String> problem) {
        link.setDmFromEveryone(everyone).thenAccept(code -> onGame(() -> report(code, problem)));
    }

    @Override
    public String aboutOf(String name) {
        LinkFriend peer = findPeer(name);
        return peer == null ? "" : link.snapshot().profile().aboutOf(peer.id());
    }

    @Override
    public void startChat(String name, Runnable done, Runnable notInNetwork, Consumer<String> problem) {
        if (link.snapshot().directNamed(name) != null) {
            done.run();
            return;
        }
        if (!available()) {
            notInNetwork.run();
            return;
        }
        link.openChat(name).thenAccept(code -> onGame(() -> {
            switch (code) {
                case "ok" -> waitForCard(name, done, 0);
                case "not_found" -> notInNetwork.run();
                default -> problem.accept(chatProblem(code));
            }
        }));
    }

    // чат открыт, но ключи собеседника приходят со следующим sync — до этого конверт ему не уйдёт
    private void waitForCard(String name, Runnable done, int attempt) {
        if (link.snapshot().directNamed(name) != null || attempt >= CHAT_WAIT_ATTEMPTS) {
            done.run();
            return;
        }
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS)
                .execute(() -> onGame(() -> waitForCard(name, done, attempt + 1)));
    }

    @Override
    public boolean isBlocked(String name) {
        LinkFriend peer = findPeer(name);
        return peer != null && link.snapshot().profile().isBlocked(peer.id());
    }

    @Override
    public void setBlocked(String name, boolean blocked, Consumer<String> problem) {
        LinkFriend peer = findPeer(name);
        if (peer == null) {
            problem.accept("Этого игрока нет в сети AFriends");
            return;
        }
        link.blockChat(peer.id(), blocked).thenAccept(code -> onGame(() -> report(code, problem)));
    }

    // игрок, которого мы знаем по сети: друг, собеседник или участник общей группы
    private LinkFriend findPeer(String name) {
        LinkSnapshot state = link.snapshot();
        LinkFriend peer = state.directNamed(name);
        if (peer != null) {
            return peer;
        }
        for (LinkGroup group : state.groups()) {
            LinkFriend member = group.memberNamed(name);
            if (member != null) {
                return member;
            }
        }
        return null;
    }

    private static void report(String code, Consumer<String> problem) {
        if (!"ok".equals(code)) {
            problem.accept(chatProblem(code));
        }
    }

    static String chatProblem(String code) {
        return switch (code) {
            case "dm_closed" -> "Этот игрок принимает сообщения только от друзей";
            case "blocked" -> "Переписка с этим игроком заблокирована";
            case "rate_limited" -> "Слишком много новых чатов — подожди немного";
            case "bad_text" -> "Описание — до " + MAX_ABOUT + " символов, без переносов строк";
            case "bad_target" -> "Самому себе написать нельзя — для заметок есть «Избранное»";
            case "limit" -> "Слишком много заблокированных";
            default -> LinkText.sendProblem(code);
        };
    }

    private static void onGame(Runnable task) {
        Minecraft.getInstance().execute(task);
    }
}
