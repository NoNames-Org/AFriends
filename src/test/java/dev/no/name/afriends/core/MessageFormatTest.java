package dev.no.name.afriends.core;

import dev.no.name.afriends.core.MessageFormat.Kind;
import dev.no.name.afriends.core.MessageFormat.Line;
import dev.no.name.afriends.core.MessageFormat.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.no.name.afriends.core.MessageFormat.BOLD;
import static dev.no.name.afriends.core.MessageFormat.CODE;
import static dev.no.name.afriends.core.MessageFormat.ITALIC;
import static dev.no.name.afriends.core.MessageFormat.STRIKE;
import static dev.no.name.afriends.core.MessageFormat.UNDERLINE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageFormatTest {
    private static List<Span> spans(String text) {
        List<Line> lines = MessageFormat.parse(text);
        assertEquals(1, lines.size());
        return lines.get(0).spans();
    }

    private static Span span(String text, int flags) {
        return new Span(text, flags);
    }

    private static List<Span> plainSpan(String text) {
        return List.of(span(text, 0));
    }

    @Test
    @DisplayName("обычный текст — один кусок без начертания")
    void plainText() {
        assertEquals(plainSpan("привет, мир"), spans("привет, мир"));
    }

    @Test
    @DisplayName("пустой текст и null — одна пустая строка")
    void empty() {
        assertEquals(List.of(new Line(Kind.NORMAL, List.of())), MessageFormat.parse(""));
        assertEquals(List.of(new Line(Kind.NORMAL, List.of())), MessageFormat.parse(null));
        assertEquals("", MessageFormat.plain(null));
    }

    @Test
    @DisplayName("жирный, курсив, подчёркнутый, зачёркнутый и код")
    void basicStyles() {
        assertEquals(List.of(span("a ", 0), span("b", BOLD), span(" c", 0)), spans("a **b** c"));
        assertEquals(List.of(span("b", ITALIC)), spans("*b*"));
        assertEquals(List.of(span("b", UNDERLINE)), spans("__b__"));
        assertEquals(List.of(span("b", STRIKE)), spans("~~b~~"));
        assertEquals(List.of(span("x ", 0), span("b", CODE)), spans("x `b`"));
    }

    @Test
    @DisplayName("маркеры можно вкладывать друг в друга")
    void nested() {
        assertEquals(List.of(span("a ", BOLD), span("b", BOLD | ITALIC), span(" c", BOLD)), spans("**a *b* c**"));
        assertEquals(List.of(span("x", BOLD | UNDERLINE | STRIKE)), spans("**__~~x~~__**"));
        assertEquals(List.of(span("u", UNDERLINE | ITALIC), span("v", UNDERLINE)), spans("__*u*v__"));
    }

    @Test
    @DisplayName("*** — жирный курсив, закрывается в обратном порядке")
    void tripleStars() {
        assertEquals(List.of(span("x", BOLD | ITALIC)), spans("***x***"));
        assertEquals(List.of(span("a", BOLD | ITALIC), span("b", BOLD)), spans("***a*b**"));
    }

    @Test
    @DisplayName("незакрытые маркеры остаются текстом")
    void unclosed() {
        assertEquals(plainSpan("**a"), spans("**a"));
        assertEquals(plainSpan("a*"), spans("a*"));
        assertEquals(plainSpan("__x"), spans("__x"));
        assertEquals(plainSpan("~~x"), spans("~~x"));
        assertEquals(plainSpan("`x"), spans("`x"));
    }

    @Test
    @DisplayName("незакрытый внутренний маркер становится текстом, внешний работает")
    void unclosedInner() {
        assertEquals(List.of(span("a *b", BOLD), span(" c*", 0)), spans("**a *b** c*"));
    }

    @Test
    @DisplayName("одни маркеры без содержимого — текст")
    void onlyMarkers() {
        assertEquals(plainSpan("****"), spans("****"));
        assertEquals(plainSpan("**"), spans("**"));
        assertEquals(plainSpan("*"), spans("*"));
        assertEquals(plainSpan("____"), spans("____"));
        assertEquals(plainSpan("``"), spans("``"));
        assertEquals(plainSpan("~~~~"), spans("~~~~"));
    }

    @Test
    @DisplayName("одиночные _ и ~ — просто символы")
    void singleUnderscoreAndTilde() {
        assertEquals(plainSpan("snake_case_name ~5"), spans("snake_case_name ~5"));
    }

    @Test
    @DisplayName("маркер у пробела не открывает и не закрывает: 2 * 3 * 4")
    void spacedStars() {
        assertEquals(plainSpan("2 * 3 * 4"), spans("2 * 3 * 4"));
        assertEquals(plainSpan("** a **"), spans("** a **"));
    }

    @Test
    @DisplayName("внутри кода разметка не работает")
    void codeKeepsMarkup() {
        assertEquals(List.of(span("**a** *b* \\*", CODE)), spans("`**a** *b* \\*`"));
        assertEquals(List.of(span("a ", 0), span("x*y", CODE), span(" b", 0)), spans("a `x*y` b"));
    }

    @Test
    @DisplayName("код внутри жирного сохраняет жирный")
    void codeInsideBold() {
        assertEquals(List.of(span("a ", BOLD), span("c", BOLD | CODE), span(" b", BOLD)), spans("**a `c` b**"));
    }

    @Test
    @DisplayName("звёздочки внутри кода не закрывают внешний курсив")
    void codeStarsDoNotClose() {
        assertEquals(List.of(span("a ", ITALIC), span("*", ITALIC | CODE), span(" b", ITALIC)), spans("*a `*` b*"));
    }

    @Test
    @DisplayName("обратный слэш экранирует маркеры")
    void escapes() {
        assertEquals(plainSpan("*a*"), spans("\\*a\\*"));
        assertEquals(plainSpan("**not bold**"), spans("\\*\\*not bold\\*\\*"));
        assertEquals(plainSpan("`x`"), spans("\\`x\\`"));
        assertEquals(plainSpan("a\\b"), spans("a\\\\b"));
        assertEquals(plainSpan("C:\\dir"), spans("C:\\dir"));
        assertEquals(plainSpan("end\\"), spans("end\\"));
        assertEquals(List.of(span("a*b", ITALIC)), spans("*a\\*b*"));
    }

    @Test
    @DisplayName("заголовки и цитаты по началу строки")
    void lineKinds() {
        List<Line> lines = MessageFormat.parse("# Заголовок\n## Подзаголовок\n> цитата\n>\nтекст\n### три\n#без пробела");
        assertEquals(List.of(Kind.HEADING, Kind.SUBHEADING, Kind.QUOTE, Kind.QUOTE, Kind.NORMAL, Kind.NORMAL, Kind.NORMAL),
                lines.stream().map(Line::kind).toList());
        assertEquals("Заголовок", lines.get(0).text());
        assertEquals("Подзаголовок", lines.get(1).text());
        assertEquals("цитата", lines.get(2).text());
        assertEquals("", lines.get(3).text());
        assertEquals("### три", lines.get(5).text());
        assertEquals("#без пробела", lines.get(6).text());
    }

    @Test
    @DisplayName("экранированные # и > в начале строки — обычный текст")
    void escapedLinePrefix() {
        List<Line> lines = MessageFormat.parse("\\# не заголовок\n\\> не цитата");
        assertEquals(Kind.NORMAL, lines.get(0).kind());
        assertEquals("# не заголовок", lines.get(0).text());
        assertEquals(Kind.NORMAL, lines.get(1).kind());
        assertEquals("> не цитата", lines.get(1).text());
    }

    @Test
    @DisplayName("разметка внутри заголовка и цитаты")
    void inlineInsideLineKinds() {
        Line heading = MessageFormat.parse("# **Важно** и *срочно*").get(0);
        assertEquals(List.of(span("Важно", BOLD), span(" и ", 0), span("срочно", ITALIC)), heading.spans());
        Line quote = MessageFormat.parse("> ~~old~~").get(0);
        assertEquals(List.of(span("old", STRIKE)), quote.spans());
    }

    @Test
    @DisplayName("маркеры не переносятся через строку")
    void markersDoNotSpanLines() {
        List<Line> lines = MessageFormat.parse("**a\nb**");
        assertEquals(plainSpan("**a"), lines.get(0).spans());
        assertEquals(plainSpan("b**"), lines.get(1).spans());
    }

    @Test
    @DisplayName("кириллица и эмодзи с суррогатными парами не ломаются")
    void unicode() {
        assertEquals(List.of(span("Привет 😀", BOLD), span(" ", 0), span("мир 👍🏽", ITALIC)), spans("**Привет 😀** *мир 👍🏽*"));
        assertEquals(plainSpan("\\😀"), spans("\\😀"));
        assertEquals(List.of(span("😀", CODE)), spans("`😀`"));
    }

    @Test
    @DisplayName("пустые строки сохраняются")
    void blankLines() {
        List<Line> lines = MessageFormat.parse("a\n\nb\n");
        assertEquals(4, lines.size());
        assertTrue(lines.get(1).spans().isEmpty());
        assertTrue(lines.get(3).spans().isEmpty());
    }

    @Test
    @DisplayName("plain убирает разметку и префиксы строк, переводы строк остаются")
    void plain() {
        assertEquals("Заголовок\nцитата\nжирный и код **x**", MessageFormat.plain("# Заголовок\n> цитата\n**жирный** и `код **x**`"));
        assertEquals("**не закрыт", MessageFormat.plain("**не закрыт"));
        assertEquals("*звёздочка*", MessageFormat.plain("\\*звёздочка\\*"));
        assertEquals("обычный текст", MessageFormat.plain("обычный текст"));
    }

    @Test
    @DisplayName("пробелы по краям сохраняются")
    void keepsSpaces() {
        assertEquals(List.of(span("  a ", 0), span("b", BOLD), span("  ", 0)), spans("  a **b**  "));
    }

    @Test
    @DisplayName("Span.has проверяет флаг")
    void spanHas() {
        Span span = span("x", BOLD | STRIKE);
        assertTrue(span.has(BOLD));
        assertTrue(span.has(STRIKE));
        assertEquals(false, span.has(ITALIC));
    }
}
