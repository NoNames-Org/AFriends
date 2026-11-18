package dev.no.name.afriends.net.tunnel;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Сообщение туннеля — JSON, порезанный на куски. Каждый кусок шифруется отдельным кадром Noise:
//   u8 флаг (1 = будет продолжение) || u16 длина куска || кусок || нули до кратности 256
// Дополнение прячет точную длину сообщения
final class Frames {
    private static final int HEADER_BYTES = 3;
    private static final int PAD_BLOCK = 256;
    private static final int MAX_CHUNK = 60_000;

    private Frames() {
    }

    static List<byte[]> encode(String json) {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        List<byte[]> frames = new ArrayList<>();
        int offset = 0;
        do {
            int length = Math.min(MAX_CHUNK, data.length - offset);
            int size = (HEADER_BYTES + length + PAD_BLOCK - 1) / PAD_BLOCK * PAD_BLOCK;
            byte[] frame = new byte[size];
            frame[0] = (byte) (offset + length < data.length ? 1 : 0);
            frame[1] = (byte) (length >>> 8);
            frame[2] = (byte) length;
            System.arraycopy(data, offset, frame, HEADER_BYTES, length);
            frames.add(frame);
            offset += length;
        } while (offset < data.length);
        return frames;
    }

    // собирает сообщение из кусков
    static final class Assembler {
        private final int maxMessageBytes;
        private final ByteArrayOutputStream chunks = new ByteArrayOutputStream();

        Assembler(int maxMessageBytes) {
            this.maxMessageBytes = maxMessageBytes;
        }

        // готовый JSON, когда пришёл последний кусок, иначе null
        String push(byte[] frame) {
            if (frame.length < HEADER_BYTES) {
                throw new IllegalArgumentException("кадр короче заголовка");
            }
            int more = frame[0] & 0xFF;
            int length = ((frame[1] & 0xFF) << 8) | (frame[2] & 0xFF);
            if (more > 1 || HEADER_BYTES + length > frame.length || chunks.size() + length > maxMessageBytes) {
                throw new IllegalArgumentException("битый или слишком большой кадр");
            }
            chunks.write(frame, HEADER_BYTES, length);
            if (more == 1) {
                return null;
            }
            String json = chunks.toString(StandardCharsets.UTF_8);
            chunks.reset();
            return json;
        }
    }
}
