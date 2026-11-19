package dev.no.name.afriends.net.game;

import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.net.model.LinkGroup;

/** Тексты для игрока о группах: отказы сервера, нехватка прав, «участника/участников». */
final class GroupTexts {
    private GroupTexts() {
    }

    static String noRight(MessageImage attachment) {
        if (attachment == null) {
            return "Владелец группы запретил тебе писать сюда";
        }
        if (attachment.voice()) {
            return "Владелец группы запретил тебе голосовые";
        }
        return attachment.animated() ? "Владелец группы запретил тебе гифки" : "Владелец группы запретил тебе картинки";
    }

    static String problem(String code) {
        return switch (code) {
            case "offline" -> "Нет связи с сервером сети";
            case "not_friends" -> "Позвать можно только друга по сети";
            case "group_full" -> "В группе уже " + LinkGroup.MAX_MEMBERS + " участника";
            case "too_many_groups" -> "Слишком много групп";
            case "member_too_many_groups" -> "У друга уже слишком много групп";
            case "not_owner" -> "Это может только владелец группы";
            case "no_permission" -> "Владелец группы не дал тебе на это права";
            case "bad_target" -> "С владельцем группы так нельзя";
            case "bad_prefix" -> "Префикс — до " + GroupRoute.MAX_PREFIX + " символов, без переносов";
            case "not_member" -> "Ты больше не в этой группе";
            case "rate_limited" -> "Слишком часто — подожди немного";
            default -> "Не вышло (" + code + ")";
        };
    }

    static String membersWord(int count) {
        int tens = count % 100;
        int ones = count % 10;
        return tens >= 11 && tens <= 14 ? "участников" : ones == 1 ? "участник" : ones >= 2 && ones <= 4 ? "участника" : "участников";
    }
}
