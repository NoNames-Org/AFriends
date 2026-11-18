package dev.no.name.afriends.gui.theme;

public enum ThemePreset {
    GRAPHITE("graphite", "Графит", 0x0B0C0F, 0xFFFFFFFF),
    BLOOD("blood", "Кровь", 0x120A0C, 0xFFE0313F),
    OCEAN("ocean", "Океан", 0x0A1224, 0xFF3D7BFF),
    LIME("lime", "Лайм", 0x0F1210, 0xFFC6FF3D),
    VIOLET("violet", "Фиолет", 0x110D1A, 0xFFB58CFF),
    AMBER("amber", "Янтарь", 0x14100A, 0xFFFFB53D);

    private final String id;
    private final String title;
    private final int background;
    private final int accent;

    ThemePreset(String id, String title, int background, int accent) {
        this.id = id;
        this.title = title;
        this.background = background;
        this.accent = accent;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public int background() {
        return background;
    }

    public int accent() {
        return accent;
    }

    public static ThemePreset find(String id) {
        for (ThemePreset preset : values()) {
            if (preset.id.equals(id)) {
                return preset;
            }
        }
        return null;
    }
}
