package dev.no.name.blooddesigner.gui.render.blur;

import dev.no.name.blooddesigner.BloodDesigner;
import net.minecraft.resources.Identifier;

public final class MenuBlur {
    public static final int LEVELS = 10;

    private static final Identifier[] CHAINS = new Identifier[LEVELS + 1];
    private static final float NONE = -1;

    private static float requested = NONE;

    static {
        for (int level = 1; level <= LEVELS; level++) {
            CHAINS[level] = Identifier.fromNamespaceAndPath(BloodDesigner.MOD_ID, "menu_blur_" + level);
        }
    }

    private MenuBlur() {
    }

    public static void request(float strength) {
        requested = Math.max(0, Math.min(1, strength));
    }

    public static void cancel() {
        requested = NONE;
    }

    public static boolean hasRequest() {
        return requested != NONE;
    }

    public static Identifier consumeChain() {
        int level = Math.round(requested * LEVELS);
        requested = NONE;
        return level > 0 ? CHAINS[level] : null;
    }
}
