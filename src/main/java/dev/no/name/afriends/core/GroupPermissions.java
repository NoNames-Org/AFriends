package dev.no.name.afriends.core;

import java.util.List;

/**
 * Права участника группы — битовая маска, как на сервере (nonames-cloud).
 * «Отправка сообщений» — главное право: без него не уходит ничего, даже текст, а подправа (картинки, гифки,
 * голосовые, опросы) действуют только вместе с ним.
 */
public final class GroupPermissions {
    public static final int SEND = 1;
    public static final int IMAGES = 2;
    public static final int GIFS = 4;
    public static final int VOICE = 8;
    public static final int POLLS = 16;
    public static final int TITLE = 32;
    public static final int DESCRIPTION = 64;
    public static final int AVATAR = 128;
    public static final int ADD = 256;
    public static final int KICK = 512;
    public static final int PIN = 1024;
    public static final int ALL = 2047;
    public static final int DEFAULT = SEND | IMAGES | GIFS | VOICE | POLLS | ADD;

    /** Пункт в окне прав; parent — главное право для подправа (0 — само главное). */
    public record Entry(int bit, String title, int parent) {
        public boolean isChild() {
            return parent != 0;
        }
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry(SEND, "Отправка сообщений", 0),
            new Entry(IMAGES, "Отправка картинок", SEND),
            new Entry(GIFS, "Отправка гифок", SEND),
            new Entry(VOICE, "Голосовые сообщения", SEND),
            new Entry(POLLS, "Создание опросов", SEND),
            new Entry(TITLE, "Изменение названия группы", 0),
            new Entry(DESCRIPTION, "Изменение описания группы", 0),
            new Entry(AVATAR, "Изменение аватарки группы", 0),
            new Entry(ADD, "Добавление участников", 0),
            new Entry(KICK, "Исключение участников", 0),
            new Entry(PIN, "Закрепление сообщений", 0));

    private GroupPermissions() {
    }

    /** Есть ли право с учётом главного: подправо без «Отправки сообщений» не действует. */
    public static boolean has(int perms, int bit) {
        if ((perms & bit) == 0) {
            return false;
        }
        Entry entry = entry(bit);
        return entry == null || !entry.isChild() || (perms & entry.parent()) != 0;
    }

    /** Переключить право; выключение главного не трогает подправа — они вернутся, когда его включат. */
    public static int toggle(int perms, int bit) {
        return (perms ^ bit) & ALL;
    }

    private static Entry entry(int bit) {
        for (Entry entry : ENTRIES) {
            if (entry.bit() == bit) {
                return entry;
            }
        }
        return null;
    }
}
