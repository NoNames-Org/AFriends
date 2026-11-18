package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageFormat;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.media.VoiceClips;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.TextFieldWidget;
import dev.no.name.afriends.gui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Вкладка «Медиа» или «Голосовые» в профиле: картинки чата сеткой (новые сверху, клик — открыть крупно)
 * или список голосовых с автором и датой (клик — играть). Поле сверху ищет по подписи, у голосовых — по автору и дате
 */
final class ProfileMedia {
    private static final int FIELD = 16;
    private static final int COLUMNS = 4;
    private static final int GAP = 3;
    private static final int VOICE_ROW = 30;
    private static final int SCROLL_STEP = 16;

    enum Kind { MEDIA, VOICE }

    private record Item(MessageImage image, String text, String author, String date) {
    }

    // где что нарисовано в этом кадре: клик по картинке или голосовому
    private record Hit(MessageImage image, float x, float y, float width, float height) {
    }

    private final Kind kind;
    private final TextFieldWidget search;
    private final List<Hit> hits = new ArrayList<>();
    private List<Item> items = List.of();
    private int loadedFrom = -1;
    private float scroll;
    private float x;
    private float y;
    private float width;
    private float height;

    ProfileMedia(Kind kind) {
        this.kind = kind;
        String hint = kind == Kind.MEDIA ? "Поиск по подписи" : "Поиск по автору или дате";
        this.search = new TextFieldWidget(Icon.SEARCH, hint).withMaxLength(32);
    }

    void reset() {
        search.setText("");
        search.blur();
        scroll = 0;
        loadedFrom = -1;
    }

    void blur() {
        search.blur();
    }

    boolean isTyping() {
        return search.isFocused();
    }

    // переписка меняется, пока окно открыто: список собирается заново, когда в ней стало больше или меньше сообщений
    void load(List<PrivateMessage> chat, Function<PrivateMessage, String> author) {
        if (chat.size() == loadedFrom) {
            return;
        }
        loadedFrom = chat.size();
        List<Item> found = new ArrayList<>();
        for (int i = chat.size() - 1; i >= 0; i--) {
            PrivateMessage message = chat.get(i);
            for (MessageImage image : message.images()) {
                if (image.voice() == (kind == Kind.VOICE)) {
                    found.add(new Item(image, MessageFormat.plain(message.text()), author.apply(message), PanelKit.stamp(message.time())));
                }
            }
        }
        items = List.copyOf(found);
    }

    void layout(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        search.setBounds((int) x, (int) y, (int) width, FIELD);
    }

