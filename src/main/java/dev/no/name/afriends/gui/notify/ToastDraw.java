package dev.no.name.afriends.gui.notify;

/**
 * Рисование одного тоста: версия Minecraft даёт свой слой HUD (GuiGraphics на 1.21.11,
 * GuiGraphicsExtractor на 26.x), а раскладка и затухание — общие в {@link MessageToasts}.
 * Поэтому здесь только примитивы, которые есть в обоих слоях: заливка и строка текста.
 */
public interface ToastDraw {
    /** Ширина экрана в пикселях интерфейса. */
    int screenWidth();

    /** Ширина строки в пикселях текущего шрифта. */
    int textWidth(String text);

    /** Залить прямоугольник цветом ARGB (альфа смешивается). */
    void box(int x, int y, int width, int height, int argb);

    /** Нарисовать строку без тени цветом ARGB. */
    void line(String text, int x, int y, int argb);
}
