package dev.no.name.afriends.api;

import dev.no.name.afriends.gui.hub.PanelState;
import dev.no.name.afriends.gui.render.icon.Icon;

/**
 * Кнопка в панели справа от чата и панель, которую она открывает. Открыта всегда одна панель: AFriends сам
 * закрывает остальные. Панель создаётся заново при каждом открытии чата.
 */
public interface RailPanel {
    Icon icon();

    String label();

    /** Показывать ли кнопку сейчас (например, только на своём сервере). Спрашивается при открытии чата. */
    default boolean visible() {
        return true;
    }

    /** Значок на кнопке: число или «!»; null — без значка. */
    default String badge() {
        return null;
    }

    /**
     * Открыть эту панель сразу при открытии чата, закрыв «Друзей» и остальные (например, по кнопке «Играть» из
     * переписки). Спрашивается один раз при открытии чата, после {@link #create}. Сам аддон не может закрыть чужие
     * панели, поэтому открывает их AFriends. Появилось в контракте 2.1.
     */
    default boolean openOnAttach() {
        return false;
    }

    /** Панель в прямоугольнике слева от кнопок; state — открыта ли она и анимация открытия. */
    SidePanel create(Bounds bounds, PanelState state);

    record Bounds(int x, int y, int width, int height) {
    }
}
