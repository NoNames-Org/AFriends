package dev.no.name.blooddesigner.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.blooddesigner.gui.animation.Animated;
import dev.no.name.blooddesigner.gui.animation.Easing;
import dev.no.name.blooddesigner.gui.animation.Motion;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.sound.UiSound;
import dev.no.name.blooddesigner.gui.sound.UiSounds;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.compat.Compat;
import net.minecraft.client.Minecraft;
import dev.no.name.blooddesigner.compat.Gfx;

public class TextFieldWidget extends Widget {
    private static final int PADDING = 6;
    private static final long CARET_PERIOD = 1100;

    private final Icon icon;
    private final String placeholder;
    private final Animated focus = new Animated(0, Motion.NORMAL);
    private final Animated hover = new Animated(0, Motion.FAST);
    private String text = "";
    private int maxLength = Integer.MAX_VALUE;

    private boolean allSelected;
    private boolean focused;
    // без рамки: поле живёт внутри общей полосы ввода (у переписки), рамку рисует она
    private boolean framed = true;

    public TextFieldWidget(Icon icon, String placeholder) {
        this.icon = icon;
        this.placeholder = placeholder;
    }

    public TextFieldWidget withMaxLength(int length) {
        this.maxLength = length;
        return this;
    }

    public TextFieldWidget withoutFrame() {
        this.framed = false;
        return this;
    }

    public void setText(String value) {
        text = value.length() > maxLength ? value.substring(0, maxLength) : value;
        allSelected = false;
    }

    public String getText() {
        return text;
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY) {
        focus.setTarget(focused);
        hover.setTarget(contains(mouseX, mouseY));
        float focusAmount = focus.get();

        if (framed) {
            int border = Colors.mix(Colors.mix(Theme.BORDER, Theme.BORDER_HOVER, hover.get()), Theme.BORDER_SELECTED, focusAmount);
            Draw.box(g, x, y, width, height, Theme.RADIUS, Colors.mix(Theme.CARD, Theme.CARD_HOVER, focusAmount), border);
        }
        if (icon != null) {
            Draw.icon(g, icon, x + PADDING - 1, y + (height - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL,
                    Colors.mix(Theme.TEXT_FAINT, Theme.TEXT, focusAmount));
        }

        int textX = x + (framed ? PADDING : 0) + (icon != null ? Theme.ICON_SMALL + 3 : 0);
        int textY = y + (height - 8) / 2;
        String shown = Draw.trim(text, x + width - PADDING - textX - 2);
        if (text.isEmpty()) {
            Draw.text(g, placeholder, textX, textY, Colors.fade(Theme.TEXT_FAINT, 1 - 0.5f * focusAmount));
        } else {
            if (allSelected && focused) {
                Draw.roundedRect(g, textX - 1, y + 3, Draw.width(shown) + 2, height - 6, 1.5f, Colors.fade(Theme.ACCENT, 0.35f));
            }
            Draw.text(g, shown, textX, textY, Theme.TEXT);
        }

        float caretX = textX + (text.isEmpty() ? 0 : Draw.width(shown) + 1);
        float caretAlpha = focusAmount * (0.25f + 0.75f * Easing.pulse(Motion.now(), CARET_PERIOD));
        Draw.rect(g, caretX, y + 4, 0.75f, height - 8, Colors.fade(Theme.TEXT, caretAlpha));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean wasFocused = focused;
        setFocused(contains(mouseX, mouseY));
        if (focused && !wasFocused) {
            UiSounds.play(UiSound.CLICK);
        }
        return focused;
    }

    public void focus() {
        setFocused(true);
    }

    public void blur() {
        setFocused(false);
    }

    public boolean isFocused() {
        return focused;
    }

    public void refocus() {
        if (focused) {
            Compat.textInputFocus(this, true);
        }
    }

    private void setFocused(boolean value) {
        if (focused == value) {
            return;
        }
        focused = value;
        Compat.textInputFocus(this, value);
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (!focused) {
            return false;
        }
        if (isControlDown() && handleShortcut(keyCode)) {
            return true;
        }
        if (keyCode == InputConstants.KEY_BACKSPACE && !text.isEmpty()) {
            text = allSelected ? "" : text.substring(0, text.length() - 1);
            allSelected = false;
            UiSounds.play(UiSound.DELETE, 0.85f);
        } else if (keyCode == InputConstants.KEY_DELETE && allSelected) {
            text = "";
            allSelected = false;
            UiSounds.play(UiSound.DELETE, 0.85f);
        } else if (isCaretMove(keyCode)) {
            allSelected = false;
        }
        // буквы сюда тоже приходят, но выделение не снимаем: следом придёт charTyped и заменит выделенное символом

        return keyCode != InputConstants.KEY_ESCAPE;
    }

    /** Клавиши, которые в обычном поле снимают выделение и двигают курсор. */
    private static boolean isCaretMove(int keyCode) {
        return keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT
                || keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_DOWN
                || keyCode == InputConstants.KEY_HOME || keyCode == InputConstants.KEY_END;
    }

    @Override
    public boolean charTyped(String typed) {
        if (!focused) {
            return false;
        }
        if (allSelected) {
            text = "";
            allSelected = false;
        }
        if (text.length() + typed.length() > maxLength) {
            return true;
        }
        text += typed;
        UiSounds.playVaried(UiSound.TYPE, 0.08f);
        return true;
    }

    private boolean handleShortcut(int keyCode) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (keyCode) {
            case InputConstants.KEY_A -> allSelected = !text.isEmpty();
            case InputConstants.KEY_C -> {
                if (!text.isEmpty()) {
                    minecraft.keyboardHandler.setClipboard(text);
                }
            }
            case InputConstants.KEY_X -> {
                if (!text.isEmpty()) {
                    minecraft.keyboardHandler.setClipboard(text);
                    text = "";
                    allSelected = false;
                    UiSounds.play(UiSound.DELETE, 0.85f);
                }
            }
            case InputConstants.KEY_V -> paste(minecraft.keyboardHandler.getClipboard());
            default -> {
                return false;
            }
        }
        return true;
    }

    private void paste(String clipboard) {
        if (clipboard == null || clipboard.isEmpty()) {
            return;
        }
        String base = allSelected ? "" : text;
        allSelected = false;
        String cleaned = clipboard.replaceAll("[\r\n\t]+", " ");
        int room = Math.max(0, maxLength - base.length());
        text = base + (cleaned.length() > room ? cleaned.substring(0, room) : cleaned);
        UiSounds.playVaried(UiSound.TYPE, 0.08f);
    }

    private static boolean isControlDown() {
        return Compat.isKeyDown(InputConstants.KEY_LCONTROL) || Compat.isKeyDown(InputConstants.KEY_RCONTROL);
    }
}