    private List<Item> shown() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return items;
        }
        return items.stream().filter(item -> kind == Kind.MEDIA ? item.text().toLowerCase(Locale.ROOT).contains(query)
                : item.author().toLowerCase(Locale.ROOT).contains(query) || item.date().contains(query)).toList();
    }

    private float listTop() {
        return y + FIELD + 4;
    }

    private float cell() {
        return (width - GAP * (COLUMNS - 1)) / COLUMNS;
    }

    private float contentHeight(int count) {
        if (kind == Kind.VOICE) {
            return count * VOICE_ROW;
        }
        int rows = (count + COLUMNS - 1) / COLUMNS;
        return rows * (cell() + GAP);
    }

    void draw(Gfx g, MessageRoute route, int mouseX, int mouseY) {
        search.render(g, mouseX, mouseY);
        hits.clear();
        List<Item> shown = shown();
        float top = listTop();
        float bottom = y + height;
        if (shown.isEmpty()) {
            String empty = !search.getText().isBlank() ? "Ничего не нашлось"
                    : kind == Kind.MEDIA ? "Картинок в этом чате пока нет" : "Голосовых в этом чате пока нет";
            Draw.textCentered(g, empty, x + width / 2f, top + 16, Theme.TEXT_DIM);
            return;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight(shown.size()) - (bottom - top))));
        g.enableScissor((int) x, (int) top, (int) (x + width), (int) bottom);
        long now = System.currentTimeMillis();
        for (int i = 0; i < shown.size(); i++) {
            if (kind == Kind.MEDIA) {
                drawCell(g, route, shown.get(i), i, top, bottom, now, mouseX, mouseY);
            } else {
                drawVoice(g, route, shown.get(i), i, top, bottom, mouseX, mouseY);
            }
        }
        g.disableScissor();
    }

    private void drawCell(Gfx g, MessageRoute route, Item item, int index, float top, float bottom, long now, int mouseX, int mouseY) {
        float cell = cell();
        float cellX = x + (index % COLUMNS) * (cell + GAP);
        float cellY = top + (index / COLUMNS) * (cell + GAP) - scroll;
        if (cellY + cell < top || cellY > bottom) {
            return;
        }
        Draw.roundedRect(g, cellX, cellY, cell, cell, Theme.RADIUS, Theme.TILE);
        MessageImage image = item.image();
        float ratio = Math.max(1, image.width()) / (float) Math.max(1, image.height());
        float w = ratio >= 1 ? cell : cell * ratio;
        float h = ratio >= 1 ? cell / ratio : cell;
        ImageBubble.draw(g, image, route, Math.round(cellX + (cell - w) / 2), Math.round(cellY + (cell - h) / 2),
                Math.round(w), Math.round(h), now);
        if (PanelKit.inside(mouseX, mouseY, cellX, cellY, cell, cell) && mouseY >= top && mouseY < bottom) {
            Draw.border(g, cellX, cellY, cell, cell, Theme.RADIUS, Theme.BORDER_SELECTED);
        }
        hits.add(new Hit(image, cellX, cellY, cell, cell));
    }

    private void drawVoice(Gfx g, MessageRoute route, Item item, int index, float top, float bottom, int mouseX, int mouseY) {
        float rowY = top + index * VOICE_ROW - scroll;
        if (rowY + VOICE_ROW < top || rowY > bottom) {
            return;
        }
        if (PanelKit.inside(mouseX, mouseY, x, rowY, width, VOICE_ROW) && mouseY >= top && mouseY < bottom) {
            Draw.roundedRect(g, x, rowY, width, VOICE_ROW - 2, Theme.RADIUS, Theme.CARD_HOVER);
        }
        VoiceBlock.draw(g, item.image(), route, x + 4, rowY + 3, Theme.TEXT);
        float textX = x + 4 + VoiceBlock.WIDTH + 8;
        int textWidth = (int) (x + width - 4 - textX);
        Draw.text(g, Draw.trim(item.author(), textWidth), textX, rowY + 5, Theme.TEXT, TextStyle.BODY_MEDIUM);
        Draw.text(g, Draw.trim(item.date(), textWidth, TextStyle.SMALL), textX, rowY + 16, Theme.TEXT_DIM, TextStyle.SMALL);
        hits.add(new Hit(item.image(), x, rowY, width, VOICE_ROW - 2));
    }

    // картинка, по которой кликнули (её открыть крупно); голосовое играет само. null — мимо
    MessageImage click(double mouseX, double mouseY, MessageRoute route) {
        if (search.mouseClicked(mouseX, mouseY, Widget.LEFT_BUTTON)) {
            return null;
        }
        if (mouseY < listTop() || mouseY >= y + height) {
            return null;
        }
        for (Hit hit : hits) {
            if (!PanelKit.inside(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height())) {
                continue;
            }
            if (kind == Kind.VOICE) {
                VoiceClips.toggle(hit.image(), route);
                UiSounds.play(UiSound.CLICK);
                return null;
            }
            return hit.image();
        }
        return null;
    }

    boolean keyPressed(int key) {
        return search.keyPressed(key);
    }

    boolean charTyped(String text) {
        boolean typed = search.charTyped(text);
        if (typed) {
            scroll = 0;
        }
        return typed;
    }

    void scroll(double amount) {
        scroll = Math.max(0, scroll - (float) (amount * SCROLL_STEP));
    }
}
