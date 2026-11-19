package dev.no.name.afriends.core;

import java.util.Locale;

/**
 * Какой это чат. Снаружи чат по-прежнему передаётся строкой (так он хранится в файле и уходит в сеть),
 * а этот тип разбирает строку один раз, чтобы код ветвился по виду чата через {@code switch},
 * а не сравнивал строки с «$»
 */
public sealed interface ChatId {
    /** «Избранное»: свои заметки. */
    String NOTES_KEY = "$notes";
    /** Личные сообщения от AFriends с портала. «$» не бывает в нике, поэтому игрок с ником AFriends сюда не попадёт. */
    String OFFICIAL_KEY = "$blooddesigner";
    /** Групповой чат: «$g:» + id группы с сервера сети. */
    String GROUP_PREFIX = "$g:";

    ChatId NOTES = new Notes();
    ChatId OFFICIAL = new Official();

    /** Личка с игроком; name — ник в том регистре, в каком пришёл. */
    record Direct(String name) implements ChatId {
        @Override
        public String key() {
            return name.toLowerCase(Locale.ROOT);
        }

        @Override
        public String raw() {
            return name;
        }
    }

    record Group(String id) implements ChatId {
        @Override
        public String key() {
            return GROUP_PREFIX + id;
        }
    }

    record Notes() implements ChatId {
        @Override
        public String key() {
            return NOTES_KEY;
        }
    }

    record Official() implements ChatId {
        @Override
        public String key() {
            return OFFICIAL_KEY;
        }
    }

    static ChatId of(String chat) {
        if (NOTES_KEY.equals(chat)) {
            return NOTES;
        }
        if (OFFICIAL_KEY.equals(chat)) {
            return OFFICIAL;
        }
        if (chat != null && chat.startsWith(GROUP_PREFIX)) {
            return new Group(chat.substring(GROUP_PREFIX.length()));
        }
        return new Direct(chat);
    }

    static ChatId group(String groupId) {
        return new Group(groupId);
    }

    /** Ключ хранения: служебные чаты как есть, ник — без учёта регистра. */
    String key();

    /** Строка, которой чат передаётся по коду: ник как есть или служебный ключ. */
    default String raw() {
        return key();
    }

    /** Служебный чат: его ключ не ник и хранится как есть. */
    default boolean reserved() {
        return !(this instanceof Direct);
    }

    static String storageKey(String chat) {
        return of(chat).key();
    }

    static boolean isReserved(String chat) {
        return of(chat).reserved();
    }
}
