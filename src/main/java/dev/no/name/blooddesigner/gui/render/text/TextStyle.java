package dev.no.name.blooddesigner.gui.render.text;

import dev.no.name.blooddesigner.BloodDesigner;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public enum TextStyle {
    SMALL("small", 6.0f),
    BODY("body", 7.0f),
    BODY_MEDIUM("body_medium", 7.0f),
    TITLE("title", 10.0f);

    public static final int MAX_PIXEL_SCALE = 8;

    private static final float CAP_HEIGHT_RATIO = 0.727f;

    private final String fontName;
    private final float size;
    private final Style[] styles = new Style[MAX_PIXEL_SCALE + 1];

    TextStyle(String fontName, float size) {
        this.fontName = fontName;
        this.size = size;
    }

    public Style getStyle(int pixelScale) {
        int scale = Math.max(1, Math.min(MAX_PIXEL_SCALE, pixelScale));
        if (styles[scale] == null) {
            Identifier id = Identifier.fromNamespaceAndPath(BloodDesigner.MOD_ID, fontName + "_" + scale);
            styles[scale] = Style.EMPTY.withFont(new FontDescription.Resource(id));
        }
        return styles[scale];
    }

    public float getCapHeight() {
        return size * CAP_HEIGHT_RATIO;
    }
}
