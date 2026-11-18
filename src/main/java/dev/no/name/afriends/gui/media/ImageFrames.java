package dev.no.name.afriends.gui.media;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Картинка, разложенная на кадры для показа в чате: у PNG и JPG кадр один, у GIF — все, собранные целиком
 * (в GIF кадр часто лишь заплатка поверх прошлого). Всё уменьшено, чтобы длинная сторона была не больше MAX_SIDE:
 * в чате больше не нужно, а память видеокарты не резиновая. Для просмотра во всё окно картинка читается крупнее —
 * до FULL_SIDE. Работает без потока игры
 */
public record ImageFrames(int width, int height, List<BufferedImage> frames, int[] delays) {
    public static final int MAX_SIDE = 360;
    public static final int FULL_SIDE = 2048;
    private static final int MAX_FRAMES = 150;
    private static final int MIN_DELAY_MS = 20;

    public static ImageFrames read(Path file, boolean animated) throws IOException {
        return read(file, animated, MAX_SIDE);
    }

    // maxSide — предел длинной стороны после уменьшения; меньшие картинки не растягиваются
    public static ImageFrames read(Path file, boolean animated, int maxSide) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("не картинка");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                return animated ? gif(reader, maxSide) : still(reader, maxSide);
            } finally {
                reader.dispose();
            }
        }
    }

    private static ImageFrames still(ImageReader reader, int maxSide) throws IOException {
        BufferedImage image = reader.read(0);
        BufferedImage scaled = scale(image, maxSide);
        return new ImageFrames(scaled.getWidth(), scaled.getHeight(), List.of(scaled), new int[] {0});
    }

    // GIF: кадр рисуется поверх холста со своим сдвигом, потом холст чистится по правилу disposal
    private static ImageFrames gif(ImageReader reader, int maxSide) throws IOException {
        int count = Math.min(reader.getNumImages(true), MAX_FRAMES);
        int canvasWidth = reader.getWidth(0);
        int canvasHeight = reader.getHeight(0);
        IIOMetadata stream = reader.getStreamMetadata();
        if (stream != null) {
            Node screen = child(stream.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
            if (screen != null) {
                canvasWidth = Math.max(canvasWidth, intAttr(screen, "logicalScreenWidth", canvasWidth));
                canvasHeight = Math.max(canvasHeight, intAttr(screen, "logicalScreenHeight", canvasHeight));
            }
        }
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        List<BufferedImage> frames = new ArrayList<>();
        int[] delays = new int[count];
        for (int i = 0; i < count; i++) {
            BufferedImage frame = reader.read(i);
            Node tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
            Node descriptor = child(tree, "ImageDescriptor");
            Node control = child(tree, "GraphicControlExtension");
            int x = descriptor == null ? 0 : intAttr(descriptor, "imageLeftPosition", 0);
            int y = descriptor == null ? 0 : intAttr(descriptor, "imageTopPosition", 0);
            String disposal = control == null ? "none" : attr(control, "disposalMethod", "none");
            int delay = control == null ? 10 : intAttr(control, "delayTime", 10);
            delays[i] = Math.max(MIN_DELAY_MS, delay * 10);

            BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
            Graphics2D g = canvas.createGraphics();
            g.drawImage(frame, x, y, null);
            g.dispose();
            frames.add(scale(canvas, maxSide));

            if ("restoreToBackgroundColor".equals(disposal)) {
                Graphics2D clear = canvas.createGraphics();
                clear.setComposite(AlphaComposite.Clear);
                clear.fillRect(x, y, frame.getWidth(), frame.getHeight());
                clear.dispose();
            } else if (before != null) {
                canvas = before;
            }
        }
        BufferedImage first = frames.get(0);
        return new ImageFrames(first.getWidth(), first.getHeight(), List.copyOf(frames), delays);
    }

    public int duration() {
        int total = 0;
        for (int delay : delays) {
            total += delay;
        }
        return total;
    }

    // какой кадр показывать в момент time (мс): гифка крутится по кругу
    public int frameAt(long time) {
        if (frames.size() == 1) {
            return 0;
        }
        long position = time % Math.max(1, duration());
        for (int i = 0; i < delays.length; i++) {
            position -= delays[i];
            if (position < 0) {
                return i;
            }
        }
        return frames.size() - 1;
    }

    private static BufferedImage scale(BufferedImage source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        float factor = Math.min(1f, maxSide / (float) Math.max(width, height));
        int targetWidth = Math.max(1, Math.round(width * factor));
        int targetHeight = Math.max(1, Math.round(height * factor));
        // билинейный шаг берёт лишь 4 соседних пикселя: при сжатии сильнее чем вдвое теряются детали и лезет лесенка,
        // поэтому сначала уменьшаем пополам, пока до цели не останется меньше двух раз
        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return resize(current, targetWidth, targetHeight);
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return result;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static Node child(Node parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    private static String attr(Node node, String name, String fallback) {
        NamedNodeMap attributes = node.getAttributes();
        Node value = attributes == null ? null : attributes.getNamedItem(name);
        return value == null ? fallback : value.getNodeValue();
    }

    private static int intAttr(Node node, String name, int fallback) {
        try {
            return Integer.parseInt(attr(node, name, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
