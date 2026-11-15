package dev.no.name.blooddesigner.gui.settings;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import dev.no.name.blooddesigner.gui.sound.UiSound;
import dev.no.name.blooddesigner.gui.sound.UiSounds;
import dev.no.name.blooddesigner.net.game.LinkSettings;
import dev.no.name.blooddesigner.net.game.LinkText;
import dev.no.name.blooddesigner.net.service.LinkService;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

// раздел «Сеть» в настройках: состояние связи и три переключателя
final class NetworkSection {
    private static final int ROW_HEIGHT = 26;
    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 11;
    private static final List<String> ABOUT = List.of(
            "ЛС друзьям с BloodDesigner идут со сквозным шифрованием:",
            "их не читает ни сервер Minecraft, ни сервер сети.",
            "Игрокам без мода — по-старому, через /msg сервера.");

    private record Toggle(String title, String hint, Supplier<Boolean> value, Consumer<Boolean> change) {
    }

    private final LinkService link;
    private final List<Toggle> toggles;

    NetworkSection(LinkSettings settings, LinkService link) {
        this.link = link;
        this.toggles = List.of(
                new Toggle("Подключаться к сети", "Друзья на любом сервере, ЛС без /msg, значки S и D",
                        settings::enabled, settings::setEnabled),
                new Toggle("Показывать друзьям сервер", "Иначе друзья видят только «в игре»",
                        settings::shareServer, settings::setShareServer),
                new Toggle("Значок у ника виден всем", "Иначе значок видят только друзья",
                        settings::visible, settings::setVisible));
    }

    void draw(Gfx g, int x, int top, int width, int mouseX, int mouseY) {
        String state = LinkText.connection(link.snapshot(), link.configured());
        Draw.text(g, Draw.trim(state, width), x, top, Theme.TEXT, TextStyle.TITLE);
        for (int i = 0; i < toggles.size(); i++) {
            Toggle toggle = toggles.get(i);
            int y = rowY(top, i);
            Draw.text(g, Draw.trim(toggle.title(), width - SWITCH_WIDTH - 8), x, y, Theme.TEXT);
            Draw.text(g, Draw.trim(toggle.hint(), width - SWITCH_WIDTH - 8, TextStyle.SMALL), x, y + 11,
                    Theme.TEXT_FAINT, TextStyle.SMALL);
            drawSwitch(g, x + width - SWITCH_WIDTH, y + 2, toggle.value().get());
        }
        int aboutY = rowY(top, toggles.size()) + 4;
        for (int i = 0; i < ABOUT.size(); i++) {
            Draw.text(g, Draw.trim(ABOUT.get(i), width, TextStyle.SMALL), x, aboutY + i * 10, Theme.TEXT_FAINT, TextStyle.SMALL);
        }
    }

    boolean click(double mouseX, double mouseY, int x, int top, int width) {
        for (int i = 0; i < toggles.size(); i++) {
            int y = rowY(top, i);
            if (mouseX >= x && mouseX < x + width && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 4) {
                Toggle toggle = toggles.get(i);
                toggle.change().accept(!toggle.value().get());
                UiSounds.play(UiSound.CLICK);
                return true;
            }
        }
        return false;
    }

    private static int rowY(int top, int index) {
        return top + 20 + index * ROW_HEIGHT;
    }

    private static void drawSwitch(Gfx g, float x, float y, boolean on) {
        float radius = SWITCH_HEIGHT / 2f;
        Draw.box(g, x, y, SWITCH_WIDTH, SWITCH_HEIGHT, radius, on ? Theme.ACCENT : Theme.CARD, on ? Theme.ACCENT : Theme.BORDER_HOVER);
        float knob = SWITCH_HEIGHT - 4;
        float knobX = on ? x + SWITCH_WIDTH - knob - 2 : x + 2;
        Draw.roundedRect(g, knobX, y + 2, knob, knob, knob / 2f, on ? Theme.ACCENT_TEXT : Theme.TEXT_SOFT);
    }
}
