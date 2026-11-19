package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.addon.AddonRegistry;
import dev.no.name.afriends.api.MessageCard;
import dev.no.name.afriends.core.PollView;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.gui.media.ChatMedia;
import dev.no.name.afriends.net.announcements.AnnouncementFeed;

import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Всё, что нужно пузырям открытого чата помимо самих сообщений: медиа, карточки и режим стримера от аддонов,
 * опросы объявлений, отметки о прочтении и префиксы авторов группы. Собирается окном на каждый кадр
 *
 * @param receipts отметка на своё сообщение по id ({@code PrivateMessages.RECEIPT_*})
 * @param prefixes префикс автора в группе по нику («Админ» → «[Админ] Bob»)
 */
record BubbleContext(ChatMedia media, AddonRegistry addons, AnnouncementFeed announcements,
                     ToIntFunction<String> receipts, Function<String, String> prefixes) {

    /** Режим стримера: текст сообщений не показываем. */
    boolean hidden() {
        return addons.hideMessages();
    }

    MessageCard.Card card(String partner, PrivateMessage message) {
        return addons.card(partner, message.text(), message.fromMe());
    }

    /** Опрос сообщения: свой у сообщения или опрос объявления. */
    PollView poll(PrivateMessage message) {
        return message.poll() != null ? message.poll() : announcements.poll(message);
    }

    int receipt(String messageId) {
        return receipts.applyAsInt(messageId);
    }

    String authorLabel(PrivateMessage message) {
        String prefix = prefixes.apply(message.author());
        return prefix == null || prefix.isEmpty() ? message.author() : "[" + prefix + "] " + message.author();
    }
}
