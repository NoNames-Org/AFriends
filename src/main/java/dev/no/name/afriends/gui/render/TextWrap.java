package dev.no.name.afriends.gui.render;

import java.util.ArrayList;
import java.util.List;

/** Перенос текста по словам в заданную ширину (обычный шрифт); слишком длинное слово режется. */
public final class TextWrap {
    private TextWrap() {
    }

    // перевод строки начинает новую строку: он бывает в объявлениях с портала
    public static List<String> lines(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            lines.addAll(paragraph(paragraph, maxWidth));
        }
        return lines;
    }

    private static List<String> paragraph(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (Draw.width(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            String rest = word;
            while (Draw.width(rest) > maxWidth && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && Draw.width(rest.substring(0, cut)) > maxWidth) {
                    cut--;
                }
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
