package dev.no.name.afriends.gui.friends;

import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.afriends.api.ComposeAction;
import dev.no.name.afriends.compat.Compat;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageFormat;
import dev.no.name.afriends.core.voice.VoiceClip;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.ButtonWidget;
import dev.no.name.afriends.gui.widget.ContainerWidget;
import dev.no.name.afriends.gui.widget.IconButtonWidget;
import dev.no.name.afriends.gui.widget.TextFieldWidget;
import dev.no.name.afriends.gui.widget.Widget;

import java.nio.file.Path;
import java.util.List;

import static dev.no.name.afriends.gui.friends.MessengerLayout.BAR_ICON;
import static dev.no.name.afriends.gui.friends.MessengerLayout.BAR_WIDTH;
import static dev.no.name.afriends.gui.friends.MessengerLayout.BAR_X;
import static dev.no.name.afriends.gui.friends.MessengerLayout.BAR_Y;
import static dev.no.name.afriends.gui.friends.MessengerLayout.CHAT_WIDTH;
import static dev.no.name.afriends.gui.friends.MessengerLayout.CHAT_X;
import static dev.no.name.afriends.gui.friends.MessengerLayout.DANGER;
import static dev.no.name.afriends.gui.friends.MessengerLayout.HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.INPUT_HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.MAX_INPUT_LINES;
import static dev.no.name.afriends.gui.friends.MessengerLayout.MAX_TEXT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.PAD;
import static dev.no.name.afriends.gui.friends.MessengerLayout.SEND_WIDTH;
import static dev.no.name.afriends.gui.friends.MessengerLayout.WIDTH;

/**
 * Полоса ввода как в Telegram: скрепка, многострочное поле, кнопки дополнений, избранные гифки, микрофон
 * и «отправить»; над ней — ответ и прикреплённая картинка, поверх — запись голосового
 */
final class ComposerView {
    /** Что полосе ввода нужно от окна. */
    interface Host {
        /** Кнопка дополнения у поля ввода; buttonX — где она стоит. */
        void compose(ComposeAction action, int buttonX);

        void toggleGifs();

        /** Сообщение ушло: лента прокручивается вниз. */
        void sent();
    }

    private final ChatController controller;
    private final MediaActions media;
    private final Host host;
    private final ContainerWidget container = new ContainerWidget();
    private final TextFieldWidget input;
    private final ReplyStrip replyStrip = new ReplyStrip();
    private final AttachmentStrip attachment;
    private final VoiceStrip voiceStrip;
    // многострочное поле растёт вверх: храним его столбец и насколько оно сейчас выше одной строки
    private final int inputX;
    private final int inputWidth;
    private int inputExtra;

    ComposerView(ChatController controller, MediaActions media, AttachmentStrip attachment,
                 List<ComposeAction> actions, Host host) {
        this.controller = controller;
        this.media = media;
        this.attachment = attachment;
        this.host = host;
        this.voiceStrip = new VoiceStrip(this::sendVoice, controller::warn);
        input = new TextFieldWidget(null, "Написать сообщение…").withMaxLength(MAX_TEXT).withoutFrame().withMultiline();
        // скрепка — слева внутри полосы: выбранная картинка встаёт над полем и уйдёт вместе с текстом
        IconButtonWidget attach = new IconButtonWidget(Icon.IMAGE, () -> media.choose(controller.partner(), this::stage));
        attach.setBounds(BAR_X + 2, BAR_Y, BAR_ICON, INPUT_HEIGHT);
        container.addChild(attach);
        // справа внутри полосы: кнопки дополнений и избранные гифки
        int icons = actions.size() + 2;
        int iconsX = BAR_X + BAR_WIDTH - 2 - icons * BAR_ICON;
        for (int i = 0; i < actions.size(); i++) {
            ComposeAction action = actions.get(i);
            int buttonX = iconsX + i * BAR_ICON;
            addIcon(action.icon(), buttonX, () -> host.compose(action, buttonX));
        }
        addIcon(Icon.STAR, iconsX + actions.size() * BAR_ICON, host::toggleGifs);
        // микрофон: голосовое другу по сети
        addIcon(Icon.MIC, iconsX + (actions.size() + 1) * BAR_ICON, this::toggleVoice);
        inputX = BAR_X + 2 + BAR_ICON + 2;
        inputWidth = iconsX - inputX - 2;
        input.setBounds(inputX, BAR_Y, inputWidth, INPUT_HEIGHT);
        ButtonWidget send = new ButtonWidget(Icon.SEND, this::send);
        send.setBounds(WIDTH - PAD - SEND_WIDTH, BAR_Y, SEND_WIDTH, INPUT_HEIGHT);
        container.setBounds(0, 0, WIDTH, HEIGHT);
        container.addChild(input);
        container.addChild(send);
    }

