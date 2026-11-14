package dev.no.name.blooddesigner.gui.hub;

import dev.no.name.blooddesigner.gui.animation.Animated;
import dev.no.name.blooddesigner.gui.animation.Motion;

public final class PanelState {
    private final Animated open = new Animated(0, Motion.NORMAL);
    private boolean opened;

    public void toggle() {
        opened = !opened;
        open.setTarget(opened);
    }

    public void open() {
        if (!opened) {
            toggle();
        }
    }

    public void close() {
        if (opened) {
            toggle();
        }
    }

    public boolean isOpen() {
        return opened;
    }

    public float progress() {
        return open.get();
    }
}
