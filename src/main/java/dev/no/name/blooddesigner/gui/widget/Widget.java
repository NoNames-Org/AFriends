package dev.no.name.blooddesigner.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.blooddesigner.compat.Gfx;

public abstract class Widget {
    public static final int LEFT_BUTTON = InputConstants.MOUSE_BUTTON_LEFT;

    protected int x;
    protected int y;
    protected int width;
    protected int height;

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public abstract void render(Gfx g, int mouseX, int mouseY);

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
    }

    public void mouseDragged(double mouseX, double mouseY, int button) {
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode) {
        return false;
    }

    public boolean charTyped(String text) {
        return false;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
