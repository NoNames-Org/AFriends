package dev.no.name.afriends.net.game;

import java.util.function.Supplier;
import java.nio.file.Path;
import dev.no.name.afriends.net.media.PreparedImage;
import dev.no.name.afriends.net.rpc.Failures;
import dev.no.name.afriends.net.chat.ChatPayload;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.net.model.Connection;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.model.LinkGroup;
import dev.no.name.afriends.net.service.LinkService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Групповые чаты через сеть: сообщение уходит каждому участнику своим сквозным конвертом, поэтому
 * сервер видит только состав группы. Новые группы из sync сразу появляются в списке чатов. Всё на потоке игры
 */
final class GroupRoute implements GroupChats {
    private final SocialData social;
    private final LinkService link;
    static final int MAX_PREFIX = 16;
    private final GroupSending sending;
    private final GroupMembership membership;

    GroupRoute(SocialData social, LinkService link) {
        this.social = social;
        this.link = link;
        this.sending = new GroupSending(social, link);
        this.membership = new GroupMembership(social, link);
    }

    // ---- состав ----

    void tick() {
        membership.tick();
    }

    boolean hasLeft(String group) {
        return membership.hasLeft(group);
    }

    @Override
    public void create(List<String> friendNames, String title, Consumer<String> done, Consumer<String> problem) {
        membership.create(friendNames, title, done, problem);
    }

    @Override
    public void add(String chat, String friendName, Consumer<String> problem) {
        membership.add(chat, friendName, problem);
    }

    @Override
    public void rename(String chat, String title, Consumer<String> problem) {
        membership.rename(chat, title, problem);
    }

    @Override
    public void leave(String chat, Consumer<String> problem) {
        membership.leave(chat, problem);
    }

    @Override
    public void kick(String chat, String memberName, Consumer<String> problem) {
        membership.kick(chat, memberName, problem);
    }

    // ---- сообщения ----

    Optional<String> send(String chat, String text, MessageReply reply, Consumer<String> failed) {
        return sending.send(chat, text, reply, failed);
    }

    Optional<String> sendBlocker(String group, MessageImage attachment) {
        return sending.sendBlocker(group, attachment);
    }

    Optional<String> sendMedia(String chat, Supplier<PreparedImage> prepared, MessageImage kind, String caption,
                               MessageReply reply, Consumer<String> failed) {
        return sending.sendMedia(chat, prepared, kind, caption, reply, failed);
    }

    @Override
    public void createPoll(String chat, String question, List<String> options, Consumer<String> problem) {
        sending.createPoll(chat, question, options, problem);
    }

    @Override
    public void vote(String chat, String messageId, int choice, Consumer<String> problem) {
        sending.vote(chat, messageId, choice, problem);
    }

    // ---- сведения и права ----

    String headerLine(String chat) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        if (group == null) {
            return "Ты не в группе";
        }
        int count = group.members().size() + 1;
        return count + " " + GroupTexts.membersWord(count) + " · сквозное шифрование";
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
        link.groups().setPerms(group.id(), member.id(), perms & GroupPermissions.ALL).thenAccept(code -> GameThread.run(() -> {
            if (!"ok".equals(code)) {
                problem.accept(GroupTexts.problem(code));
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
        link.groups().setPrefix(group.id(), memberId, clean).thenAccept(code -> GameThread.run(() -> {
            if (!"ok".equals(code)) {
                problem.accept(GroupTexts.problem(code));
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
        link.groups().sendMeta(group, null, clean, null).thenAccept(code -> GameThread.run(() -> {
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
                .thenCompose(link.groups()::uploadAvatar)
                .thenCompose(image -> {
                    GameThread.run(() -> social.messages().setAvatar(chat, image));
                    return link.groups().sendMeta(group, null, null, image);
                })
                .whenComplete((code, error) -> GameThread.run(() -> {
                    if (error != null) {
                        problem.accept("Аватарка не загрузилась: " + LinkText.sendProblem(Failures.codeOf(error)));
                    } else if (!"ok".equals(code)) {
                        problem.accept("Аватарка дошла не до всех: " + LinkText.sendProblem(code));
                    }
                }));
    }

    @Override
    public List<String> members(String chat) {
        LinkGroup group = link.snapshot().group(PrivateMessages.groupId(chat));
        return group == null ? List.of() : group.members().stream().map(LinkFriend::name).toList();
    }

}
