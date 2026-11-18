package dev.no.name.afriends.net.chat;

import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.net.model.LinkFriend;

import java.util.List;

// что из входящих конвертов уходит в игру; вызывается с потока сети
public interface InboxListener {
    void text(LinkFriend from, String text, long time);

    // конверт пришёл, но не расшифровался: подделка, сбитая сессия или сменившиеся ключи
    void unreadable(LinkFriend from, long time);

    // сообщение с id (для реакций) и, может быть, картинкой; по умолчанию — как простой текст
    default void message(LinkFriend from, String text, String id, MessageImage image, long time) {
        text(from, text, time);
    }

    // то же, но пересланное: forwardedFrom — чьё оно было
    default void message(LinkFriend from, String text, String id, MessageImage image, String forwardedFrom, long time) {
        message(from, text, id, image, time);
    }

    // то же, но ответ на сообщение reply
    default void message(LinkFriend from, String text, String id, MessageImage image, String forwardedFrom,
                         MessageReply reply, long time) {
        message(from, text, id, image, forwardedFrom, time);
    }

    // друг удалил у обоих сообщения с этими id
    default void deleted(LinkFriend from, List<String> targets, long time) {
    }

    // друг закрепил (pinned) или открепил у обоих сообщение с этим id
    default void pinned(LinkFriend from, String target, boolean pinned, long time) {
    }

    // друг поставил (reaction != null) или снял реакцию на сообщение с этим id
    default void reaction(LinkFriend from, String target, String reaction, long time) {
    }

    // сообщение в групповом чате group от участника from
    default void groupMessage(String group, LinkFriend from, String text, String id, MessageImage image,
                              MessageReply reply, long time) {
    }

    // опрос в групповом чате: вопрос — text, варианты — options
    default void groupPoll(String group, LinkFriend from, String text, String id, List<String> options,
                           MessageReply reply, long time) {
    }

    // участник from проголосовал в опросе группы (choice -1 — отозвал голос)
    default void groupVote(String group, LinkFriend from, String target, int choice, long time) {
    }

    // участник from назвал или переименовал группу
    // описание / аватарка / название группы от участника с правом на это (проверено до вызова)
    default void groupMeta(String group, LinkFriend from, String name, String description, MessageImage avatar, long time) {
        if (name != null) {
            groupNamed(group, from, name, time);
        }
    }

    // отметки «доставлено» / «прочитано» на свои сообщения
    default void receipt(LinkFriend from, List<String> targets, boolean read, long time) {
    }

    // сообщение участника, у которого нет права так писать: не показываем
    default void groupRejected(String group, LinkFriend from, long time) {
    }

    // название от участника без права переименовывать: годится, только если у нас названия ещё нет
    // (так новичок узнаёт название от того, кто его позвал)
    default void groupNameOffered(String group, LinkFriend from, String name, long time) {
    }

    default void groupNamed(String group, LinkFriend from, String name, long time) {
    }

    // личное сообщение от AFriends с портала (не от друга, без шифрования); по умолчанию никуда не идёт
    default void official(String text, List<MessageImage> images, long time) {
        official(text, time);
    }

    default void official(String text, long time) {
    }
}
