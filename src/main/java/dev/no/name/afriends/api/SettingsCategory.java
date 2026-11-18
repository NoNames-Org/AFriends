package dev.no.name.afriends.api;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.settings.ToggleList;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Категория в настройках AFriends: пункт слева и содержимое справа. */
public interface SettingsCategory {
    String title();

    /** Подпись над содержимым. */
    String subtitle();

    Icon icon();

    /** Содержимое начиная с (x, top) шириной width; координаты мыши — в тех же единицах. */
    void draw(Gfx g, int x, int top, int width, int mouseX, int mouseY);

    /** true — клик обработан. */
    boolean click(double mouseX, double mouseY, int x, int top, int width);

    /** Переключатель для {@link #toggles}. */
    record Toggle(String title, String description, BooleanSupplier enabled, Runnable toggle) {
    }

    /** Готовая категория из переключателей в стиле AFriends; note — подпись под ними (может быть пустой). */
    static SettingsCategory toggles(String title, String subtitle, Icon icon, List<Toggle> toggles, String note) {
        ToggleList list = new ToggleList(toggles, note);
        return new SettingsCategory() {
            @Override
            public String title() {
                return title;
            }

            @Override
            public String subtitle() {
                return subtitle;
            }

            @Override
            public Icon icon() {
                return icon;
            }

            @Override
            public void draw(Gfx g, int x, int top, int width, int mouseX, int mouseY) {
                list.draw(g, x, top, width);
            }

            @Override
            public boolean click(double mouseX, double mouseY, int x, int top, int width) {
                return list.click(mouseX, mouseY, x, top, width);
            }
        };
    }
}
