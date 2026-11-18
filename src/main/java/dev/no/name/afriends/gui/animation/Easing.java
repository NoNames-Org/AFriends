package dev.no.name.afriends.gui.animation;

public final class Easing {
    private Easing() {
    }

    public static float outCubic(float progress) {
        float t = 1 - clamp(progress);
        return 1 - t * t * t;
    }

    public static float pulse(long time, long period) {
        double phase = (time % period) / (double) period;
        return (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2));
    }

    public static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }
}
