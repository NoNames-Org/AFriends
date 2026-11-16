package dev.no.name.blooddesigner.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MessageContentTest {
    private static final MessageImage A = image("aa", "image/png");
    private static final MessageImage B = image("bb", "image/gif");

    private static MessageImage image(String tag, String mime) {
        return new MessageImage(tag.repeat(16), "", mime, 10, 10, 10, tag.repeat(32));
    }

    @Test
    @DisplayName("метка [img] ставит картинку на своё место")
    void tokenPlacesImage() {
        List<MessageContent.Block> blocks = MessageContent.blocks("BloodDesigner 1.1.6!\n[img]\nновое", List.of(A));
        assertEquals(new MessageContent.Text("BloodDesigner 1.1.6!"), blocks.get(0));
        assertEquals(new MessageContent.Image(A), blocks.get(1));
        assertEquals(new MessageContent.Text("новое"), blocks.get(2));
    }

    @Test
    @DisplayName("картинки без метки — сверху, как подпись под фото у игрока")
    void imagesWithoutTokenOnTop() {
        List<MessageContent.Block> blocks = MessageContent.blocks("подпись", List.of(A));
        assertInstanceOf(MessageContent.Image.class, blocks.get(0));
        assertEquals(new MessageContent.Text("подпись"), blocks.get(1));
    }

    @Test
    @DisplayName("меток меньше, чем картинок: лишние сверху; меток больше — пустые пропускаются")
    void mismatch() {
        List<MessageContent.Block> blocks = MessageContent.blocks("[img] текст", List.of(A, B));
        assertEquals(List.of(new MessageContent.Image(B), new MessageContent.Image(A), new MessageContent.Text(" текст")), blocks);
        assertEquals(List.of(new MessageContent.Text("a "), new MessageContent.Text(" b")),
                MessageContent.blocks("a [img] b", List.of()));
    }

    @Test
    @DisplayName("метка [img-id] ставит свою картинку, а не очередную; простые [img] берут оставшиеся")
    void tokenWithId() {
        assertEquals(List.of(new MessageContent.Image(B), new MessageContent.Text("между"), new MessageContent.Image(A)),
                MessageContent.blocks("[img-bbbbbb]\nмежду\n[img]", List.of(A, B)));
        assertEquals(List.of(new MessageContent.Image(A), new MessageContent.Text("текст")),
                MessageContent.blocks("[IMG-CCCC]текст", List.of(A)));
        assertEquals("a b", MessageContent.plain("a [img-aaaaaa] b"));
    }

    @Test
    @DisplayName("подпись в списке чатов: без меток, а без текста — «Изображение» или «Гифка»")
    void preview() {
        assertEquals("Гифка", MessageContent.preview(new PrivateMessage(false, "[img]", 1, null, List.of(B), null, null)));
        assertEquals("Изображение", MessageContent.preview(new PrivateMessage(false, "", 1, null, List.of(A), null, null)));
        assertEquals("привет мир", MessageContent.preview(new PrivateMessage(false, "привет\n[img] мир", 1, null, List.of(A), null, null)));
    }
}
