package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.core.MessageContent;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.MessageRoute;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Пузырь с картинками: картинки и куски текста друг под другом в порядке меток [img] (MessageContent).
 * Время — поверх последней картинки или после последней строки, как в Telegram
 */
final class RichBubble {
    private static final float PADDING_X = 5;
    private static final float PADDING_Y = 3;
    private static final float IMAGE_PADDING = 2;
    private static final float PART_GAP = 3;
    private static final float LINE_HEIGHT = 9;
    private static final float TIME_HEIGHT = 7;
    private static final float TIME_GAP = 5;

    record Part(MessageContent.Block block, List<String> lines, float width, float height) {
        boolean isImage() {
            return block instanceof MessageContent.Image;
        }
    }

    record Layout(List<Part> parts, float width, float height, boolean timeInline) {
    }

    // где на холсте картинка: клик открывает её, правый клик — меню этой картинки
    record ImageHit(MessageImage image, float x, float y, float width, float height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private RichBubble() {
    }

    static Layout layout(PrivateMessage message, float maxText, float stampWidth) {
        List<Part> parts = new ArrayList<>();
        float width = 0;
        for (MessageContent.Block block : MessageContent.blocks(message.text(), message.images())) {
            if (block instanceof MessageContent.Image image) {
                float[] size = ImageBubble.size(image.image(), maxText);
                parts.add(new Part(block, List.of(), size[0], size[1]));
                width = Math.max(width, size[0] + IMAGE_PADDING * 2);
            } else if (block instanceof MessageContent.Text text) {
                List<String> lines = MessageBubbles.wrap(text.text(), maxText);
                float widest = 0;
                for (String line : lines) {
                    widest = Math.max(widest, Draw.width(line));
                }
                parts.add(new Part(block, lines, widest, lines.size() * LINE_HEIGHT));
                width = Math.max(width, widest + PADDING_X * 2);
            }
        }
        Part last = parts.get(parts.size() - 1);
        boolean inline = false;
        float height = 0;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            height += part.height() + (i == 0 ? edge(part) : PART_GAP);
        }
        height += edge(last);
        if (!last.isImage()) {
            float lastLine = Draw.width(last.lines().get(last.lines().size() - 1));
            inline = lastLine + TIME_GAP + stampWidth <= maxText;
            width = Math.max(width, (inline ? lastLine + TIME_GAP + stampWidth : stampWidth) + PADDING_X * 2);
            height += inline ? 0 : TIME_HEIGHT;
        }
        return new Layout(List.copyOf(parts), width, height, inline);
    }

    // рисует части и возвращает, где картинки
    static List<ImageHit> draw(Gfx g, Layout layout, MessageRoute route, float x, float top, float width,
                               String time, int textColor, int timeColor) {
        List<ImageHit> hits = new ArrayList<>();
        float y = top;
        long now = System.currentTimeMillis();
        List<Part> parts = layout.parts();
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            y += i == 0 ? edge(part) : PART_GAP;
            if (part.block() instanceof MessageContent.Image image) {
                float imageX = x + (width - part.width()) / 2;
                ImageBubble.draw(g, image.image(), route, imageX, y, part.width(), part.height(), now);
                hits.add(new ImageHit(image.image(), imageX, y, part.width(), part.height()));
                if (i == parts.size() - 1) {
                    ImageBubble.drawTime(g, time, imageX + part.width(), y + part.height());
                }
            } else {
                float lineY = y;
                for (String line : part.lines()) {
                    Draw.text(g, line, x + PADDING_X, lineY, textColor);
                    lineY += LINE_HEIGHT;
                }
                if (i == parts.size() - 1) {
                    float timeX = x + width - PADDING_X - Draw.width(time, TextStyle.SMALL);
                    Draw.text(g, time, timeX, layout.timeInline() ? lineY - LINE_HEIGHT + 1 : lineY, timeColor, TextStyle.SMALL);
                }
            }
            y += part.height();
        }
        return hits;
    }

    // отступ от края пузыря: у картинки тонкая рамка, у текста — обычный
    private static float edge(Part part) {
        return part.isImage() ? IMAGE_PADDING : PADDING_Y;
    }
}