    private void addIcon(Icon icon, int x, Runnable action) {
        IconButtonWidget button = new IconButtonWidget(icon, action);
        button.setBounds(x, BAR_Y, BAR_ICON, INPUT_HEIGHT);
        container.addChild(button);
    }

    TextFieldWidget input() {
        return input;
    }

    ReplyStrip replyStrip() {
        return replyStrip;
    }

    AttachmentStrip attachment() {
        return attachment;
    }

    VoiceStrip voiceStrip() {
        return voiceStrip;
    }

    /** Картинка встаёт над полем и уйдёт вместе с текстом. */
    void stage(Path file) {
        attachment.stage(file);
        input.focus();
    }

    /** Другой чат: ответ, вложение и запись отменяются. */
    void resetForChat() {
        replyStrip.clear();
        attachment.clear();
        voiceStrip.cancel();
    }

    // ---- разметка ----

    // поле ввода растёт вверх по числу строк (до MAX_INPUT_LINES), кнопки остаются внизу
    void updateLayout() {
        int lines = Math.min(MAX_INPUT_LINES, input.lineCount());
        inputExtra = (lines - 1) * TextFieldWidget.lineHeight();
        input.setBounds(inputX, BAR_Y - inputExtra, inputWidth, INPUT_HEIGHT + inputExtra);
    }

    // верх области ввода: если картинка прикреплена, её превью стоит над полосой
    int top() {
        if (controller.isReadOnly()) {
            return BAR_Y;
        }
        return BAR_Y - inputExtra - (attachment.isStaged() ? AttachmentStrip.HEIGHT + 4 : 0)
                - (replyStrip.isActive() ? ReplyStrip.HEIGHT + 4 : 0);
    }

    // ---- отрисовка ----

    /** Полоса ввода с кнопками; у объявлений — плашка «только чтение». */
    void draw(Gfx g, int mouseX, int mouseY) {
        if (controller.isAnnouncements()) {
            drawReadOnly(g, "Только чтение — сюда приходят новости мода");
            return;
        }
        if (controller.partner() == null) {
            return;
        }
        drawBar(g, mouseX, mouseY);
        container.render(g, mouseX, mouseY);
        voiceStrip.tick();
        voiceStrip.draw(g, BAR_X, BAR_Y, BAR_WIDTH, INPUT_HEIGHT);
    }

    // общая рамка полосы ввода: внутри неё скрепка, поле и значки
    private void drawBar(Gfx g, int mouseX, int mouseY) {
        // плашка растёт вверх вместе с многострочным полем; значки и «отправить» остаются внизу
        int barY = BAR_Y - inputExtra;
        int border = input.isFocused() ? Theme.borderSelected() : Theme.border();
        Draw.box(g, BAR_X, barY, BAR_WIDTH, INPUT_HEIGHT + inputExtra, Theme.RADIUS, Theme.card(), border);
        // над полосой: сначала ответ, над ним прикреплённая картинка
        int replyY = barY - ReplyStrip.HEIGHT - 4;
        replyStrip.draw(g, BAR_X, replyY, BAR_WIDTH + SEND_WIDTH + 4, mouseX, mouseY);
        int attachY = (replyStrip.isActive() ? replyY : barY) - AttachmentStrip.HEIGHT - 4;
        attachment.draw(g, BAR_X, attachY, BAR_WIDTH + SEND_WIDTH + 4, mouseX, mouseY);
    }

