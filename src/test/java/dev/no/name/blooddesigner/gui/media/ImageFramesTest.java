package dev.no.name.blooddesigner.gui.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageFramesTest {
    @Test
    @DisplayName("большая картинка уменьшается по длинной стороне, пропорции сохраняются")
    void scalesDown(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("big.png");
        ImageIO.write(new BufferedImage(1440, 720, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        ImageFrames frames = ImageFrames.read(file, false);
        assertEquals(ImageFrames.MAX_SIDE, frames.width());
        assertEquals(ImageFrames.MAX_SIDE / 2, frames.height());
        assertEquals(1, frames.frames().size());
    }

    @Test
    @DisplayName("гифка раскладывается на кадры с задержками и крутится по кругу")
    void gif(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("anim.gif");
        writeGif(file, new Color[] {Color.RED, Color.GREEN, Color.BLUE}, 10);
        ImageFrames frames = ImageFrames.read(file, true);
        assertEquals(3, frames.frames().size());
        assertEquals(300, frames.duration());
        assertEquals(0, frames.frameAt(50));
        assertEquals(1, frames.frameAt(150));
        assertEquals(2, frames.frameAt(250));
        assertEquals(0, frames.frameAt(310));
        assertEquals(Color.GREEN.getRGB(), frames.frames().get(1).getRGB(5, 5));
    }

    // GIF на 3 кадра 16×16 по delay сотых секунды
    private static void writeGif(Path file, Color[] colors, int delay) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (Color color : colors) {
                BufferedImage frame = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = frame.createGraphics();
                g.setColor(color);
                g.fillRect(0, 0, 16, 16);
                g.dispose();
                ImageWriteParam param = writer.getDefaultWriteParam();
                IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame), param);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = new IIOMetadataNode("GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", String.valueOf(delay));
                control.setAttribute("transparentColorIndex", "0");
                root.appendChild(control);
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }
}
