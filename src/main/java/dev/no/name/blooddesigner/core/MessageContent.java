package dev.no.name.blooddesigner.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Текст сообщения с картинками. Метка [img-xxxxxx] ставит на своё место картинку, чей id начинается с xxxxxx
 * (так портал различает несколько картинок), простая [img] — очередную ещё не поставленную. Картинки без метки —
 * сверху (так у игрока фото всегда над подписью). То же правило на портале (cloud/portal/js/ui/richText.js)
 */
public final class MessageContent {
    private static final Pattern TOKEN = Pattern.compile("\\[img(?:-([0-9a-z]+))?]", Pattern.CASE_INSENSITIVE);

    public sealed interface Block {
    }

    public record Text(String text) implements Block {
    }

    public record Image(MessageImage image) implements Block {
    }

    private MessageContent() {
    }

    public static List<Block> blocks(String text, List<MessageImage> images) {
        String source = text == null ? "" : text;
        List<String> parts = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(source);
        int from = 0;
        while (matcher.find()) {
            parts.add(source.substring(from, matcher.start()));
            ids.add(matcher.group(1));
            from = matcher.end();
        }
        parts.add(source.substring(from));
        Integer[] placed = place(ids, images);
        List<Block> blocks = new ArrayList<>();
        boolean[] used = new boolean[images.size()];
        for (Integer index : placed) {
            if (index != null) {
                used[index] = true;
            }
        }
        for (int i = 0; i < images.size(); i++) {
            if (!used[i]) {
                blocks.add(new Image(images.get(i)));
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).replaceAll("^\\n+|\\n+$", "");
            if (!part.isBlank()) {
                blocks.add(new Text(part));
            }
            if (i < placed.length && placed[i] != null) {
                blocks.add(new Image(images.get(placed[i])));
            }
        }
        return blocks;
    }

    // какая картинка у каждой метки: сначала метки с id, потом простые [img] по порядку из оставшихся
    private static Integer[] place(List<String> ids, List<MessageImage> images) {
        Integer[] placed = new Integer[ids.size()];
        boolean[] taken = new boolean[images.size()];
        for (int t = 0; t < ids.size(); t++) {
            String id = ids.get(t);
            if (id == null) {
                continue;
            }
            String prefix = id.toLowerCase(Locale.ROOT);
            for (int i = 0; i < images.size(); i++) {
                if (!taken[i] && images.get(i).blob().startsWith(prefix)) {
                    taken[i] = true;
                    placed[t] = i;
                    break;
                }
            }
        }
        int next = 0;
        for (int t = 0; t < ids.size(); t++) {
            if (ids.get(t) != null) {
                continue;
            }
            while (next < images.size() && taken[next]) {
                next++;
            }
            if (next < images.size()) {
                taken[next] = true;
                placed[t] = next++;
            }
        }
        return placed;
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
        return image == null ? "" : image.voice() ? "Голосовое сообщение" : image.animated() ? "Гифка" : "Изображение";
    }
}
