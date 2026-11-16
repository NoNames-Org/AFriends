package dev.no.name.blooddesigner.net.game;

import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import dev.no.name.blooddesigner.net.model.Presence;

// подписи сети для списка друзей, мессенджера и настроек
public final class LinkText {
    private static final long MINUTE = 60_000;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private LinkText() {
    }

    public static String connection(LinkSnapshot state, boolean configured) {
        if (!configured) {
            return "Сервер сети ещё не настроен";
        }
        if (SharedLinkFolder.busy()) {
            return "Сеть уже открыта в другой копии игры";
        }
        return switch (state.connection()) {
            case ONLINE -> state.onlineCount() > 0 ? "В сети · друзей онлайн: " + state.onlineCount() : "В сети";
            case CONNECTING -> "Подключение…";
            case NAME_TAKEN -> "Ник занят другим аккаунтом сети";
            case DISABLED -> "Сеть выключена";
            case OFFLINE -> "Нет связи с сервером сети";
        };
    }

    public static String where(Presence presence) {
        if (presence == null) {
            return "В сети";
        }
        return switch (presence.where()) {
            case MENU -> "В меню";
            case SINGLEPLAYER -> "Одиночный мир";
            case SERVER -> presence.server();
            case HIDDEN -> "В игре";
        };
    }

    // вторая строка у друга: где он и сколько там, или когда был
    public static String line(LinkFriend friend, long now) {
        if (!friend.online()) {
            return "Был в сети " + ago(now - friend.lastSeen());
        }
        Presence presence = friend.presence();
        String where = where(presence);
        if (presence != null && presence.since() > 0 && presence.where() != Presence.Where.MENU) {
            return where + " · " + duration(now - presence.since());
        }
        return where;
    }

    static String ago(long ms) {
        return ms < MINUTE ? "только что" : duration(ms) + " назад";
    }

    static String duration(long ms) {
        if (ms < HOUR) {
            return Math.max(1, ms / MINUTE) + " мин";
        }
        if (ms < DAY) {
            return ms / HOUR + " ч";
        }
        return ms / DAY + " дн";
    }

    // почему сообщение по сети не ушло
    public static String sendProblem(String code) {
        return switch (code) {
            case "offline" -> "Нет связи с сервером сети";
            case "keys_changed" -> "Ключ друга сменился — сверьте отпечаток, прежде чем писать";
            case "downgraded" -> "Сервер пытается ослабить шифрование — сообщение не отправлено";
            case "mailbox_full" -> "У друга переполнен ящик";
            case "rate_limited" -> "Слишком часто — подождите немного";
            case "not_friends" -> "Вы больше не друзья в сети";
            case "too_long" -> "Слишком длинное сообщение";
            default -> "Не отправлено (" + code + ")";
        };
    }
}
