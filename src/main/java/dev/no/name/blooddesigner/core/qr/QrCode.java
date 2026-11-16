package dev.no.name.blooddesigner.core.qr;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * QR-код для короткого текста: байтовый режим, коррекция M, версии 1–6 (до 106 байт), маска 0.
 * Своя реализация по ISO/IEC 18004, чтобы не тащить библиотеку ради отпечатка ключей.
 * modules()[y][x] == true — тёмный модуль
 */
public final class QrCode {
    private static final int MAX_VERSION = 6;
    // по версиям 1..6 для уровня M: всего кодовых слов, блоков, слов коррекции на блок
    private static final int[] TOTAL_CODEWORDS = {0, 26, 44, 70, 100, 134, 172};
    private static final int[] BLOCKS = {0, 1, 1, 1, 2, 2, 4};
    private static final int[] ECC_PER_BLOCK = {0, 10, 16, 26, 18, 24, 16};
    // уровень M в поле формата — 00, маска — 000
    private static final int FORMAT_DATA = 0;

    private final int size;
    private final boolean[][] modules;
    private final boolean[][] reserved;

    private QrCode(int version) {
        size = version * 4 + 17;
        modules = new boolean[size][size];
        reserved = new boolean[size][size];
    }

    public int size() {
        return size;
    }

    public boolean dark(int x, int y) {
        return modules[y][x];
    }

