package dev.no.name.afriends.gui.animation;

public final class Motion {
    public static final long FAST = 120;
    public static final long NORMAL = 200;
    public static final long SLOW = 300;
    public static final long STAGGER = 22;
    public static final float RISE = 8;

    private Motion() {
    }

    public static long now() {
        return System.nanoTime() / 1_000_000;
    }

    public static float appear(long start, int index) {
        float progress = (float) (now() - start - index * STAGGER) / NORMAL;
        return Easing.outCubic(progress);
    }
}
