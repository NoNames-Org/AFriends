package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.animation.Easing;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.text.TextStyle;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static dev.no.name.afriends.gui.friends.MessengerLayout.CHAT_WIDTH;
import static dev.no.name.afriends.gui.friends.MessengerLayout.CHAT_X;
import static dev.no.name.afriends.gui.friends.MessengerLayout.SEPARATOR_HEIGHT;

/** Строки ленты: пузыри с разделителями дней («Сегодня», «Вчера», «3 марта») и анимация появления пузыря. */
final class FeedRows {
    private static final long APPEAR_MS = 260;
    private static final float PENDING_ALPHA = 0.6f;
    private static final float SLIDE_Y = 10;
    private static final float SLIDE_X = 8;
    private static final String[] MONTHS = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };

    /** Разделитель дня (day не null) или пузырь сообщения с индексом в показанном списке. */
    record Row(String day, MessageBubbles.Bubble bubble, int index, float height) {
    }

    private FeedRows() {
    }

    static List<Row> build(BubbleContext ctx, List<PrivateMessage> shown, float areaWidth, int firstPending, String partner) {
        List<MessageBubbles.Bubble> bubbles = MessageBubbles.layout(ctx, shown, areaWidth, firstPending, partner);
        List<Row> rows = new ArrayList<>();
        LocalDate previous = null;
        for (int i = 0; i < bubbles.size(); i++) {
            LocalDate day = Instant.ofEpochMilli(shown.get(i).time()).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!day.equals(previous)) {
                rows.add(new Row(dayLabel(day), null, -1, SEPARATOR_HEIGHT));
                previous = day;
            }
            rows.add(new Row(null, bubbles.get(i), i, bubbles.get(i).height() + MessageBubbles.GAP));
        }
        return rows;
    }

    static float height(List<Row> rows) {
        float content = 0;
        for (Row row : rows) {
            content += row.height();
        }
        return content;
    }

    static String dayLabel(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.equals(today)) {
            return "Сегодня";
        }
        if (day.equals(today.minusDays(1))) {
            return "Вчера";
        }
        String label = day.getDayOfMonth() + " " + MONTHS[day.getMonthValue() - 1];
        return day.getYear() == today.getYear() ? label : label + " " + day.getYear();
    }

    static void drawDay(Gfx g, String label, float y) {
        float width = Draw.width(label, TextStyle.SMALL) + 12;
        float x = CHAT_X + (CHAT_WIDTH - width) / 2f;
        Draw.box(g, x, y + 3, width, 11, 5.5f, Theme.CARD, Theme.BORDER);
        Draw.text(g, label, x + 6, y + 5.5f, Theme.TEXT_SOFT, TextStyle.SMALL);
    }

    // пузырь въезжает и проявляется; ожидающий — полупрозрачный и гаснет к концу таймаута
    static MessageBubbles.Drawn drawAnimated(Gfx g, MessageBubbles.Bubble bubble, float left, float right,
                                             float y, long start, MessageBubbles.Stamp stamp, long now,
                                             BubbleContext ctx) {
        float progress = start == 0 ? 1 : Math.min(1, (now - start) / (float) APPEAR_MS);
        float eased = Easing.outCubic(progress);
        float alpha = eased * (stamp == MessageBubbles.Stamp.TIME ? 1 : PENDING_ALPHA);
        if (stamp == MessageBubbles.Stamp.PENDING) {
            float age = (now - start) / (float) ChatController.PENDING_TIMEOUT_MS;
            alpha *= age > 0.8f ? (1 - age) / 0.2f : 1;
        }
        float slideX = bubble.message().fromMe() ? (1 - eased) * SLIDE_X : 0;
        Draw.pushAlpha(alpha);
        Draw.pushOffset(g, slideX, (1 - eased) * SLIDE_Y);
        MessageBubbles.Drawn drawnBubble = MessageBubbles.draw(g, bubble, left, right, y, stamp, ctx);
        Draw.popOffset(g);
        Draw.popAlpha();
        return drawnBubble;
    }
}
