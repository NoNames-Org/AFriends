package dev.no.name.blooddesigner.gui.friends;

import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.core.GroupPoll;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import dev.no.name.blooddesigner.gui.sound.UiSound;
import dev.no.name.blooddesigner.gui.sound.UiSounds;
import dev.no.name.blooddesigner.gui.widget.TextFieldWidget;
import dev.no.name.blooddesigner.gui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Окно нового опроса посреди групповой переписки: вопрос и от 2 до 10 вариантов. «+ Вариант» добавляет поле,
 * крестик у третьего и дальше — убирает. Tab — к следующему полю, Enter — отправить, клик мимо или Esc — закрыть
 */
final class PollComposer {
    private static final int WIDTH = 220;
    private static final int PAD = 6;
    private static final int TITLE = 14;
    private static final int FIELD = 16;
    private static final int GAP = 3;
    private static final int ADD = 13;
    private static final int BUTTON = 16;
    private static final int REMOVE = 10;

    // chat — ключ группы; вопрос и варианты как их ввели
    private final BiConsumer<String, Poll> send;
    private final TextFieldWidget question = field(Icon.HELP, "Вопрос", GroupPoll.MAX_QUESTION);
    private final List<TextFieldWidget> options = new ArrayList<>();
    private String chat;
    private float x;
    private float y;
    private float height;
    private int areaHeight;

    record Poll(String question, List<String> options) {
    }

    PollComposer(BiConsumer<String, Poll> send) {
        this.send = send;
    }

    boolean isOpen() {
        return chat != null;
    }

    void open(String chat, int areaWidth, int areaHeight) {
        this.chat = chat;
        this.areaHeight = areaHeight;
        question.setText("");
        options.clear();
        for (int i = 0; i < GroupPoll.MIN_OPTIONS; i++) {
            options.add(option(i));
        }
        x = (areaWidth - WIDTH) / 2f;
        layout();
        focus(question);
    }

    void close() {
        chat = null;
        question.blur();
        options.forEach(TextFieldWidget::blur);
    }

    private static TextFieldWidget field(Icon icon, String placeholder, int max) {
        return new TextFieldWidget(icon, placeholder).withMaxLength(max);
    }

    private static TextFieldWidget option(int index) {
        return field(Icon.CIRCLE, "Вариант " + (index + 1), GroupPoll.MAX_OPTION);
    }

    private boolean canAdd() {
        return options.size() < GroupPoll.MAX_OPTIONS;
    }

    // окно растёт вместе с вариантами и остаётся по центру
    private void layout() {
        height = PAD * 2 + TITLE + (FIELD + GAP) * (1 + options.size()) + (canAdd() ? ADD : 0) + GAP + BUTTON;
        y = Math.max(2, (areaHeight - height) / 2f);
        float rowY = y + PAD + TITLE;
        question.setBounds((int) x + PAD, (int) rowY, WIDTH - PAD * 2, FIELD);
        for (int i = 0; i < options.size(); i++) {
            rowY += FIELD + GAP;
            int width = WIDTH - PAD * 2 - (removable(i) ? REMOVE + 2 : 0);
            options.get(i).setBounds((int) x + PAD, (int) rowY, width, FIELD);
        }
    }

    private boolean removable(int index) {
        return index >= GroupPoll.MIN_OPTIONS;
    }

    private float addY() {
        return y + PAD + TITLE + (FIELD + GAP) * (1 + options.size());
    }

    private float buttonY() {
        return y + height - PAD - BUTTON;
    }

