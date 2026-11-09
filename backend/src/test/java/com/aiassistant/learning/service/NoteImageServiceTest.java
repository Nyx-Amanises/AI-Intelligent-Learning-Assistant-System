package com.aiassistant.learning.service;

import static org.junit.jupiter.api.Assertions.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.FileStorageProperties;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class NoteImageServiceTest {
    @TempDir Path temporary;
    private NoteImageService service;

    @BeforeEach
    void setup() {
        var storage = new FileStorageProperties();
        storage.setUploadDir(temporary.resolve("uploads").toString());
        service = new NoteImageService(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg"})
    void imagePixelsAreReencodedWithoutUntrustedFilenameOrTrailingPayload(String format) throws Exception {
        var payload = new ByteArrayOutputStream();
        payload.write(image(format, 40, 24));
        payload.write("<script>untrusted-original-metadata</script>".getBytes(StandardCharsets.UTF_8));
        var result = service.upload(7L, new MockMultipartFile("file", "../../outside." + format,
                "application/octet-stream", payload.toByteArray()));
        assertTrue(result.url().matches("/api/notes/images/[a-f0-9-]{36}\\.(png|jpg)"));
        assertEquals(40, result.width());
        assertEquals(24, result.height());
        var stored = service.read(7L, name(result.url()));
        assertEquals(format.equals("png") ? "image/png" : "image/jpeg", stored.contentType());
        assertFalse(new String(stored.bytes(), StandardCharsets.ISO_8859_1).contains("untrusted-original-metadata"));
        var decoded = ImageIO.read(new ByteArrayInputStream(stored.bytes()));
        assertEquals(40, decoded.getWidth());
        assertEquals(24, decoded.getHeight());
        assertTrue(Files.isRegularFile(temporary.resolve("uploads/notes/7").resolve(name(result.url()))));
        assertFalse(Files.exists(temporary.resolve("outside." + format)));
    }

    @Test
    void repeatedUploadsHaveDistinctNamesAndOtherUserCannotReadEitherOne() throws Exception {
        var one = service.upload(7L, file(image("png", 10, 10)));
        var two = service.upload(7L, file(image("png", 10, 10)));
        assertNotEquals(one.url(), two.url());
        assertCode(404, () -> service.read(8L, name(one.url())));
        assertCode(404, () -> service.read(8L, name(two.url())));
        assertNotNull(service.read(7L, name(one.url())));
        assertCode(401, () -> service.read(null, name(one.url())));
        assertCode(401, () -> service.upload(0L, file(new byte[]{1})));
    }

    @Test
    void largePngIsResizedAndTransparencySurvives() throws Exception {
        var result = service.upload(7L, file(image("png", 3000, 1000)));
        assertEquals(2400, result.width());
        assertEquals(800, result.height());
        var decoded = ImageIO.read(new ByteArrayInputStream(service.read(7L, name(result.url())).bytes()));
        assertEquals(0, (decoded.getRGB(0, 0) >>> 24));
    }

    @Test
    void jpegExifRotationIsAppliedAndRemovedFromStoredFile() throws Exception {
        byte[] original = image("jpeg", 80, 40);
        byte[] exif = new byte[32];
        System.arraycopy(new byte[]{'E', 'x', 'i', 'f', 0, 0}, 0, exif, 0, 6);
        ByteBuffer buffer = ByteBuffer.wrap(exif).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(6);
        buffer.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8).putShort((short) 1);
        buffer.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) 6).putShort((short) 0).putInt(0);
        var payload = new ByteArrayOutputStream();
        payload.write(original, 0, 2);
        payload.write(new byte[]{(byte) 0xff, (byte) 0xe1, 0, 34});
        payload.write(exif);
        payload.write(original, 2, original.length - 2);
        var result = service.upload(7L, file(payload.toByteArray()));
        assertEquals(40, result.width());
        assertEquals(80, result.height());
        byte[] stored = service.read(7L, name(result.url())).bytes();
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains("Exif"));
        assertEquals(80, ImageIO.read(new ByteArrayInputStream(stored)).getHeight());
    }

    @ParameterizedTest
    @ValueSource(strings = {"<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>", "not an image", "\u0089PNG", ""})
    void disguisedAndMalformedUploadsAreRejectedWithoutSaving(String value) {
        assertCode(400, () -> service.upload(7L, file(value.getBytes(StandardCharsets.UTF_8))));
        assertFalse(Files.exists(temporary.resolve("uploads/notes")));
    }

    @Test
    void otherDecodableFormatsAreNotAccepted() throws Exception {
        byte[] gif = image("gif", 10, 10);
        assertCode(400, () -> service.upload(7L, file(gif)));
    }

    @Test
    void oversizeStreamIsRejectedEvenWhenMultipartSizeLies() {
        byte[] large = new byte[5 * 1024 * 1024 + 1];
        assertCode(400, () -> service.upload(7L, file(large)));
        var lying = new MockMultipartFile("file", "small.png", "image/png", large) {
            @Override public long getSize() { return 16; }
        };
        assertCode(400, () -> service.upload(7L, lying));
        assertFalse(Files.exists(temporary.resolve("uploads/notes")));
    }

    @ParameterizedTest
    @CsvSource({"8193,1", "5000,4000"})
    void oversizedDimensionsAreRejectedFromHeaderBeforeAllocatingImage(int width, int height) throws Exception {
        byte[] bytes = image("png", 1, 1);
        ByteBuffer.wrap(bytes).putInt(16, width).putInt(20, height);
        CRC32 crc = new CRC32();
        crc.update(bytes, 12, 17);
        ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());
        var failure = assertThrows(BusinessException.class, () -> service.upload(7L, file(bytes)));
        assertEquals(400, failure.getCode());
        assertTrue(failure.getMessage().contains("1600万"));
        assertFalse(Files.exists(temporary.resolve("uploads/notes")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../other.png", "..\\other.png", "C:\\private.png", "https://other/image.png",
            "00000000-0000-0000-0000-000000000000.svg", "00000000-0000-0000-0000-000000000000.png::$DATA", "%2e%2e%2fimage.png"})
    void invalidNamesNeverResolveOutsideOwnedDirectory(String value) {
        assertCode(404, () -> service.read(7L, value));
    }

    @Test
    void absentAndUnexpectedlyOversizedStoredFilesAreUnavailable() throws Exception {
        var result = service.upload(7L, file(image("png", 10, 10)));
        assertCode(404, () -> service.read(7L, "00000000-0000-0000-0000-000000000000.png"));
        Files.write(temporary.resolve("uploads/notes/7").resolve(name(result.url())), new byte[5 * 1024 * 1024 + 1]);
        assertCode(404, () -> service.read(7L, name(result.url())));
    }

    private static String name(String url) { return url.substring(url.lastIndexOf('/') + 1); }
    private static MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "note.png", "image/png", bytes); }
    private static byte[] image(String format, int width, int height) throws Exception {
        var image = new BufferedImage(width, height, format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        if (format.equals("jpeg")) {
            var graphics = image.createGraphics();
            graphics.setColor(Color.ORANGE); graphics.fillRect(0, 0, width, height); graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output)); image.flush(); return output.toByteArray();
    }
    private static void assertCode(int code, Runnable action) {
        assertEquals(code, assertThrows(BusinessException.class, action::run).getCode());
    }
}
