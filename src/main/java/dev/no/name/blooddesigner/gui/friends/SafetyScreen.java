package dev.no.name.blooddesigner.gui.friends;

import com.mojang.blaze3d.platform.Window;
import dev.no.name.blooddesigner.compat.Compat;
import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.compat.ScreenBase;
import dev.no.name.blooddesigner.core.qr.QrCode;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.animation.Animated;
import dev.no.name.blooddesigner.gui.animation.Motion;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.blur.MenuBlur;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import dev.no.name.blooddesigner.gui.sound.UiSound;
import dev.no.name.blooddesigner.gui.sound.UiSounds;
import dev.no.name.blooddesigner.gui.widget.ButtonWidget;
import dev.no.name.blooddesigner.gui.widget.Widget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Сверка ключей с другом: 60 цифр отпечатка и они же QR-кодом. Друг наводит камеру телефона на QR
 * на твоём экране и сравнивает цифры со своим окном — проще, чем диктовать голосом
 */
public final class SafetyScreen extends ScreenBase {
    private static final int WIDTH = 236;
    private static final int PAD = 14;
    private static final int MODULE = 3;
    private static final int QUIET = 3;
    private static final int GROUPS_PER_ROW = 4;
    private static final int ROW_GAP = 12;
    private static final int BUTTON_HEIGHT = 18;
    private static final int SCREEN_MARGIN = 24;

    private final Screen parent;
    private final String friend;
    private final String number;
    private final QrCode code;
    private final ButtonWidget copy;
    private final ButtonWidget done;
    private final Animated windowOpen = new Animated(0, Motion.SLOW);

    private int pixelScale = 1;
    private float scale = 1;
    private int left;
    private int top;
    private long copiedAt;

    public SafetyScreen(Screen parent, String friend, String number) {
        super(Component.literal("Сверка ключей"));
        this.parent = parent;
        this.friend = friend;
        this.number = number;
        this.code = QrCode.encode(number);
        this.copy = new ButtonWidget(Icon.COPY, "Скопировать цифры", this::copyNumber);
        this.done = new ButtonWidget(Icon.CHECK, "Готово", this::onClose);
        windowOpen.setTarget(1);
        UiSounds.play(UiSound.MENU_OPEN);
    }

    private int qrPixels() {
        return (code.size() + QUIET * 2) * MODULE;
    }

    private int height() {
        int rows = (number.split(" ").length + GROUPS_PER_ROW - 1) / GROUPS_PER_ROW;
        return PAD + 26 + qrPixels() + 10 + rows * ROW_GAP + 8 + 28 + BUTTON_HEIGHT + PAD;
    }

    @Override
    protected void init() {
        Window window = minecraft.getWindow();
        int fitX = window.getWidth() / (WIDTH + SCREEN_MARGIN);
        int fitY = window.getHeight() / (height() + SCREEN_MARGIN);
        pixelScale = Math.max(1, Math.min(fitX, fitY));
        scale = pixelScale / (float) window.getGuiScale();
        left = (window.getWidth() / pixelScale - WIDTH) / 2;
        top = (window.getHeight() / pixelScale - height()) / 2;
        int buttonsY = top + height() - PAD - BUTTON_HEIGHT;
        int half = (WIDTH - PAD * 2 - 6) / 2;
        copy.setBounds(left + PAD, buttonsY, half, BUTTON_HEIGHT);
        done.setBounds(left + PAD + half + 6, buttonsY, half, BUTTON_HEIGHT);
    }

