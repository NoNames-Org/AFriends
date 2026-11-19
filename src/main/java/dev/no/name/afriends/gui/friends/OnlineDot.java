package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.addon.AddonRegistry;
import dev.no.name.afriends.api.FriendStatus;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;

/** Зелёная точка «в сети» в правом нижнем углу головы игрока: в друзьях, списке чатов и шапке переписки */
final class OnlineDot {
    static final int COLOR = 0xFF5CFFB0;
    private static final int SIZE = 6;

    private OnlineDot() {
    }

    // в сети ли игрок по данным дополнения; нет данных — считаем, что не в сети
    static boolean isOnline(AddonRegistry addons, String name) {
        FriendStatus.Status status = addons.status(name);
        return status != null && status.online();
    }

    // точка с тёмной обводкой, чтобы отделялась от головы
    static void draw(Gfx g, float faceX, float faceY, int face) {
        float x = faceX + face - SIZE + 2;
        float y = faceY + face - SIZE + 2;
        Draw.roundedRect(g, x, y, SIZE, SIZE, SIZE / 2f, Theme.PANEL_BOTTOM);
        Draw.roundedRect(g, x + 1, y + 1, SIZE - 2, SIZE - 2, (SIZE - 2) / 2f, COLOR);
    }
}
