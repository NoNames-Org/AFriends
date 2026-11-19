package dev.no.name.afriends.gui.friends;

import java.util.List;
import java.util.function.Function;

/**
 * Окошки мессенджера в порядке, в котором они получают события. Порядок у каждого вида события свой
 * (так было исторически: например, просмотр картинки первым ловит клик, но клавиши отдаёт редакторам),
 * поэтому стек хранит четыре списка, а не один z-порядок
 */
final class OverlayStack {
    private final List<Overlay> clicks;
    private final List<Overlay> keys;
    private final List<Overlay> typing;
    private final List<Overlay> scrolls;

    OverlayStack(List<Overlay> clicks, List<Overlay> keys, List<Overlay> typing, List<Overlay> scrolls) {
        this.clicks = List.copyOf(clicks);
        this.keys = List.copyOf(keys);
        this.typing = List.copyOf(typing);
        this.scrolls = List.copyOf(scrolls);
    }

    Overlay.Result click(double x, double y, boolean right) {
        return first(clicks, overlay -> overlay.click(x, y, right));
    }

    Overlay.Result key(int key) {
        return first(keys, overlay -> overlay.key(key));
    }

    Overlay.Result type(String text) {
        return first(typing, overlay -> overlay.type(text));
    }

    Overlay.Result scroll(double x, double y, double amount) {
        return first(scrolls, overlay -> overlay.scroll(x, y, amount));
    }

    private static Overlay.Result first(List<Overlay> overlays, Function<Overlay, Overlay.Result> event) {
        for (Overlay overlay : overlays) {
            Overlay.Result result = event.apply(overlay);
            if (result != Overlay.Result.PASS) {
                return result;
            }
        }
        return Overlay.Result.PASS;
    }
}
