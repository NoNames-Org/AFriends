package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.core.PrivateMessage;
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

    enum Stamp { TIME, PENDING, FAILED }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    record Bubble(PrivateMessage message, List<String> lines, float width, float height, boolean timeInline) {
    }

    private MessageBubbles() {
    }

    static List<Bubble> layout(List<PrivateMessage> messages, float areaWidth, int firstPending) {
        float maxText = areaWidth * MAX_WIDTH_SHARE - PADDING_X * 2;

        float pendingWidth = Math.max(Draw.width(PENDING, TextStyle.SMALL), Draw.width(FAILED, TextStyle.SMALL));
        List<Bubble> bubbles = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            PrivateMessage message = messages.get(index);
            float stampWidth = index >= firstPending ? pendingWidth : Draw.width(time(message), TextStyle.SMALL);
            List<String> lines = wrap(message.text(), maxText);
            float widest = 0;
            for (String line : lines) {
                widest = Math.max(widest, Draw.width(line));
            }
            float lastLine = Draw.width(lines.get(lines.size() - 1));

            boolean inline = lastLine + TIME_GAP + stampWidth <= maxText;
            float contentWidth = inline ? Math.max(widest, lastLine + TIME_GAP + stampWidth) : Math.max(widest, stampWidth);
            float height = PADDING_Y * 2 + lines.size() * LINE_HEIGHT + (inline ? 0 : TIME_HEIGHT);
            bubbles.add(new Bubble(message, lines, contentWidth + PADDING_X * 2, height, inline));
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

    static void draw(Gfx g, Bubble bubble, float left, float right, float top, Stamp stamp) {
        boolean mine = bubble.message().fromMe();
        boolean failed = stamp == Stamp.FAILED;
        float x = mine ? right - bubble.width() : left;
        int fill = failed ? Theme.CARD_PRESSED : mine ? Theme.ACCENT : Theme.CARD_SELECTED;
        int textColor = failed ? Theme.TEXT_SOFT : mine ? Theme.ACCENT_TEXT : Theme.TEXT;
        Draw.roundedRect(g, x, top, bubble.width(), bubble.height(), RADIUS, fill);
        float lineY = top + PADDING_Y;
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
    }

    static List<String> wrap(String text, float maxWidth) {
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
