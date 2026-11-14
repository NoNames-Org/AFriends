package dev.no.name.blooddesigner.gui.animation;

public class Animated {
    private final long duration;
    private float from;
    private float target;
    private long startTime;

    public Animated(float initial, long duration) {
        this.duration = duration;
        this.from = initial;
        this.target = initial;
    }

    public void setTarget(boolean on) {
        setTarget(on ? 1 : 0);
    }

    public void setTarget(float target) {
        setTarget(target, 0);
    }

    public void setTarget(float target, long delay) {
        if (target == this.target) {
            return;
        }

        this.from = get();
        this.target = target;
        this.startTime = Motion.now() + delay;
    }

    public void snap(float value) {
        this.from = value;
        this.target = value;
    }

    public float get() {
        float progress = (float) (Motion.now() - startTime) / duration;
        return from + (target - from) * Easing.outCubic(progress);
    }
}
