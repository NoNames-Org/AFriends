package dev.no.name.blooddesigner.core.modes;

public record ModeRoute(int mainSlot, Submenu submenu, int subSlot, String command) {
    public enum Submenu {
        NONE(null),
        FFA("FFA selection"),
        MEETUPS("Meetups"),
        PRACTICE(null);

        private final String infoName;

        Submenu(String infoName) {
            this.infoName = infoName;
        }

        public String infoName() {
            return infoName;
        }
    }

    public static ModeRoute direct(int mainSlot) {
        return new ModeRoute(mainSlot, Submenu.NONE, -1, null);
    }

    public static ModeRoute via(int mainSlot, Submenu submenu, int subSlot) {
        return new ModeRoute(mainSlot, submenu, subSlot, null);
    }

    public static ModeRoute command(String command) {
        return new ModeRoute(-1, Submenu.NONE, -1, command);
    }

    public boolean isCommand() {
        return command != null;
    }

    public boolean hasSubmenu() {
        return submenu != Submenu.NONE;
    }
}
