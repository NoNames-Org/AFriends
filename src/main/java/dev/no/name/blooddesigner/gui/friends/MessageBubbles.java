package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.addon.Addons;
import dev.no.name.blooddesigner.api.MessageCard;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.MessageRoute;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.compat.Gfx;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class MessageBubbles {
    static final int GAP = 3;
    private static final int PADDING_X = 5;
    private static final int PADDING_Y = 3;
    private static final int LINE_HEIGHT = 9;
    private static final int TIME_HEIGHT = 7;

    private static final int TIME_GAP = 5;
    private static final float RADIUS = 4;
    private static final float MAX_WIDTH_SHARE = 0.7f;
    private static final String PENDING = "отправка…";
    private static final String FAILED = "не в сети";
    private static final int DANGER = 0xFFFF6B6B;
    private static final String HIDDEN = "Скрыто";
    private static final int CARD_ICON = 12;
    private static final int CARD_BUTTON = 14;
    private static final float CARD_MIN_WIDTH = 110;
    // строка «Переслано от …» над содержимым пересланного сообщения
    private static final int FORWARD_HEIGHT = 10;

    enum Stamp { TIME, PENDING, FAILED }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    // card — карточка дополнения вместо текста; rich — картинки с текстом по меткам [img]; chips — реакции под ним
    record Bubble(PrivateMessage message, List<String> lines, float width, float height, boolean timeInline,
                  MessageCard.Card card, RichBubble.Layout rich, List<ReactionChips.Chip> chips) {
        Bubble(PrivateMessage message, List<String> lines, float width, float height, boolean timeInline, MessageCard.Card card) {
            this(message, lines, width, height, timeInline, card, null, List.of());
        }

        boolean hasImage() {
            return rich != null;
        }

        // пересланное: сверху строка «Переслано от …», пузырь выше и при надобности шире
        Bubble withForward() {
            if (message.forwardedFrom() == null) {
                return this;
            }
            float labelWidth = Draw.width(forwardLabel(message), TextStyle.SMALL) + PADDING_X * 2;
            return new Bubble(message, lines, Math.max(width, labelWidth), height + FORWARD_HEIGHT, timeInline, card, rich, chips);
        }

        // реакции дописываются строкой снизу: пузырь растёт и при надобности шире
        Bubble withChips(List<ReactionChips.Chip> chips) {
            if (chips.isEmpty()) {
                return this;
            }
            float chipsWidth = ReactionChips.width(chips) + PADDING_X * 2;
            return new Bubble(message, lines, Math.max(width, chipsWidth), height + ReactionChips.ROW, timeInline, card,
                    rich, chips);
        }
    }

    // где на холсте нарисован пузырь: для клика по картинке, реакциям и правого клика по сообщению
    record Drawn(PrivateMessage message, float x, float y, float width, float height, CardButton card,
                 List<RichBubble.ImageHit> images, List<ReactionChips.Hit> chips) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        // картинка под курсором или null
        MessageImage imageAt(double mouseX, double mouseY) {
            for (RichBubble.ImageHit hit : images) {
                if (hit.contains(mouseX, mouseY)) {
                    return hit.image();
                }
            }
            return null;
        }
    }

    // где на холсте кнопка карточки
    record CardButton(float x, float y, float width, float height, MessageCard.Card card) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private MessageBubbles() {
    }

    static List<Bubble> layout(List<PrivateMessage> messages, float areaWidth, int firstPending, String partner) {
        float maxText = areaWidth * MAX_WIDTH_SHARE - PADDING_X * 2;
        // режим стримера: текст сообщений не показываем
        boolean hidden = Addons.get().hideMessages();

        float pendingWidth = Math.max(Draw.width(PENDING, TextStyle.SMALL), Draw.width(FAILED, TextStyle.SMALL));
        List<Bubble> bubbles = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            PrivateMessage message = messages.get(index);
            float stampWidth = index >= firstPending ? pendingWidth : Draw.width(time(message), TextStyle.SMALL);
            List<ReactionChips.Chip> chips = ReactionChips.of(message);
            if (!hidden && !message.images().isEmpty()) {
                RichBubble.Layout rich = RichBubble.layout(message, maxText, stampWidth);
                bubbles.add(new Bubble(message, List.of(), rich.width(), rich.height(), rich.timeInline(), null, rich, List.of())
                        .withForward().withChips(chips));
                continue;
            }
            MessageCard.Card card = hidden ? null : Addons.get().card(partner, message.text(), message.fromMe());
            if (card != null) {
                bubbles.add(cardBubble(message, card, maxText).withForward().withChips(chips));
                continue;
            }
            List<String> lines = wrap(hidden ? HIDDEN : message.text(), maxText);
            float widest = 0;
            for (String line : lines) {
                widest = Math.max(widest, Draw.width(line));
            }
            float lastLine = Draw.width(lines.get(lines.size() - 1));

            boolean inline = lastLine + TIME_GAP + stampWidth <= maxText;
            float contentWidth = inline ? Math.max(widest, lastLine + TIME_GAP + stampWidth) : Math.max(widest, stampWidth);
            float height = PADDING_Y * 2 + lines.size() * LINE_HEIGHT + (inline ? 0 : TIME_HEIGHT);
            bubbles.add(new Bubble(message, lines, contentWidth + PADDING_X * 2, height, inline, null).withForward().withChips(chips));
        }
        return bubbles;
    }

    static float totalHeight(List<Bubble> bubbles) {
        float total = 0;
        for (Bubble bubble : bubbles) {
            total += bubble.height() + GAP;
        }
        return Math.max(0, total - GAP);
    }

    // карточка: значок, заголовок, подпись и, если есть, кнопка
    private static Bubble cardBubble(PrivateMessage message, MessageCard.Card card, float maxText) {
        float textWidth = Math.max(Draw.width(card.title()), Draw.width(card.subtitle(), TextStyle.SMALL));
        if (card.button() != null) {
            textWidth = Math.max(textWidth, Draw.width(card.button(), TextStyle.SMALL) + 12 - CARD_ICON - 5);
        }
        float width = Math.min(maxText, Math.max(CARD_MIN_WIDTH, CARD_ICON + 5 + textWidth)) + PADDING_X * 2;
        float height = PADDING_Y * 2 + LINE_HEIGHT * 2 + (card.button() != null ? CARD_BUTTON + 3 : 0) + TIME_HEIGHT;
        return new Bubble(message, List.of(), width, height, false, card);
    }

    // рисует пузырь и возвращает, где что нарисовано: кнопка карточки, картинка, плашки реакций
    static Drawn draw(Gfx g, Bubble bubble, float left, float right, float top, Stamp stamp, MessageRoute route) {
        boolean mine = bubble.message().fromMe();
        boolean failed = stamp == Stamp.FAILED;
        float x = mine ? right - bubble.width() : left;
        int fill = failed ? Theme.CARD_PRESSED : mine ? Theme.ACCENT : Theme.CARD_SELECTED;
        int textColor = failed ? Theme.TEXT_SOFT : mine ? Theme.ACCENT_TEXT : Theme.TEXT;
        Draw.roundedRect(g, x, top, bubble.width(), bubble.height(), RADIUS, fill);
        float bubbleTop = top;
        if (bubble.message().forwardedFrom() != null) {
            Draw.text(g, Draw.trim(forwardLabel(bubble.message()), (int) bubble.width() - PADDING_X * 2, TextStyle.SMALL),
                    x + PADDING_X, top + PADDING_Y, Colors.fade(textColor, 0.6f), TextStyle.SMALL);
            top += FORWARD_HEIGHT;
        }
        if (bubble.hasImage()) {
            return drawImage(g, bubble, x, top, bubbleTop, stamp, route, textColor);
        }
        CardButton button = bubble.card() == null ? null : drawCard(g, bubble, x, top, textColor);
        float lineY = top + PADDING_Y + (bubble.card() == null ? 0 : LINE_HEIGHT * 2
                + (bubble.card().button() != null ? CARD_BUTTON + 3 : 0));
        for (String line : bubble.lines()) {
            Draw.text(g, line, x + PADDING_X, lineY, textColor);
            lineY += LINE_HEIGHT;
        }
        String time = switch (stamp) {
            case PENDING -> PENDING;
            case FAILED -> FAILED;
            case TIME -> time(bubble.message());
        };
        int timeColor = failed ? DANGER : mine ? withAlpha(Theme.ACCENT_TEXT, 0.75f) : Theme.TEXT_DIM;
        float timeX = x + bubble.width() - PADDING_X - Draw.width(time, TextStyle.SMALL);

        float timeY = bubble.timeInline() ? lineY - LINE_HEIGHT + 1 : lineY;
        Draw.text(g, time, timeX, timeY, timeColor, TextStyle.SMALL);
        List<ReactionChips.Hit> chips = ReactionChips.draw(g, bubble.chips(), x + PADDING_X,
                bubbleTop + bubble.height() - ReactionChips.ROW + 1, textColor);
        return new Drawn(bubble.message(), x, bubbleTop, bubble.width(), bubble.height(), button, List.of(), chips);
    }

    private static String forwardLabel(PrivateMessage message) {
        return "Переслано от " + message.forwardedFrom();
    }

    private static Drawn drawImage(Gfx g, Bubble bubble, float x, float top, float bubbleTop, Stamp stamp, MessageRoute route,
                                   int textColor) {
        String time = switch (stamp) {
            case PENDING -> PENDING;
            case FAILED -> FAILED;
            case TIME -> time(bubble.message());
        };
        boolean mine = bubble.message().fromMe();
        int timeColor = stamp == Stamp.FAILED ? DANGER : mine ? withAlpha(Theme.ACCENT_TEXT, 0.75f) : Theme.TEXT_DIM;
        List<RichBubble.ImageHit> images = RichBubble.draw(g, bubble.rich(), route, x, top, bubble.width(), time, textColor, timeColor);
        List<ReactionChips.Hit> chips = ReactionChips.draw(g, bubble.chips(), x + PADDING_X,
                bubbleTop + bubble.height() - ReactionChips.ROW + 1, textColor);
        return new Drawn(bubble.message(), x, bubbleTop, bubble.width(), bubble.height(), null, images, chips);
    }

    private static CardButton drawCard(Gfx g, Bubble bubble, float x, float top, int textColor) {
        MessageCard.Card card = bubble.card();
        float textX = x + PADDING_X + CARD_ICON + 5;
        int textWidth = (int) (x + bubble.width() - PADDING_X - textX);
        Draw.icon(g, card.icon(), x + PADDING_X, top + PADDING_Y + 3, CARD_ICON, textColor);
        Draw.text(g, Draw.trim(card.title(), textWidth), textX, top + PADDING_Y, textColor);
        Draw.text(g, Draw.trim(card.subtitle(), textWidth, TextStyle.SMALL), textX, top + PADDING_Y + LINE_HEIGHT + 1,
                Colors.fade(textColor, 0.7f), TextStyle.SMALL);
        if (card.button() == null) {
            return null;
        }
        float buttonX = x + PADDING_X;
        float buttonY = top + PADDING_Y + LINE_HEIGHT * 2 + 2;
        float buttonWidth = bubble.width() - PADDING_X * 2;
        Draw.roundedRect(g, buttonX, buttonY, buttonWidth, CARD_BUTTON, RADIUS - 1,
                card.done() ? Colors.fade(textColor, 0.08f) : Colors.fade(textColor, 0.16f));
        String label = Draw.trim(card.button(), (int) buttonWidth - 8, TextStyle.SMALL);
        Draw.text(g, label, buttonX + (buttonWidth - Draw.width(label, TextStyle.SMALL)) / 2f, buttonY + 4,
                card.done() ? Colors.fade(textColor, 0.5f) : textColor, TextStyle.SMALL);
        return card.done() ? null : new CardButton(buttonX, buttonY, buttonWidth, CARD_BUTTON, card);
    }

    // перевод строки начинает новую строку: он бывает в объявлениях с портала
    static List<String> wrap(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            lines.addAll(wrapParagraph(paragraph, maxWidth));
        }
        return lines;
    }

    private static List<String> wrapParagraph(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (Draw.width(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            String rest = word;
            while (Draw.width(rest) > maxWidth && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && Draw.width(rest.substring(0, cut)) > maxWidth) {
                    cut--;
                }
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static String time(PrivateMessage message) {
        return TIME.format(Instant.ofEpochMilli(message.time()).atZone(ZoneId.systemDefault()));
    }

    private static int withAlpha(int color, float alpha) {
        return Math.round(alpha * 255) << 24 | color & 0xFFFFFF;
    }
}