    /** Вместо поля ввода в чатах только для чтения. */
    private static void drawReadOnly(Gfx g, String text) {
        float y = BAR_Y;
        Draw.box(g, CHAT_X + PAD, y, CHAT_WIDTH - PAD * 2, INPUT_HEIGHT, Theme.RADIUS, Theme.card(), Theme.border());
        float textX = CHAT_X + PAD + (CHAT_WIDTH - PAD * 2 - Theme.ICON_SMALL - 5 - Draw.width(text)) / 2f;
        Draw.icon(g, Icon.LOCK, textX, y + (INPUT_HEIGHT - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL, Theme.textDim());
        Draw.text(g, text, textX + Theme.ICON_SMALL + 5, y + (INPUT_HEIGHT - 8) / 2f, Theme.textDim());
    }

    /** Подпись-предупреждение над полосой ввода. */
    void drawWarning(Gfx g) {
        String warning = controller.visibleWarning(System.currentTimeMillis());
        if (warning.isEmpty()) {
            return;
        }
        float width = Math.min(CHAT_WIDTH - PAD * 2, Draw.width(warning, TextStyle.SMALL) + 12);
        float x = CHAT_X + (CHAT_WIDTH - width) / 2f;
        float y = top() - 18;
        Draw.box(g, x, y, width, 14, Theme.RADIUS, Theme.popup(), DANGER);
        Draw.text(g, Draw.trim(warning, (int) width - 12, TextStyle.SMALL), x + 6, y + 4, DANGER, TextStyle.SMALL);
    }

    // ---- отправка ----

    void send() {
        if (voiceStrip.isRecording()) {
            voiceStrip.finish();
            return;
        }
        // больше трёх пустых строк подряд не уходит
        String text = MessageFormat.limitBlankLines(input.getText()).strip();
        // прикреплённая картинка уходит вместе с текстом: картинка сверху, текст подписью
        if (attachment.isStaged()) {
            String partner = controller.partner();
            if (partner != null && !controller.isReadOnly() && media.send(partner, attachment.file(), text, replyStrip.reply())) {
                attachment.clear();
                replyStrip.clear();
                input.setText("");
            }
            return;
        }
        if (!text.isEmpty() && sendText(text)) {
            input.setText("");
        }
    }

    // false — не ушло: нет соединения или дополнение не пустило
    boolean sendText(String text) {
        if (!controller.sendText(text, replyStrip.reply())) {
            return false;
        }
        replyStrip.clear();
        host.sent();
        return true;
    }

    private void toggleVoice() {
        if (!voiceStrip.isRecording() && !controller.canSendVoice()) {
            controller.warn("Голосовые можно отправлять только другу по сети");
            return;
        }
        voiceStrip.toggle();
        UiSounds.play(UiSound.CLICK);
    }

    private void sendVoice(VoiceClip clip) {
        if (controller.sendVoice(clip.encode(), clip.centiseconds(), replyStrip.reply())) {
            replyStrip.clear();
        }
    }

    // ---- ввод ----

    boolean active() {
        return controller.partner() != null && !controller.isAnnouncements();
    }

    boolean mouseClicked(double x, double y) {
        return active() && container.mouseClicked(x, y, Widget.LEFT_BUTTON);
    }

    boolean keyPressed(int key) {
        return !controller.isAnnouncements() && container.keyPressed(key);
    }

    boolean charTyped(String text) {
        return !controller.isAnnouncements() && container.charTyped(text);
    }

    /** Enter в поле: Shift+Enter — перенос строки, обычный Enter — отправка. false — поле не в фокусе. */
    boolean enter() {
        if (!input.isFocused()) {
            return false;
        }
        if (Compat.isKeyDown(InputConstants.KEY_LSHIFT) || Compat.isKeyDown(InputConstants.KEY_RSHIFT)) {
            input.appendNewline();
        } else {
            send();
        }
        return true;
    }
}
