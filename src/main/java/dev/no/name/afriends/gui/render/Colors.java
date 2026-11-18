package dev.no.name.afriends.gui.render;

public final class Colors {
    private Colors() {
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        int a = channel(from >>> 24, to >>> 24, t);
        int r = channel(from >> 16 & 0xFF, to >> 16 & 0xFF, t);
        int g = channel(from >> 8 & 0xFF, to >> 8 & 0xFF, t);
        int b = channel(from & 0xFF, to & 0xFF, t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int fade(int color, float factor) {
        int alpha = Math.round((color >>> 24) * Math.max(0, Math.min(1, factor)));
        return alpha << 24 | color & 0xFFFFFF;
    }

    private static int channel(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }

    public static int fromHsv(float hue, float saturation, float value) {
        float h = (hue % 1 + 1) % 1 * 6;
        int sector = (int) Math.floor(h) % 6;
        float f = h - (float) Math.floor(h);
        float p = value * (1 - saturation);
        float q = value * (1 - saturation * f);
        float t = value * (1 - saturation * (1 - f));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    public static float[] toHsv(int color) {
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8 & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float hue = 0;
        if (delta > 0) {
            if (max == r) {
                hue = ((g - b) / delta) / 6;
            } else if (max == g) {
                hue = ((b - r) / delta + 2) / 6;
            } else {
                hue = ((r - g) / delta + 4) / 6;
            }
            hue = (hue + 1) % 1;
        }
        float saturation = max == 0 ? 0 : delta / max;
        return new float[]{hue, saturation, max};
    }
}
