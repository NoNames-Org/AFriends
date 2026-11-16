package dev.no.name.blooddesigner.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Текст сообщения с картинками по порядку: каждая метка [img] ставит на своё место очередную картинку,
 * картинки без метки — сверху (так у игрока фото всегда над подписью). То же правило на портале
 */
public final class MessageContent {
    private static final Pattern TOKEN = Pattern.compile("\\[img]", Pattern.CASE_INSENSITIVE);

    public sealed interface Block {
    }

    public record Text(String text) implements Block {
    }

    public record Image(MessageImage image) implements Block {
    }

    private MessageContent() {
    }

    public static List<Block> blocks(String text, List<MessageImage> images) {
        String[] parts = TOKEN.split(text == null ? "" : text, -1);
        int marks = parts.length - 1;
        List<Block> blocks = new ArrayList<>();
        for (int i = Math.min(marks, images.size()); i < images.size(); i++) {
            blocks.add(new Image(images.get(i)));
        }
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].replaceAll("^\\n+|\\n+$", "");
            if (!part.isBlank()) {
                blocks.add(new Text(part));
            }
            if (i < marks && i < images.size()) {
                blocks.add(new Image(images.get(i)));
            }
        }
        return blocks;
    }

    // текст без меток: для списка чатов, игрового чата и поиска
    public static String plain(String text) {
        return TOKEN.matcher(text == null ? "" : text).replaceAll(" ").replaceAll("[ \\t]+", " ").strip();
    }

    // подпись в списке чатов: текст, а если его нет — «Изображение» или «Гифка»
    public static String preview(PrivateMessage message) {
        String plain = plain(message.text()).replaceAll("\\s+", " ").strip();
        if (!plain.isEmpty()) {
            return plain;
        }
        MessageImage image = message.image();
        return image == null ? "" : image.animated() ? "Гифка" : "Изображение";
    }
}
