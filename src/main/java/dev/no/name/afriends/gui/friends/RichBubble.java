package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.gui.render.FormattedText;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageContent;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.text.TextStyle;

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
    private static final float TIME_HEIGHT = 7;
    private static final float TIME_GAP = 5;

    record Part(MessageContent.Block block, List<FormattedText.Line> lines, float width, float height) {
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
                List<FormattedText.Line> lines = FormattedText.layout(text.text(), maxText);
                float widest = FormattedText.widest(lines);
                parts.add(new Part(block, lines, widest, FormattedText.height(lines)));
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
            float lastLine = FormattedText.lastWidth(last.lines());
            inline = lastLine + TIME_GAP + stampWidth <= maxText;
            width = Math.max(width, (inline ? lastLine + TIME_GAP + stampWidth : stampWidth) + PADDING_X * 2);
            height += inline ? 0 : TIME_HEIGHT;
        }
        return new Layout(List.copyOf(parts), width, height, inline);
    }

    // рисует части и возвращает, где картинки
    static List<ImageHit> draw(Gfx g, Layout layout, MessageRoute route, float x, float top, float width,
                               String time, int textColor, int timeColor, PrivateMessage ticksFor) {
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
                    if (ticksFor != null) {
                        MessageBubbles.drawTicks(g, ticksFor, imageX + part.width() - 6, y + part.height() - 10.5f, 0xFFFFFFFF);
                    }
                }
            } else {
                float lineY = FormattedText.draw(g, part.lines(), x + PADDING_X, y, textColor);
                if (i == parts.size() - 1) {
                    float timeX = x + width - PADDING_X - Draw.width(time, TextStyle.SMALL);
                    float timeY = layout.timeInline() ? FormattedText.timeY(part.lines(), lineY) : lineY;
                    Draw.text(g, time, timeX, timeY, timeColor, TextStyle.SMALL);
                    if (ticksFor != null) {
                        MessageBubbles.drawTicks(g, ticksFor, x + width - PADDING_X, timeY, timeColor);
                    }
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
