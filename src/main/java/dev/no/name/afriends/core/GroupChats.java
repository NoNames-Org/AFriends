package dev.no.name.afriends.core;

import java.util.List;
import java.util.function.Consumer;

/**
 * Групповые чаты для окон: окна не знают про сеть. Чат группы — ключ {@link PrivateMessages#groupKey}.
 * problem получает текст причины, если не вышло (на потоке игры)
 */
public interface GroupChats {
    int MAX_TITLE = 32;

    // сеть подключена: без неё группы не создать и не изменить
    boolean available();

    // друзья по сети, которых можно позвать в группу
    List<String> friends();

    // участники кроме себя
    List<String> members(String chat);

    boolean owned(String chat);

    // done получает ключ чата новой группы
    void create(List<String> friendNames, String title, Consumer<String> done, Consumer<String> problem);

    void add(String chat, String friendName, Consumer<String> problem);

    void rename(String chat, String title, Consumer<String> problem);

    void leave(String chat, Consumer<String> problem);

    void kick(String chat, String memberName, Consumer<String> problem);

    // опрос в группу: вопрос и варианты (см. GroupPoll.cleanOptions)
    void createPoll(String chat, String question, List<String> options, Consumer<String> problem);

    // свой голос в опросе группы; choice -1 — отозвать
    void vote(String chat, String messageId, int choice, Consumer<String> problem);

    // ---- права, префиксы, описание и аватарка ----

    // свои права в группе (GroupPermissions); владелец — все
    default int myPerms(String chat) {
        return owned(chat) ? GroupPermissions.ALL : GroupPermissions.DEFAULT;
    }

    // права участника по нику
    default int permsOf(String chat, String memberName) {
        return GroupPermissions.DEFAULT;
    }

    default String prefixOf(String chat, String memberName) {
        return "";
    }

    // ник владельца; null — неизвестно (или это ты)
    default String ownerName(String chat) {
        return null;
    }

    default void setPerms(String chat, String memberName, int perms, Consumer<String> problem) {
        problem.accept("Права меняются только через сеть AFriends");
    }

    default void setPrefix(String chat, String memberName, String prefix, Consumer<String> problem) {
        problem.accept("Префиксы меняются только через сеть AFriends");
    }

    // описание группы всем участникам; пустое — убрать
    default void setDescription(String chat, String text, Consumer<String> problem) {
        problem.accept("Описание меняется только через сеть AFriends");
    }

    // аватарка группы из файла картинки
    default void setAvatar(String chat, java.nio.file.Path file, Consumer<String> problem) {
        problem.accept("Аватарка меняется только через сеть AFriends");
    }
}
