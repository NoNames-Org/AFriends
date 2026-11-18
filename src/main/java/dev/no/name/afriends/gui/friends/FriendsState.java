package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.core.Friend;
import dev.no.name.afriends.gui.animation.Animated;
import dev.no.name.afriends.gui.animation.Motion;

public final class FriendsState {
    private final Animated open = new Animated(0, Motion.NORMAL);
    private final Animated menuOpen = new Animated(0, Motion.FAST);
    private boolean opened;
    private Friend menuFriend;
    private int menuAnchorY;

    public void toggle() {
        opened = !opened;
        open.setTarget(opened);
        if (!opened) {
            closeMenu();
        }
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

    public void openMenu(Friend friend, int anchorY) {
        menuFriend = friend;
        menuAnchorY = anchorY;
        menuOpen.snap(0);
        menuOpen.setTarget(1);
    }

    public void closeMenu() {
        menuFriend = null;
        menuOpen.snap(0);
    }

    public Friend menuFriend() {
        return menuFriend;
    }

    public int menuAnchorY() {
        return menuAnchorY;
    }

    public float menuProgress() {
        return menuOpen.get();
    }
}
