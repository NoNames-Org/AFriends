package dev.no.name.afriends.gui.settings;

import dev.no.name.afriends.api.SettingsCategory;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.util.SystemBrowser;
import dev.no.name.afriends.net.update.VersionCheck;

/**
 * «О моде»: своя и последняя версия, авторы и кнопка «Обновить». Последняя версия подтягивается с сервера
 * (последний релиз на GitHub); пока ответа нет — «Проверяем…», если версия устарела — появляется кнопка на
 * страницу проекта. Это обычная категория настроек со своей отрисовкой, рядом со «Звуком» и аддонами
 */
public final class AboutCategory implements SettingsCategory {
    private static final String SITE = "https://no-names.com/projects/AFriends";
    private static final int BUTTON_WIDTH = 92;
    private static final int BUTTON_HEIGHT = 20;

    @Override
    public String title() {
        return "О моде";
    }

    @Override
    public String subtitle() {
        return "Версия, авторы и обновление";
    }

    @Override
    public Icon icon() {
        return Icon.INFO;
    }

    @Override
    public void draw(Gfx g, int x, int top, int width, int mouseX, int mouseY) {
        VersionCheck check = VersionCheck.get();
        check.refresh();

        Draw.text(g, "Текущая версия", x, top, Theme.TEXT_SOFT);
        Draw.text(g, Draw.trim(check.current(), width), x, top + 12, Theme.TEXT, TextStyle.TITLE);

        int statusY = top + 34;
        if (check.outdated()) {
            Draw.text(g, Draw.trim("Доступна версия " + check.latest(), width), x, statusY, Theme.ACCENT);
        } else if (check.checked()) {
            Draw.text(g, "У вас последняя версия", x, statusY, Theme.TEXT_SOFT);
        } else {
            Draw.text(g, "Проверяем обновления…", x, statusY, Theme.TEXT_DIM);
        }

        String authors = check.authors();
        int authorsY = statusY + 22;
        Draw.text(g, "Авторы", x, authorsY, Theme.TEXT_SOFT);
        Draw.text(g, Draw.trim(authors.isEmpty() ? "—" : authors, width), x, authorsY + 12, Theme.TEXT);

        if (check.outdated()) {
            int[] button = buttonRect(x, top, width);
            boolean hovered = inside(mouseX, mouseY, button);
            Draw.box(g, button[0], button[1], button[2], button[3], Theme.RADIUS,
                    hovered ? Theme.CARD_HOVER : Theme.CARD_SELECTED, Theme.ACCENT);
            Draw.icon(g, Icon.DOWNLOAD, button[0] + 10, button[1] + (button[3] - Theme.ICON_SMALL) / 2f,
                    Theme.ICON_SMALL, Theme.ACCENT);
            Draw.text(g, "Обновить", button[0] + 14 + Theme.ICON_SMALL, button[1] + (button[3] - 8) / 2f, Theme.TEXT);
        }
    }

    @Override
    public boolean click(double mouseX, double mouseY, int x, int top, int width) {
        if (!VersionCheck.get().outdated()) {
            return false;
        }
        if (inside((int) mouseX, (int) mouseY, buttonRect(x, top, width))) {
            UiSounds.play(UiSound.CLICK);
            SystemBrowser.open(SITE);
            return true;
        }
        return false;
    }

    // кнопка «Обновить» под строкой авторов, у левого края содержимого
    private static int[] buttonRect(int x, int top, int width) {
        int y = top + 34 + 22 + 12 + 16;
        return new int[]{x, y, Math.min(BUTTON_WIDTH, width), BUTTON_HEIGHT};
    }

    private static boolean inside(double mouseX, double mouseY, int[] rect) {
        return mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }
}
