package dev.no.name.afriends.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Разметка текста сообщения, как в Telegram. Разметка — часть обычного текста, поэтому старые клиенты показывают её как есть.
 * Строки: «# » заголовок, «## » подзаголовок, «> » цитата. Внутри строки: **жирный**, *курсив*, __подчёркнутый__,
 * ~~зачёркнутый~~, `код` (внутри кода разметки нет). Незакрытый маркер — обычный текст, \* — сама звёздочка.
 * Маркеры не переносятся через строку
 */
public final class MessageFormat {
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int UNDERLINE = 4;
    public static final int STRIKE = 8;
    public static final int CODE = 16;

    private static final String ESCAPABLE = "\\*_~`#>";

    public enum Kind { NORMAL, HEADING, SUBHEADING, QUOTE }

    // кусок текста одного начертания: flags — набор BOLD, ITALIC, ...
    public record Span(String text, int flags) {
        public boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    public record Line(Kind kind, List<Span> spans) {
        public String text() {
            StringBuilder text = new StringBuilder();
            for (Span span : spans) {
                text.append(span.text());
            }
            return text.toString();
        }
    }

    private MessageFormat() {
    }

    public static List<Line> parse(String text) {
        List<Line> lines = new ArrayList<>();
        for (String line : (text == null ? "" : text).split("\n", -1)) {
            lines.add(line(line));
        }
        return List.copyOf(lines);
    }

    // текст без разметки: для превью в списке чатов и уведомлений
    public static String plain(String text) {
        List<String> lines = new ArrayList<>();
        for (Line line : parse(text)) {
            lines.add(line.text());
        }
        return String.join("\n", lines);
    }

    private static Line line(String line) {
        if (line.startsWith("## ")) {
            return new Line(Kind.SUBHEADING, inline(line.substring(3)));
        }
        if (line.startsWith("# ")) {
            return new Line(Kind.HEADING, inline(line.substring(2)));
        }
        if (line.startsWith("> ")) {
            return new Line(Kind.QUOTE, inline(line.substring(2)));
        }
        if (line.equals(">")) {
            return new Line(Kind.QUOTE, List.of());
        }
        return new Line(Kind.NORMAL, inline(line));
    }

    // маркер разметки в разборе: пока не нашлась пара — открывающий, без пары в конце становится текстом
    private static final class Token {
        final String text;
        final int flag;
        boolean opener;
        boolean closer;

        Token(String text, int flag) {
            this.text = text;
            this.flag = flag;
        }

        boolean literal() {
            return flag == 0 || !opener && !closer;
        }
    }

    // разбор строки: маркеры подбираются в пары стеком, внутренние незакрытые при закрытии внешнего — текст
    static List<Span> inline(String line) {
        List<Token> tokens = new ArrayList<>();
        List<Token> stack = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length() && ESCAPABLE.indexOf(line.charAt(i + 1)) >= 0) {
                text.append(line.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '`') {
                int end = line.indexOf('`', i + 1);
                if (end > i + 1) {
                    flush(tokens, text);
                    Token code = new Token(line.substring(i + 1, end), CODE);
                    code.opener = true;
                    code.closer = true;
                    tokens.add(code);
                    i = end + 1;
                    continue;
                }
                text.append(c);
                i++;
                continue;
            }
            if (c == '*' || c == '_' || c == '~') {
                int end = i;
                while (end < line.length() && line.charAt(end) == c) {
                    end++;
                }
                flush(tokens, text);
                boolean canClose = i > 0 && !Character.isWhitespace(line.charAt(i - 1));
                boolean canOpen = end < line.length() && !Character.isWhitespace(line.charAt(end));
                run(tokens, stack, c, end - i, canOpen, canClose);
                i = end;
                continue;
            }
            text.append(c);
            i++;
        }
        flush(tokens, text);
        // так и не закрытые к концу строки — обычный текст
        for (Token open : stack) {
            open.opener = false;
        }
        return spans(tokens);
    }

    // серия одинаковых символов маркера: сначала закрывает открытые (самый внутренний первым), остаток открывает
    private static void run(List<Token> tokens, List<Token> stack, char c, int count, boolean canOpen, boolean canClose) {
        int left = count;
        while (left > 0) {
            int size = c == '*' ? (left >= 2 ? 2 : 1) : 2;
            if (left < size) {
                tokens.add(new Token(String.valueOf(c).repeat(left), 0));
                return;
            }
            if (canClose && close(tokens, stack, c, left)) {
                left -= width(tokens.get(tokens.size() - 1).flag);
                continue;
            }
            Token token = new Token(String.valueOf(c).repeat(size), flag(c, size));
            if (canOpen) {
                token.opener = true;
                stack.add(token);
            }
            tokens.add(token);
            left -= size;
        }
    }

    // ищет открывающий маркер, который можно закрыть этой серией; всё открытое выше него становится текстом.
    // ** закрывает прежде всего **, а не * внутри; *** — что открыто внутри, то и первым
    private static boolean close(List<Token> tokens, List<Token> stack, char c, int left) {
        int depth = find(stack, c, left, true);
        if (depth < 0) {
            depth = find(stack, c, left, false);
        }
        if (depth < 0) {
            return false;
        }
        Token open = stack.get(depth);
        // между маркерами должно быть хоть что-то: **** — просто звёздочки
        if (tokens.get(tokens.size() - 1) == open) {
            return false;
        }
        for (int above = stack.size() - 1; above > depth; above--) {
            stack.remove(above).opener = false;
        }
        stack.remove(depth);
        Token closer = new Token(open.text, open.flag);
        closer.closer = true;
        tokens.add(closer);
        return true;
    }

    private static int find(List<Token> stack, char c, int left, boolean exact) {
        for (int depth = stack.size() - 1; depth >= 0; depth--) {
            Token open = stack.get(depth);
            boolean fits = exact ? left <= 2 && open.text.length() == left : open.text.length() <= left;
            if (open.text.charAt(0) == c && fits) {
                return depth;
            }
        }
        return -1;
    }

    private static int flag(char c, int size) {
        return switch (c) {
            case '*' -> size == 2 ? BOLD : ITALIC;
            case '_' -> UNDERLINE;
            default -> STRIKE;
        };
    }

    private static int width(int flag) {
        return flag == ITALIC ? 1 : 2;
    }

    private static void flush(List<Token> tokens, StringBuilder text) {
        if (!text.isEmpty()) {
            tokens.add(new Token(text.toString(), 0));
            text.setLength(0);
        }
    }

    // маркеры с парой меняют начертание, остальное — текст; соседние куски одного начертания склеиваются
    private static List<Span> spans(List<Token> tokens) {
        List<Span> spans = new ArrayList<>();
        int flags = 0;
        for (Token token : tokens) {
            if (token.flag == CODE) {
                add(spans, token.text, flags | CODE);
            } else if (token.literal()) {
                add(spans, token.text, flags);
            } else if (token.closer) {
                flags &= ~token.flag;
            } else {
                flags |= token.flag;
            }
        }
        return List.copyOf(spans);
    }

    private static void add(List<Span> spans, String text, int flags) {
        if (text.isEmpty()) {
            return;
        }
        int last = spans.size() - 1;
        if (last >= 0 && spans.get(last).flags() == flags) {
            spans.set(last, new Span(spans.get(last).text() + text, flags));
        } else {
            spans.add(new Span(text, flags));
        }
    }
}
