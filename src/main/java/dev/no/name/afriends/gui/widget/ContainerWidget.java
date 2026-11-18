package dev.no.name.afriends.gui.widget;

import dev.no.name.afriends.compat.Gfx;

import java.util.ArrayList;
import java.util.List;

public class ContainerWidget extends Widget {
    protected final List<Widget> children = new ArrayList<>();

    protected <T extends Widget> T add(T child) {
        children.add(child);
        return child;
    }

    public void addChild(Widget child) {
        children.add(child);
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY) {
        for (Widget child : children) {
            child.render(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = false;

        for (Widget child : children) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                handled = true;
            }
        }
        return handled;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (Widget child : children) {
            child.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY, int button) {
        for (Widget child : children) {
            child.mouseDragged(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        for (Widget child : children) {
            if (child.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        for (Widget child : children) {
            if (child.keyPressed(keyCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(String text) {
        for (Widget child : children) {
            if (child.charTyped(text)) {
                return true;
            }
        }
        return false;
    }
}
