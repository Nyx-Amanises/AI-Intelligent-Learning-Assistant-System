package com.aiassistant.learning.common.util;

import java.awt.geom.AffineTransform;

/** 只读取 JPEG APP1 中 TIFF IFD0 的方向标签，不遍历任意偏移或解码其他元数据。 */
public final class JpegExifOrientation {
    private JpegExifOrientation() {}

    public static int read(byte[] bytes) {
        if (bytes.length < 2 || unsigned(bytes[0]) != 0xff || unsigned(bytes[1]) != 0xd8) return 1;
        int cursor = 2;
        while (cursor < bytes.length) {
            if (unsigned(bytes[cursor++]) != 0xff) return 1;
            while (cursor < bytes.length && unsigned(bytes[cursor]) == 0xff) cursor++;
            if (cursor >= bytes.length) return 1;
            int marker = unsigned(bytes[cursor++]);
            // 不在压缩像素流中搜索 EXIF，也不跨越 JPEG 的结束标记。
            if (marker == 0xda || marker == 0xd9 || marker == 0) return 1;
            if (marker == 0x01 || marker == 0xd8 || (marker >= 0xd0 && marker <= 0xd7)) continue;
            if (bytes.length - cursor < 2) return 1;
            int length = readShort(bytes, cursor, false);
            if (length < 2 || length > bytes.length - cursor) return 1;
            int end = cursor + length;
            if (marker == 0xe1) {
                int orientation = readExif(bytes, cursor + 2, end);
                if (orientation >= 1 && orientation <= 8) return orientation;
            }
            cursor = end;
        }
        return 1;
    }

    private static int readExif(byte[] bytes, int start, int end) {
        if (end - start < 14 || bytes[start] != 'E' || bytes[start + 1] != 'x'
                || bytes[start + 2] != 'i' || bytes[start + 3] != 'f'
                || bytes[start + 4] != 0 || bytes[start + 5] != 0) return 0;
        int tiff = start + 6;
        boolean little = bytes[tiff] == 'I' && bytes[tiff + 1] == 'I';
        if (!little && !(bytes[tiff] == 'M' && bytes[tiff + 1] == 'M')) return 0;
        if (readShort(bytes, tiff + 2, little) != 42) return 0;
        long offset = readInt(bytes, tiff + 4, little);
        // TIFF 偏移是无符号32位；先以long验证再转int，不能让整数溢出穿过边界。
        if (offset < 8 || offset > end - tiff - 2L) return 0;
        int directory = tiff + (int) offset;
        int count = readShort(bytes, directory, little);
        int entries = directory + 2;
        if (count > (end - entries) / 12) return 0;
        for (int index = 0; index < count; index++) {
            int entry = entries + index * 12;
            if (readShort(bytes, entry, little) != 0x0112
                    || readShort(bytes, entry + 2, little) != 3
                    || readInt(bytes, entry + 4, little) != 1) continue;
            int orientation = readShort(bytes, entry + 8, little);
            if (orientation >= 1 && orientation <= 8) return orientation;
        }
        return 0;
    }

    /** 以图像边界坐标构造1–8全部方向，调用者先按交换后的宽高分配输出画布。 */
    public static AffineTransform transform(int orientation, int width, int height) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
    }

    private static int unsigned(byte value) { return Byte.toUnsignedInt(value); }

    private static int readShort(byte[] bytes, int offset, boolean little) {
        return little ? unsigned(bytes[offset]) | unsigned(bytes[offset + 1]) << 8
                : unsigned(bytes[offset]) << 8 | unsigned(bytes[offset + 1]);
    }

    private static long readInt(byte[] bytes, int offset, boolean little) {
        long first = readShort(bytes, offset, little);
        long second = readShort(bytes, offset + 2, little);
        return little ? first | second << 16 : first << 16 | second;
    }
}