    private Poll poll() {
        String text = question.getText().replaceAll("\\s+", " ").strip();
        List<String> values = options.stream().map(TextFieldWidget::getText).filter(value -> !value.isBlank()).toList();
        List<String> clean = GroupPoll.cleanOptions(values);
        return text.isEmpty() || clean == null ? null : new Poll(text, clean);
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (chat == null) {
            return;
        }
        Draw.shadow(g, x, y + 2, WIDTH, height, Theme.RADIUS + 2, 12, Theme.SHADOW);
        Draw.box(g, x, y, WIDTH, height, Theme.RADIUS + 2, Theme.POPUP, Theme.BORDER);
        Draw.text(g, "Новый опрос", x + PAD, y + PAD, Theme.TEXT_SOFT, TextStyle.SMALL);
        question.render(g, mouseX, mouseY);
        for (int i = 0; i < options.size(); i++) {
            TextFieldWidget field = options.get(i);
            field.render(g, mouseX, mouseY);
            if (removable(i)) {
                float removeX = field.getX() + field.getWidth() + 2;
                boolean hover = mouseX >= removeX && mouseX < removeX + REMOVE && mouseY >= field.getY() && mouseY < field.getY() + FIELD;
                Draw.icon(g, Icon.CLOSE, removeX + 1, field.getY() + 4, REMOVE - 2, hover ? Theme.DANGER : Theme.TEXT_DIM);
            }
        }
        if (canAdd()) {
            float addY = addY();
            boolean hover = mouseX >= x + PAD && mouseX < x + WIDTH - PAD && mouseY >= addY && mouseY < addY + ADD;
            Draw.text(g, "+ Вариант", x + PAD + 2, addY + 3, hover ? Theme.TEXT : Theme.TEXT_DIM, TextStyle.SMALL);
        }
        boolean ready = poll() != null;
        float buttonY = buttonY();
        Draw.roundedRect(g, x + PAD, buttonY, WIDTH - PAD * 2, BUTTON, Theme.RADIUS, ready ? Theme.ACCENT : Theme.CARD);
        String label = ready ? "Отправить опрос" : "Вопрос и хотя бы 2 разных варианта";
        Draw.textCentered(g, label, x + WIDTH / 2f, buttonY + 4, ready ? Theme.ACCENT_TEXT : Theme.TEXT_DIM);
    }

    // true — клик внутри окна (обработан); мимо окна оно закрывается
    boolean click(double mouseX, double mouseY) {
        if (chat == null) {
            return false;
        }
        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + height) {
            close();
            return true;
        }
        for (int i = GroupPoll.MIN_OPTIONS; i < options.size(); i++) {
            TextFieldWidget field = options.get(i);
            float removeX = field.getX() + field.getWidth() + 2;
            if (mouseX >= removeX && mouseX < removeX + REMOVE && mouseY >= field.getY() && mouseY < field.getY() + FIELD) {
                field.blur();
                options.remove(i);
                renumber();
                layout();
                UiSounds.play(UiSound.DELETE);
                return true;
            }
        }
        if (canAdd() && mouseY >= addY() && mouseY < addY() + ADD) {
            addOption();
            return true;
        }
        if (mouseY >= buttonY() && mouseY < buttonY() + BUTTON) {
            submit();
            return true;
        }
        question.mouseClicked(mouseX, mouseY, Widget.LEFT_BUTTON);
        options.forEach(field -> field.mouseClicked(mouseX, mouseY, Widget.LEFT_BUTTON));
        return true;
    }

    private void addOption() {
        TextFieldWidget field = option(options.size());
        options.add(field);
        layout();
        focus(field);
        UiSounds.play(UiSound.SELECT);
    }

    // подсказки «Вариант N» идут по порядку и после удаления из середины
    private void renumber() {
        for (int i = 0; i < options.size(); i++) {
            TextFieldWidget old = options.get(i);
            TextFieldWidget fresh = option(i);
            fresh.setText(old.getText());
            if (old.isFocused()) {
                old.blur();
                fresh.focus();
            }
            options.set(i, fresh);
        }
    }

    private void submit() {
        Poll poll = poll();
        if (poll == null) {
            return;
        }
        String target = chat;
        close();
        UiSounds.play(UiSound.CLICK);
        send.accept(target, poll);
    }

    private void focus(TextFieldWidget field) {
        question.blur();
        options.forEach(TextFieldWidget::blur);
        field.focus();
    }

    // Tab — следующее поле; на последнем варианте Tab добавляет новый, пока можно
    private void focusNext() {
        List<TextFieldWidget> fields = new ArrayList<>();
        fields.add(question);
        fields.addAll(options);
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).isFocused()) {
                if (i + 1 < fields.size()) {
                    focus(fields.get(i + 1));
                } else if (canAdd()) {
                    addOption();
                } else {
                    focus(question);
                }
                return;
            }
        }
        focus(question);
    }

    boolean keyPressed(int key) {
        if (chat == null) {
            return false;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            submit();
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            focusNext();
            return true;
        }
        question.keyPressed(key);
        options.forEach(field -> field.keyPressed(key));
        return true;
    }

    boolean charTyped(String text) {
        if (chat == null) {
            return false;
        }
        if (!question.charTyped(text)) {
            options.forEach(field -> field.charTyped(text));
        }
        return true;
    }
}
