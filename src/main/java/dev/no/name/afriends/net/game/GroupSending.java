package dev.no.name.afriends.net.game;

import java.util.function.Supplier;
import dev.no.name.afriends.net.media.PreparedImage;
import dev.no.name.afriends.net.rpc.Failures;
import dev.no.name.afriends.net.chat.GroupRights;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.core.GroupPoll;
import dev.no.name.afriends.core.MessageIds;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.net.model.Connection;
import dev.no.name.afriends.net.model.LinkGroup;
import dev.no.name.afriends.net.service.LinkService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Сообщения, картинки, опросы и голоса в группу: проверка прав и отправка каждому участнику. */
final class GroupSending {
    private final SocialData social;
    private final LinkService link;

    GroupSending(SocialData social, LinkService link) {
        this.social = social;
        this.link = link;
    }

    Optional<String> send(String chat, String text, MessageReply reply, Consumer<String> failed) {
        String group = PrivateMessages.groupId(chat);
        Optional<String> blocked = sendBlocker(group, null);
        if (blocked.isPresent()) {
            return blocked;
        }
        String id = MessageIds.next();
        link.groups().sendText(group, text, id, reply).thenAccept(code -> GameThread.run(() -> {
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
            return Optional.of(GroupTexts.noRight(attachment));
        }
        return Optional.empty();
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
                        return CompletableFuture.failedFuture(new IllegalStateException(GroupTexts.noRight(actual)));
                    }
                    return link.groups().sendImage(group, image, id, caption, reply);
                })
                .whenComplete((image, error) -> GameThread.run(() -> {
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

    void createPoll(String chat, String question, List<String> options, Consumer<String> problem) {
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
        link.groups().sendPoll(group, text, id, clean).thenAccept(code -> GameThread.run(() -> {
            if ("ok".equals(code)) {
                social.messages().add(chat, new PrivateMessage(true, text, System.currentTimeMillis(), id, List.of(), null, null,
                        null, null, null, GroupPoll.fresh(clean)));
            } else {
                problem.accept(LinkText.sendProblem(code));
            }
        }));
    }

    // голос сразу виден у себя; не дошёл — возвращаем прежний и говорим почему
    void vote(String chat, String messageId, int choice, Consumer<String> problem) {
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
        social.accountWide().vote(chat, messageId, null, choice);
        link.groups().sendVote(group, messageId, choice).thenAccept(code -> GameThread.run(() -> {
            if (!"ok".equals(code)) {
                social.accountWide().vote(chat, messageId, null, previous);
                problem.accept("Голос не ушёл: " + LinkText.sendProblem(code));
            }
        }));
    }
}
