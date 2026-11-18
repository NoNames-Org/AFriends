package dev.no.name.afriends.gui.render;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageFormat;
import dev.no.name.afriends.core.MessageFormat.Kind;
import dev.no.name.afriends.gui.render.text.TextStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Текст сообщения с разметкой (MessageFormat): перенос по словам в заданную ширину и рисование.
 * Жирный — шрифт средней толщины, заголовки — TITLE и SUBTITLE, курсив — ванильный наклон глифов,
 * подчёркивание и зачёркивание — свои линии по базовой линии шрифта, код — подложка (моноширинного шрифта нет).
 * Ширина кусков меряется без округления, поэтому перенос совпадает с рисованием
 */
public final class FormattedText {
    public static final float LINE_HEIGHT = 9;
    private static final float HEADING_HEIGHT = 13;
    private static final float SUBHEADING_HEIGHT = 11;
    private static final float HEADING_OFFSET = 2;
    private static final float SUBHEADING_OFFSET = 1.5f;
    // цитата: полоска слева и отступ текста от неё
    private static final float QUOTE_BAR = 2;
    private static final float QUOTE_INDENT = 6;
    private static final float QUOTE_TEXT_FADE = 0.75f;
    private static final float QUOTE_BAR_FADE = 0.5f;
    private static final float CODE_FADE = 0.14f;
    private static final float CODE_PAD = 1;
    private static final float CODE_RADIUS = 1.5f;
    // наклонённый последний глиф чуть выступает за ширину строки
    private static final float ITALIC_OVERHANG = 1;
    // положение линий относительно базовой линии, в долях размера шрифта
    private static final float UNDERLINE_SHIFT = 0.12f;
    private static final float STRIKE_SHIFT = 0.3f;
    private static final float CODE_TOP = 0.93f;
    private static final float CODE_HEIGHT = 1.21f;

    // кусок строки одного начертания; x — от левого края строки
    public record Run(String text, int flags, TextStyle style, float x, float width) {
        boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    // строка после переноса: width — с отступом цитаты, height — своя у заголовков
    public record Line(Kind kind, List<Run> runs, float width, float height) {
    }

    private record Piece(String text, int flags, float width) {
    }

    private FormattedText() {
    }

    // текст с разметкой, перенесённый в maxWidth
    public static List<Line> layout(String text, float maxWidth) {
        List<Line> lines = new ArrayList<>();
        for (MessageFormat.Line line : MessageFormat.parse(text)) {
            lines.addAll(new Wrapper(line.kind(), maxWidth).wrap(line.spans()));
        }
        return List.copyOf(lines);
    }

    // текст без разметки (скрытое для стримера): тот же перенос, что TextWrap
    public static List<Line> plain(String text, float maxWidth) {
        List<Line> lines = new ArrayList<>();
        for (String line : TextWrap.lines(text, maxWidth)) {
            float width = Draw.width(line);
            lines.add(new Line(Kind.NORMAL, List.of(new Run(line, 0, TextStyle.BODY, 0, width)), width, LINE_HEIGHT));
        }
        return List.copyOf(lines);
    }

    public static float widest(List<Line> lines) {
        float widest = 0;
        for (Line line : lines) {
            widest = Math.max(widest, line.width());
        }
        return widest;
    }

    public static float height(List<Line> lines) {
        float height = 0;
        for (Line line : lines) {
            height += line.height();
        }
        return height;
    }

    public static float lastWidth(List<Line> lines) {
        return lines.isEmpty() ? 0 : lines.get(lines.size() - 1).width();
    }

    // где рисовать время после последней строки (endY — низ текста): у обычной строки это endY - 9 + 1, как раньше,
    // у заголовка — так же по его базовой линии
    public static float timeY(List<Line> lines, float endY) {
        if (lines.isEmpty()) {
            return endY - LINE_HEIGHT + 1;
        }
        Line last = lines.get(lines.size() - 1);
        float lastBase = endY - last.height() + offset(last.kind()) + Draw.baseline(0, style(last.kind(), 0));
        float normalBase = endY - LINE_HEIGHT + Draw.baseline(0, TextStyle.BODY);
        return endY - LINE_HEIGHT + 1 + (lastBase - normalBase);
    }

    // рисует строки и возвращает низ текста
    public static float draw(Gfx g, List<Line> lines, float x, float y, int color) {
        float lineY = y;
        for (Line line : lines) {
            drawLine(g, line, x, lineY, color);
            lineY += line.height();
        }
        return lineY;
    }

    private static void drawLine(Gfx g, Line line, float x, float lineY, int color) {
        int textColor = color;
        if (line.kind() == Kind.QUOTE) {
            Draw.rect(g, x, lineY, QUOTE_BAR, line.height(), Colors.fade(color, QUOTE_BAR_FADE));
            textColor = Colors.fade(color, QUOTE_TEXT_FADE);
        }
        float textY = lineY + offset(line.kind());
        float thickness = Math.max(1, Math.round(Draw.pixelScale() * 0.6f)) / (float) Draw.pixelScale();
        for (Run run : line.runs()) {
            float runX = x + run.x();
            float size = run.style().getSize();
            float base = Draw.baseline(textY, run.style());
            if (run.has(MessageFormat.CODE)) {
                Draw.roundedRect(g, runX - CODE_PAD, base - size * CODE_TOP, run.width() + CODE_PAD * 2, size * CODE_HEIGHT,
                        CODE_RADIUS, Colors.fade(color, CODE_FADE));
            }
            if (run.has(MessageFormat.ITALIC)) {
                Draw.italicText(g, run.text(), runX, textY, textColor, run.style());
            } else {
                Draw.text(g, run.text(), runX, textY, textColor, run.style());
            }
            if (run.has(MessageFormat.UNDERLINE)) {
                Draw.rect(g, runX, base + size * UNDERLINE_SHIFT, run.width(), thickness, textColor);
            }
            if (run.has(MessageFormat.STRIKE)) {
                Draw.rect(g, runX, base - size * STRIKE_SHIFT, run.width(), thickness, textColor);
            }
        }
    }