    public static QrCode encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        for (int version = 1; version <= MAX_VERSION; version++) {
            int capacity = dataCodewords(version);
            // режим (4 бита) + длина (8 бит) + данные
            if (4 + 8 + data.length * 8 <= capacity * 8) {
                QrCode code = new QrCode(version);
                code.drawFunctionPatterns(version);
                code.drawCodewords(addEcc(version, dataBits(data, capacity)));
                code.applyMask();
                code.drawFormat();
                return code;
            }
        }
        throw new IllegalArgumentException("текст длиннее " + (dataCodewords(MAX_VERSION) - 2) + " байт");
    }

    private static int dataCodewords(int version) {
        return TOTAL_CODEWORDS[version] - BLOCKS[version] * ECC_PER_BLOCK[version];
    }

    // режим 0100, длина, данные, терминатор и заполнение 0xEC 0x11
    private static byte[] dataBits(byte[] data, int capacity) {
        BitBuffer bits = new BitBuffer();
        bits.append(0b0100, 4);
        bits.append(data.length, 8);
        for (byte value : data) {
            bits.append(value & 0xFF, 8);
        }
        bits.append(0, Math.min(4, capacity * 8 - bits.length()));
        bits.append(0, (8 - bits.length() % 8) % 8);
        byte[] result = new byte[capacity];
        byte[] written = bits.toBytes();
        System.arraycopy(written, 0, result, 0, written.length);
        for (int i = written.length, pad = 0; i < capacity; i++, pad ^= 1) {
            result[i] = (byte) (pad == 0 ? 0xEC : 0x11);
        }
        return result;
    }

    // разбивка на блоки, коррекция Рида — Соломона и чередование слов по блокам
    private static byte[] addEcc(int version, byte[] data) {
        int blocks = BLOCKS[version];
        int ecc = ECC_PER_BLOCK[version];
        int total = TOTAL_CODEWORDS[version];
        int shortBlocks = blocks - total % blocks;
        int shortLength = total / blocks;
        byte[] divisor = ReedSolomon.divisor(ecc);
        byte[][] parts = new byte[blocks][];
        for (int i = 0, offset = 0; i < blocks; i++) {
            int dataLength = shortLength - ecc + (i < shortBlocks ? 0 : 1);
            byte[] block = Arrays.copyOfRange(data, offset, offset + dataLength);
            offset += dataLength;
            byte[] remainder = ReedSolomon.remainder(block, divisor);
            byte[] full = new byte[shortLength + 1];
            System.arraycopy(block, 0, full, 0, dataLength);
            // у коротких блоков на месте недостающего слова дыра — её пропустим при чередовании
            System.arraycopy(remainder, 0, full, shortLength + 1 - ecc, ecc);
            parts[i] = full;
        }
        byte[] result = new byte[total];
        int index = 0;
        for (int column = 0; column < shortLength + 1; column++) {
            for (int i = 0; i < blocks; i++) {
                if (column != shortLength - ecc || i >= shortBlocks) {
                    result[index++] = parts[i][column];
                }
            }
        }
        return result;
    }

    private void drawFunctionPatterns(int version) {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        if (version >= 2) {
            int position = size - 7;
            drawAlignment(position, position);
        }
        // место под формат резервируется сейчас, рисуется после маски
        drawFormatBits(0);
        // тёмный модуль у нижнего левого искателя
        setFunction(8, size - 8, true);
    }

    private void drawFinder(int centerX, int centerY) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = centerX + dx;
                int y = centerY + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) {
                    int distance = Math.max(Math.abs(dx), Math.abs(dy));
                    setFunction(x, y, distance != 2 && distance != 4);
                }
            }
        }
    }

    private void drawAlignment(int centerX, int centerY) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunction(centerX + dx, centerY + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    // данные змейкой по парам столбцов снизу вверх и обратно, мимо служебных модулей
    private void drawCodewords(byte[] codewords) {
        int bit = 0;
        int totalBits = codewords.length * 8;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vertical = 0; vertical < size; vertical++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vertical : vertical;
                    if (!reserved[y][x] && bit < totalBits) {
                        modules[y][x] = ((codewords[bit >>> 3] >>> (7 - (bit & 7))) & 1) != 0;
                        bit++;
                    }
                }
            }
        }
    }

    // маска 0: инвертируются модули данных, где (x + y) чётное
    private void applyMask() {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!reserved[y][x] && (x + y) % 2 == 0) {
                    modules[y][x] = !modules[y][x];
                }
            }
        }
    }

    private void drawFormat() {
        drawFormatBits(FORMAT_DATA);
    }

    // 5 бит формата + 10 бит БЧХ, XOR с 101010000010010, две копии
    private void drawFormatBits(int data) {
        int remainder = data;
        for (int i = 0; i < 10; i++) {
            remainder = (remainder << 1) ^ ((remainder >>> 9) * 0x537);
        }
        int bits = (data << 10 | remainder) ^ 0x5412;
        for (int i = 0; i <= 5; i++) {
            setFunction(8, i, bit(bits, i));
        }
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) {
            setFunction(14 - i, 8, bit(bits, i));
        }
        for (int i = 0; i < 8; i++) {
            setFunction(size - 1 - i, 8, bit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            setFunction(8, size - 15 + i, bit(bits, i));
        }
        setFunction(8, size - 8, true);
    }

    private static boolean bit(int value, int index) {
        return ((value >>> index) & 1) != 0;
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        reserved[y][x] = true;
    }

    private static final class BitBuffer {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        private int current;
        private int length;

        void append(int value, int count) {
            for (int i = count - 1; i >= 0; i--) {
                current = current << 1 | ((value >>> i) & 1);
                length++;
                if (length % 8 == 0) {
                    out.write(current);
                    current = 0;
                }
            }
        }

        int length() {
            return length;
        }

        byte[] toBytes() {
            return out.toByteArray();
        }
    }

    // арифметика в GF(256) с порождающим многочленом 0x11D
    private static final class ReedSolomon {
        private ReedSolomon() {
        }

        static byte[] divisor(int degree) {
            byte[] result = new byte[degree];
            result[degree - 1] = 1;
            int root = 1;
            for (int i = 0; i < degree; i++) {
                for (int j = 0; j < degree; j++) {
                    result[j] = (byte) multiply(result[j] & 0xFF, root);
                    if (j + 1 < degree) {
                        result[j] ^= result[j + 1];
                    }
                }
                root = multiply(root, 0x02);
            }
            return result;
        }

        static byte[] remainder(byte[] data, byte[] divisor) {
            byte[] result = new byte[divisor.length];
            for (byte value : data) {
                int factor = (value ^ result[0]) & 0xFF;
                System.arraycopy(result, 1, result, 0, result.length - 1);
                result[result.length - 1] = 0;
                for (int i = 0; i < result.length; i++) {
                    result[i] ^= (byte) multiply(divisor[i] & 0xFF, factor);
                }
            }
            return result;
        }

        private static int multiply(int x, int y) {
            int z = 0;
            for (int i = 7; i >= 0; i--) {
                z = (z << 1) ^ ((z >>> 7) * 0x11D);
                z ^= ((y >>> i) & 1) * x;
            }
            return z;
        }
    }
}
