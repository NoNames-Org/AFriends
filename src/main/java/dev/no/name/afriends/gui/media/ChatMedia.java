package dev.no.name.afriends.gui.media;

import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.voice.VoiceClip;

import java.nio.file.Path;

/**
 * Картинки и голосовые переписки для отрисовки: откуда качать (route) и где держать загруженное.
 * Окна получают его явно вместо обращения к глобальным кешам
 */
public record ChatMedia(MessageRoute route, ImageTextures images, VoiceClips voices) {
    public ImageTextures.Entry image(MessageImage image) {
        return images.entry(image, route);
    }

    public ImageTextures.Entry fullImage(MessageImage image) {
        return images.full(image, route);
    }

    public ImageTextures.Entry file(Path file, boolean animated) {
        return images.file(file, animated);
    }

    /** Картинка из файла на диске; gif определяется по расширению. */
    public ImageTextures.Entry file(Path file) {
        return images.file(file);
    }

    /** Готовая запись или null, пока качается. */
    public VoiceClip voice(MessageImage voice) {
        return voices.get(voice, route);
    }

    public void toggleVoice(MessageImage voice) {
        voices.toggle(voice, route);
    }

    public VoicePlayer player() {
        return voices.player();
    }
}
