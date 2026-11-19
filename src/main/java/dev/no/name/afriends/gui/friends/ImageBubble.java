package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.gui.media.ChatMedia;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.media.ImageTextures;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import net.minecraft.resources.Identifier;

/** Картинка или гифка внутри пузыря: размер по пропорциям, пока грузится — заглушка, не открылась — пометка */
final class ImageBubble {
    static final float MAX_WIDTH = 150;
    static final float MAX_HEIGHT = 130;
    private static final float MIN_SIDE = 40;

    private ImageBubble() {
    }

    // размер на холсте чата по пропорциям картинки
    static float[] size(MessageImage image, float maxWidth) {
        float width = Math.min(maxWidth, MAX_WIDTH);
        float scale = Math.min(width / image.width(), MAX_HEIGHT / image.height());
        float w = Math.max(MIN_SIDE, image.width() * scale);
        float h = Math.max(MIN_SIDE, image.height() * scale);
        return new float[] {Math.round(w), Math.round(h)};
    }

    static void draw(Gfx g, MessageImage image, ChatMedia media, float x, float y, float width, float height, long now) {
        ImageTextures.Entry entry = media.image(image);
        Identifier texture = entry.state() == ImageTextures.State.READY ? entry.texture(now) : null;
        if (texture != null) {
            g.image(texture, Math.round(x), Math.round(y), Math.round(width), Math.round(height), entry.width(), entry.height());
            if (image.animated()) {
                drawBadge(g, "GIF", x + 3, y + 3);
            }
            return;
        }
        Draw.roundedRect(g, x, y, width, height, Theme.RADIUS - 1, Theme.TILE);
        boolean failed = entry.state() == ImageTextures.State.FAILED;
        Icon icon = failed ? Icon.ERROR : Icon.IMAGE;
        String label = failed ? "не загрузилась" : "загрузка…";
        float iconSize = 12;
        Draw.icon(g, icon, x + (width - iconSize) / 2, y + height / 2 - iconSize, iconSize, Theme.TEXT_DIM);
        Draw.text(g, label, x + (width - Draw.width(label, TextStyle.SMALL)) / 2, y + height / 2 + 3, Theme.TEXT_DIM, TextStyle.SMALL);
    }

    // время поверх картинки — на тёмной плашке, как в Telegram
    static void drawTime(Gfx g, String time, float right, float bottom) {
        float width = Draw.width(time, TextStyle.SMALL) + 6;
        Draw.roundedRect(g, right - width - 3, bottom - 12, width, 9, 4.5f, 0x99000000);
        Draw.text(g, time, right - width, bottom - 10.5f, 0xFFFFFFFF, TextStyle.SMALL);
    }

    private static void drawBadge(Gfx g, String text, float x, float y) {
        float width = Draw.width(text, TextStyle.SMALL) + 6;
        Draw.roundedRect(g, x, y, width, 9, 4.5f, 0x99000000);
        Draw.text(g, text, x + 3, y + 1.5f, 0xFFFFFFFF, TextStyle.SMALL);
    }
}
