package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.gui.widget.ConfirmBox;
import dev.no.name.afriends.core.MessageContent;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.gui.render.icon.Icon;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Действия с сообщением, в порядке Telegram Desktop: Ответить, Закрепить, (у картинки) Сохранить как… /
 * Копировать картинку / Открыть / ★, (у текста) Копировать текст, Переслать, Удалить, Выделить.
 * Удаление и закреп спрашивают подтверждение с галочкой «Также … для <ник>», как в личке Telegram:
 * удалить у обоих — по умолчанию да, закрепить у обоих — по умолчанию нет
 */
final class MessageActions {
    // что знает и умеет окно переписки
    interface Host {
        String partner();

        // как зовут собеседника: в галочке «Также удалить для …» и как автора его сообщений в ответе
        String partnerName();

        // можно ли здесь отвечать, закреплять, удалять и выделять (в «Объявлениях» — нет)
        boolean editable();

        void reply(MessageReply reply);

        void forward(List<PrivateMessage> messages);

        void select(PrivateMessage message);

        void confirm(ConfirmBox.Request request);

        // удалили выделенные — выделение закончено
        void deleted();
    }

    private final MessageRoute route;
    private final PrivateMessages messages;
    private final MediaActions media;
    private final Consumer<String> warn;
    private final Consumer<String> info;
    private final Host host;

    MessageActions(MessageRoute route, PrivateMessages messages, MediaActions media, Consumer<String> warn,
                   Consumer<String> info, Host host) {
        this.route = route;
        this.messages = messages;
        this.media = media;
        this.warn = warn;
        this.info = info;
        this.host = host;
    }

    // пункты меню для сообщения; image — картинка под курсором (null — кликнули не по картинке)
    List<ChatMenu.Item> items(PrivateMessage message, MessageImage image) {
        List<ChatMenu.Item> items = new ArrayList<>();
        boolean editable = host.editable();
        if (editable) {
            items.add(new ChatMenu.Item(Icon.UNDO, "Ответить", false, () -> host.reply(replyTo(message))));
            boolean pinned = messages.isMessagePinned(host.partner(), message.key());
            items.add(new ChatMenu.Item(Icon.PIN, pinned ? "Открепить" : "Закрепить", false, () -> togglePin(message, pinned)));
        }
        if (image != null) {
            items.add(new ChatMenu.Item(Icon.DOWNLOAD, "Сохранить как…", false, () -> media.save(image)));
            items.add(new ChatMenu.Item(Icon.COPY, image.animated() ? "Копировать гифку" : "Копировать картинку", false,
                    () -> media.copy(image)));
            items.add(new ChatMenu.Item(Icon.EXTERNAL_LINK, "Открыть", false, () -> media.open(image)));
            if (image.animated()) {
                boolean favorite = media.isFavorite(image);
                items.add(new ChatMenu.Item(Icon.STAR, favorite ? "Убрать из избранного" : "В избранные гифки", false,
                        () -> media.toggleFavorite(image)));
            }
        } else if (!MessageContent.plain(message.text()).isBlank()) {
            items.add(new ChatMenu.Item(Icon.COPY, "Копировать текст", false, () -> copyText(message)));
        }
        items.add(new ChatMenu.Item(Icon.SHARE, "Переслать", false, () -> host.forward(List.of(message))));
        if (editable) {
            items.add(new ChatMenu.Item(Icon.TRASH, "Удалить", true, () -> delete(List.of(message))));
            items.add(new ChatMenu.Item(Icon.CHECK_SQUARE, "Выделить", false, () -> host.select(message)));
        }
        return items;
    }

    MessageReply replyTo(PrivateMessage message) {
        // в группе цитата подписана автором сообщения, а не названием чата
        String author = message.fromMe() ? Minecraft.getInstance().getUser().getName()
                : message.author() != null ? message.author() : host.partnerName();
        return MessageReply.to(message, author);
    }

    private void copyText(PrivateMessage message) {
        Minecraft.getInstance().keyboardHandler.setClipboard(MessageContent.plain(message.text()));
        info.accept("Текст скопирован");
    }

    // открепить — сразу (у обоих, если можно); закрепить — с вопросом
    private void togglePin(PrivateMessage message, boolean pinned) {
        String partner = host.partner();
        boolean sync = route.canSync(partner, List.of(message));
        if (pinned) {
            route.pin(partner, message, false, sync, warn);
            info.accept("Сообщение откреплено");
            return;
        }
        String check = sync ? "Также закрепить для " + host.partnerName() : null;
        host.confirm(new ConfirmBox.Request("Закрепить это сообщение вверху переписки?", check, false, "Закрепить", false,
                forBoth -> route.pin(partner, message, true, sync && forBoth, warn)));
    }

    // удалить одно или выделенные
    void delete(List<PrivateMessage> targets) {
        if (targets.isEmpty()) {
            return;
        }
        String partner = host.partner();
        boolean sync = route.canSync(partner, targets);
        String question = targets.size() == 1 ? "Удалить это сообщение?" : "Удалить " + count(targets.size()) + "?";
        String text = sync ? question : question + " Удалится только у тебя.";
        String check = sync ? "Также удалить для " + host.partnerName() : null;
        host.confirm(new ConfirmBox.Request(text, check, true, "Удалить", true, forBoth -> {
            route.delete(partner, targets, sync && forBoth, warn);
            host.deleted();
        }));
    }

    // «3 сообщения», «5 сообщений», «21 сообщение»
    static String count(int count) {
        int tens = count % 100;
        int ones = count % 10;
        String word = tens >= 11 && tens <= 14 ? "сообщений" : ones == 1 ? "сообщение" : ones >= 2 && ones <= 4 ? "сообщения" : "сообщений";
        return count + " " + word;
    }
}