    private static float offset(Kind kind) {
        return switch (kind) {
            case HEADING -> HEADING_OFFSET;
            case SUBHEADING -> SUBHEADING_OFFSET;
            default -> 0;
        };
    }

    private static float lineHeight(Kind kind) {
        return switch (kind) {
            case HEADING -> HEADING_HEIGHT;
            case SUBHEADING -> SUBHEADING_HEIGHT;
            default -> LINE_HEIGHT;
        };
    }

    // заголовки целиком своим шрифтом, в обычном тексте жирный — средняя толщина
    private static TextStyle style(Kind kind, int flags) {
        return switch (kind) {
            case HEADING -> TextStyle.TITLE;
            case SUBHEADING -> TextStyle.SUBTITLE;
            default -> (flags & MessageFormat.BOLD) != 0 ? TextStyle.BODY_MEDIUM : TextStyle.BODY;
        };
    }

    // перенос одной строки разметки: слова (возможно из кусков разного начертания) по пробелам, длинное слово режется
    private static final class Wrapper {
        private final Kind kind;
        private final float indent;
        private final float available;
        private final List<Line> lines = new ArrayList<>();
        private final List<Piece> current = new ArrayList<>();
        private final List<Piece> spaces = new ArrayList<>();
        private final List<Piece> word = new ArrayList<>();
        private float currentWidth;
        private float spacesWidth;
        private float wordWidth;

        Wrapper(Kind kind, float maxWidth) {
            this.kind = kind;
            this.indent = kind == Kind.QUOTE ? QUOTE_INDENT : 0;
            this.available = Math.max(1, maxWidth - indent);
        }

        List<Line> wrap(List<MessageFormat.Span> spans) {
            for (MessageFormat.Span span : spans) {
                split(span.text(), span.flags());
            }
            placeWord();
            // пробелы в начале строки сохраняются, как в TextWrap
            if (lines.isEmpty() && current.isEmpty()) {
                current.addAll(spaces);
            }
            if (!current.isEmpty() || lines.isEmpty()) {
                emit();
            }
            return lines;
        }

        private void split(String text, int flags) {
            int start = 0;
            while (start < text.length()) {
                boolean space = text.charAt(start) == ' ';
                int end = start;
                while (end < text.length() && (text.charAt(end) == ' ') == space) {
                    end++;
                }
                Piece piece = piece(text.substring(start, end), flags);
                if (space) {
                    placeWord();
                    spaces.add(piece);
                    spacesWidth += piece.width();
                } else {
                    word.add(piece);
                    wordWidth += piece.width();
                }
                start = end;
            }
        }

        private void placeWord() {
            if (word.isEmpty()) {
                return;
            }
            // в начале перенесённой строки пробелы не нужны, в самом начале текста — остаются
            boolean keepSpaces = !current.isEmpty() || lines.isEmpty();
            float total = currentWidth + (keepSpaces ? spacesWidth : 0) + wordWidth;
            if (total <= available) {
                if (keepSpaces) {
                    append(spaces);
                }
                append(word);
            } else {
                if (!current.isEmpty()) {
                    emit();
                }
                if (wordWidth <= available) {
                    append(word);
                } else {
                    breakWord();
                }
            }
            spaces.clear();
            spacesWidth = 0;
            word.clear();
            wordWidth = 0;
        }

        // слово шире строки режется по символам (суррогатные пары не разрываются)
        private void breakWord() {
            for (Piece piece : word) {
                String text = piece.text();
                int index = 0;
                while (index < text.length()) {
                    int next = text.offsetByCodePoints(index, 1);
                    Piece glyph = piece(text.substring(index, next), piece.flags());
                    if (currentWidth + glyph.width() > available && !current.isEmpty()) {
                        emit();
                    }
                    append(List.of(glyph));
                    index = next;
                }
            }
        }

        private void append(List<Piece> pieces) {
            for (Piece piece : pieces) {
                current.add(piece);
                currentWidth += piece.width();
            }
        }

        // соседние куски одного начертания склеиваются в один кусок для рисования
        private void emit() {
            List<Run> runs = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            int flags = -1;
            float x = indent;
            for (Piece piece : current) {
                if (piece.flags() != flags && !text.isEmpty()) {
                    x = addRun(runs, text.toString(), flags, x);
                    text.setLength(0);
                }
                flags = piece.flags();
                text.append(piece.text());
            }
            if (!text.isEmpty()) {
                x = addRun(runs, text.toString(), flags, x);
            }
            boolean italicEnd = !runs.isEmpty() && runs.get(runs.size() - 1).has(MessageFormat.ITALIC);
            lines.add(new Line(kind, List.copyOf(runs), x + (italicEnd ? ITALIC_OVERHANG : 0), lineHeight(kind)));
            current.clear();
            currentWidth = 0;
        }

        private float addRun(List<Run> runs, String text, int flags, float x) {
            TextStyle style = style(kind, flags);
            float width = Draw.preciseWidth(text, style);
            runs.add(new Run(text, flags, style, x, width));
            return x + width;
        }

        private Piece piece(String text, int flags) {
            return new Piece(text, flags, Draw.preciseWidth(text, style(kind, flags)));
        }
    }
}