    @Override
    protected void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick) {
        float shown = windowOpen.get();
        MenuBlur.request(shown);
        g.blurBeforeThisStratum();
        Draw.pushAlpha(shown);
        Draw.backdrop(g, width, height, Theme.DIM, Theme.DIM_VIGNETTE, Theme.DIM_GRAIN);
        Draw.popAlpha();
    }

    @Override
    protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);
        Draw.setPixelScale(pixelScale);
        int x = (int) (mouseX / scale);
        int y = (int) (mouseY / scale);
        float shown = windowOpen.get();
        Draw.pushAlpha(shown);
        Draw.pushOffset(g, 0, (1 - shown) * Motion.RISE);
        int panelHeight = height();
        Draw.shadow(g, left, top + 2, WIDTH, panelHeight, Theme.PANEL_RADIUS, 12, Colors.fade(Theme.SHADOW, 0.7f));
        Draw.gradient(g, left, top, WIDTH, panelHeight, Theme.PANEL_RADIUS, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
        Draw.border(g, left, top, WIDTH, panelHeight, Theme.PANEL_RADIUS, Theme.BORDER_HOVER);

        Draw.icon(g, Icon.SHIELD_CHECK, left + PAD, top + PAD + 1, Theme.ICON, Theme.ACCENT);
        Draw.text(g, Draw.trim("Сверка ключей с " + friend, WIDTH - PAD * 2 - 16, TextStyle.TITLE), left + PAD + 16, top + PAD,
                Theme.TEXT, TextStyle.TITLE);
        Draw.text(g, "Цифры одинаковые у вас обоих", left + PAD + 16, top + PAD + 12, Theme.TEXT_DIM, TextStyle.SMALL);

        int qrTop = top + PAD + 26;
        drawQr(g, left + (WIDTH - qrPixels()) / 2, qrTop);
        int digitsTop = qrTop + qrPixels() + 10;
        drawDigits(g, digitsTop);

        int rows = (number.split(" ").length + GROUPS_PER_ROW - 1) / GROUPS_PER_ROW;
        int hintTop = digitsTop + rows * ROW_GAP + 8;
        Draw.text(g, "Друг наводит камеру телефона на QR и сравнивает", left + PAD, hintTop, Theme.TEXT_SOFT, TextStyle.SMALL);
        Draw.text(g, "цифры со своим экраном. Совпали — переписку не", left + PAD, hintTop + 9, Theme.TEXT_SOFT, TextStyle.SMALL);
        Draw.text(g, "читает никто, даже сервер сети.", left + PAD, hintTop + 18, Theme.TEXT_SOFT, TextStyle.SMALL);

        copy.render(g, x, y);
        done.render(g, x, y);
        if (copiedAt > 0 && System.currentTimeMillis() - copiedAt < 1500) {
            Draw.textCentered(g, "Скопировано", left + WIDTH / 2f, copy.getY() - 10, Theme.TEXT_DIM);
        }
        Draw.popOffset(g);
        Draw.popAlpha();
        pose.popMatrix();
    }

    // белая подложка с тихой зоной: сканеры телефонов читают тёмное на светлом
    private void drawQr(Gfx g, int x, int y) {
        Draw.roundedRect(g, x, y, qrPixels(), qrPixels(), 3, 0xFFFFFFFF);
        int origin = QUIET * MODULE;
        for (int row = 0; row < code.size(); row++) {
            for (int column = 0; column < code.size(); column++) {
                if (code.dark(column, row)) {
                    Draw.rect(g, x + origin + column * MODULE, y + origin + row * MODULE, MODULE, MODULE, 0xFF000000);
                }
            }
        }
    }

    private void drawDigits(Gfx g, int y) {
        String[] groups = number.split(" ");
        float cell = (WIDTH - PAD * 2) / (float) GROUPS_PER_ROW;
        for (int i = 0; i < groups.length; i++) {
            float centerX = left + PAD + cell * (i % GROUPS_PER_ROW) + cell / 2;
            Draw.textCentered(g, groups[i], centerX, y + (i / GROUPS_PER_ROW) * ROW_GAP, Theme.TEXT);
        }
    }

    private void copyNumber() {
        minecraft.keyboardHandler.setClipboard(number);
        copiedAt = System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() / scale;
        double y = event.y() / scale;
        return copy.mouseClicked(x, y, Widget.LEFT_BUTTON) || done.mouseClicked(x, y, Widget.LEFT_BUTTON)
                || super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        Compat.setScreen(parent);
    }

    @Override
    public void removed() {
        MenuBlur.cancel();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
