package dev.no.name.blooddesigner.gui.render;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

public final class Draw {
    private static int pixelScale = 1;

    private static final float[] alphaStack = new float[16];
    private static int alphaDepth;
    private static float alpha = 1;

    private Draw() {
    }

    public static void setPixelScale(int scale) {
        pixelScale = Math.max(1, scale);
    }

    public static void pushAlpha(float factor) {
        alphaStack[alphaDepth++] = alpha;
        alpha *= Math.max(0, Math.min(1, factor));
    }

    public static void popAlpha() {
        alpha = alphaStack[--alphaDepth];
    }

    public static void pushOffset(Gfx g, float dx, float dy) {
        g.pose().pushMatrix();
        g.pose().translate(dx, dy);
    }

    public static void popOffset(Gfx g) {
        g.pose().popMatrix();
    }

    public static void rect(Gfx g, float x, float y, float width, float height, int color) {
        shape(g, x, y, width, height, 0, 0, 0, color, color, false);
    }

    public static void roundedRect(Gfx g, float x, float y, float width, float height, float radius, int color) {
        shape(g, x, y, width, height, radius, 0, 0, color, color, false);
    }

    public static void gradient(Gfx g, float x, float y, float width, float height, float radius, int top, int bottom) {
        shape(g, x, y, width, height, radius, 0, 0, top, bottom, false);
    }

    public static void gradientHorizontal(Gfx g, float x, float y, float width, float height, float radius, int left, int right) {
        shape(g, x, y, width, height, radius, 0, 0, left, right, true);
    }

    public static void border(Gfx g, float x, float y, float width, float height, float radius, int color) {
        shape(g, x, y, width, height, radius, 1f / pixelScale, 0, color, color, false);
    }

    public static void box(Gfx g, float x, float y, float width, float height, float radius, int fill, int border) {
        roundedRect(g, x, y, width, height, radius, fill);
        border(g, x, y, width, height, radius, border);
    }

    public static void shadow(Gfx g, float x, float y, float width, float height, float radius, float blur, int color) {
        shape(g, x - blur, y - blur, width + blur * 2, height + blur * 2, radius, 0, blur, color, color, false);
    }

    private static void shape(Gfx g, float x, float y, float width, float height,
                              float radius, float stroke, float softness, int startColor, int endColor, boolean horizontal) {
        if (width <= 0 || height <= 0 || alpha <= 0) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle scissor = g.scissor();
        g.shape(pose, x, y, width, height, radius, stroke, softness,
                Colors.fade(startColor, alpha), Colors.fade(endColor, alpha), horizontal,
                scissor, bounds(pose, scissor, x, y, width, height));
    }

    public static void backdrop(Gfx g, float width, float height, int color, float vignette, float grain) {
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle scissor = g.scissor();
        g.backdrop(pose, width, height, Colors.fade(color, alpha), vignette * alpha, grain * alpha,
                scissor, bounds(pose, scissor, 0, 0, width, height));
    }

    private static ScreenRectangle bounds(Matrix3x2f pose, ScreenRectangle scissor, float x, float y, float width, float height) {
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(width) + 1, (int) Math.ceil(height) + 1)
                .transformMaxBounds(pose);
        return scissor != null ? scissor.intersection(bounds) : bounds;
    }

    public static void icon(Gfx g, Icon icon, float x, float y, float size, int color) {
        icon(g, icon, x, y, size, color, 0);
    }

    public static void icon(Gfx g, Icon icon, float x, float y, float size, int color, float glow) {
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle scissor = g.scissor();
        float margin = Icon.margin(size);

        g.icon(pose, icon, snap(x), snap(y), size, Colors.fade(color, alpha), glow, scissor,
                bounds(pose, scissor, x - margin, y - margin, size + margin * 2, size + margin * 2));
    }

    public static void text(Gfx g, String text, float x, float y, int color) {
        text(g, text, x, y, color, TextStyle.BODY);
    }

    public static void text(Gfx g, String text, float x, float y, int color, TextStyle style) {
        int faded = Colors.fade(color, alpha);

        if ((faded >>> 24) < 4) {
            return;
        }

        float drawY = y - (7 - style.getCapHeight()) / 2;

        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(snap(x), snap(drawY));
        g.text(font(), styled(text, style), 0, 0, faded, false);
        pose.popMatrix();
    }

    public static void textCentered(Gfx g, String text, float centerX, float y, int color) {
        text(g, text, centerX - width(text, TextStyle.BODY) / 2f, y, color, TextStyle.BODY);
    }

    public static int width(String text) {
        return width(text, TextStyle.BODY);
    }

    public static int width(String text, TextStyle style) {
        return font().width(styled(text, style));
    }

    public static String trim(String text, int maxWidth) {
        return trim(text, maxWidth, TextStyle.BODY);
    }

    // обрезка по ширине того шрифта, которым текст будет нарисован
    public static String trim(String text, int maxWidth, TextStyle style) {
        if (width(text, style) <= maxWidth) {
            return text;
        }
        String result = text;
        while (!result.isEmpty() && width(result + "...", style) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private static Component styled(String text, TextStyle style) {
        return Component.literal(text).withStyle(style.getStyle(pixelScale));
    }

    private static float snap(float value) {
        return (float) Math.round(value * pixelScale) / pixelScale;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
