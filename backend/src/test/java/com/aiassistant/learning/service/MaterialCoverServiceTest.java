package com.aiassistant.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.util.MaterialCoverFiles;
import com.aiassistant.learning.common.util.JpegExifOrientation;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.AdditionalAnswers;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

class MaterialCoverServiceTest {
    @TempDir Path temporaryDirectory;
    private final FileStorageProperties storage = new FileStorageProperties();
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private StudyMaterialMapper mapper;
    private MaterialCoverService service;

    @BeforeEach
    void setup() throws Exception {
        storage.setUploadDir(temporaryDirectory.resolve("uploads").toString());
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:covers_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE study_material(id BIGINT PRIMARY KEY,user_id BIGINT,cover_url VARCHAR(1000),deleted INT DEFAULT 0,updated_at TIMESTAMP)");
        jdbc.update("INSERT INTO study_material(id,user_id) VALUES (42,7),(43,8)");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(StudyMaterialMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setTransactionFactory(new SpringManagedTransactionFactory());
        mapper = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(StudyMaterialMapper.class);
        transactionManager = new DataSourceTransactionManager(dataSource);
        service = transactional(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg"})
    void decodesAndReencodesImagesWithoutFilenameOrTrailingMetadata(String format) throws Exception {
        byte[] image = image(format, 32, 20);
        var payload = new ByteArrayOutputStream();
        payload.write(image);
        payload.write("private-original-metadata".getBytes(StandardCharsets.UTF_8));
        String url = service.upload(7L, 42L, new MockMultipartFile("file", "../../private." + format, "application/octet-stream", payload.toByteArray()));
        assertTrue(url.matches("/api/material/42/cover\\?v=[a-f0-9]{32}"));
        byte[] encoded = service.read(7L, 42L);
        assertEquals(0xff, Byte.toUnsignedInt(encoded[0]));
        assertEquals(0xd8, Byte.toUnsignedInt(encoded[1]));
        assertFalse(new String(encoded, StandardCharsets.ISO_8859_1).contains("private-original-metadata"));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded));
        assertEquals(32, decoded.getWidth());
        assertEquals(20, decoded.getHeight());
        assertEquals(1, files().size());
    }

    @Test
    void resizesLargeImageAndFlattensTransparencyOnWhite() throws Exception {
        service.upload(7L, 42L, file(image("png", 2000, 1000)));
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(service.read(7L, 42L)));
        assertEquals(1600, result.getWidth());
        assertEquals(800, result.getHeight());
        assertEquals(Color.WHITE.getRGB(), result.getRGB(0, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<svg xmlns='http://www.w3.org/2000/svg'></svg>", "not an image", "\u0089PNG"})
    void rejectsSpoofedAndMalformedImages(String content) {
        assertEquals(400, assertThrows(BusinessException.class, () -> service.upload(7L, 42L, file(content.getBytes(StandardCharsets.UTF_8)))).getCode());
        assertNull(key());
    }

    @Test
    void rejectsOtherDecodableFormats() throws Exception {
        assertEquals(400, assertThrows(BusinessException.class, () -> service.upload(7L, 42L, file(image("gif", 8, 8)))).getCode());
    }

    @Test
    void rejectsOversizePayloadEvenIfMultipartSizeLies() {
        byte[] payload = new byte[5 * 1024 * 1024 + 1];
        assertEquals(400, assertThrows(BusinessException.class, () -> service.upload(7L, 42L, file(payload))).getCode());
        var dishonestSize = new MockMultipartFile("file", "cover.png", "image/png", payload) {
            @Override public long getSize() { return 1; }
        };
        assertEquals(400, assertThrows(BusinessException.class, () -> service.upload(7L, 42L, dishonestSize)).getCode());
    }

    @Test
    void rejectsMissingEmptyAndOverlargeDimensions() throws Exception {
        assertEquals(400, assertThrows(BusinessException.class, () -> service.upload(7L, 42L, null)).getCode());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.upload(7L, 42L, file(new byte[0]))).getCode());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.upload(7L, 42L, file(image("png", 8193, 1)))).getCode());
    }

    @Test
    void checksOwnerDeletedAndAnonymousForEveryOperation() throws Exception {
        service.upload(7L, 42L, file(image("png", 8, 8)));
        for (long id : List.of(42L, 999L)) {
            assertEquals(404, assertThrows(BusinessException.class, () -> service.read(8L, id)).getCode());
            assertEquals(404, assertThrows(BusinessException.class, () -> service.upload(8L, id, null)).getCode());
            assertEquals(404, assertThrows(BusinessException.class, () -> service.restoreDefault(8L, id)).getCode());
        }
        assertEquals(401, assertThrows(BusinessException.class, () -> service.read(null, 42L)).getCode());
        assertEquals(401, assertThrows(BusinessException.class, () -> service.upload(null, 42L, null)).getCode());
        assertEquals(401, assertThrows(BusinessException.class, () -> service.restoreDefault(null, 42L)).getCode());
        jdbc.update("UPDATE study_material SET deleted=1 WHERE id=42");
        assertEquals(404, assertThrows(BusinessException.class, () -> service.read(7L, 42L)).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.upload(7L, 42L, null)).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.restoreDefault(7L, 42L)).getCode());
    }

    @Test
    void replacementKeepsOldFileUntilCommitThenCleansIt() throws Exception {
        String originalUrl = service.upload(7L, 42L, file(image("png", 8, 8)));
        Path original = path(key());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String replacement = service.upload(7L, 42L, file(uncheckedImage()));
            assertNotEquals(originalUrl, replacement);
            assertTrue(Files.exists(original));
            assertEquals(2, files().size());
        });
        assertFalse(Files.exists(original));
        assertEquals(1, files().size());
        assertNotNull(service.read(7L, 42L));
    }

    @Test
    void rollbackRestoresOldCoverAndRemovesNewFile() throws Exception {
        service.upload(7L, 42L, file(image("png", 8, 8)));
        String old = key();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.upload(7L, 42L, file(uncheckedImage()));
            status.setRollbackOnly();
        });
        assertEquals(old, key());
        assertTrue(Files.exists(path(old)));
        assertEquals(1, files().size());
    }

    @Test
    void restoreDefaultIsIdempotentAndRollbackRetainsOldFile() throws Exception {
        service.upload(7L, 42L, file(image("png", 8, 8)));
        String old = key();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.restoreDefault(7L, 42L);
            assertTrue(Files.exists(path(old)));
            status.setRollbackOnly();
        });
        assertEquals(old, key());
        service.restoreDefault(7L, 42L);
        service.restoreDefault(7L, 42L);
        assertNull(key());
        assertEquals(0, files().size());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.read(7L, 42L)).getCode());
    }

    @Test
    void databaseFailureDoesNotLeaveNewFiles() {
        StudyMaterialMapper failing = mock(StudyMaterialMapper.class, AdditionalAnswers.delegatesTo(mapper));
        doThrow(new IllegalStateException("db failure")).when(failing).updateCover(eq(7L), eq(42L), anyString());
        assertThrows(IllegalStateException.class, () -> transactional(failing).upload(7L, 42L, file(uncheckedImage())));
        assertNull(key());
        assertEquals(0, files().size());
    }

    @Test
    void doesNotReadOrDeleteUntrustedPaths() throws Exception {
        Path outside = temporaryDirectory.resolve("private.jpg");
        Files.writeString(outside, "private");
        for (String invalid : List.of(outside.toString(), "material-covers/../../private.jpg", "https://example.com/image.jpg")) {
            jdbc.update("UPDATE study_material SET cover_url=? WHERE id=42", invalid);
            assertNull(MaterialCoverFiles.publicUrl(42L, invalid));
            assertEquals(404, assertThrows(BusinessException.class, () -> service.read(7L, 42L)).getCode());
            service.restoreDefault(7L, 42L);
            assertEquals("private", Files.readString(outside));
        }
    }

    @Test
    void rotatesPhonePhotoBeforeApplyingMaximumDimensions() throws Exception {
        byte[] original = image("jpeg", 2000, 1000);
        service.upload(7L, 42L, file(withExif(original, exif(6, true))));
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(service.read(7L, 42L)));
        assertEquals(800, result.getWidth());
        assertEquals(1600, result.getHeight());
    }

    @ParameterizedTest
    @CsvSource({"1,0,1,2,3", "2,1,0,3,2", "3,3,2,1,0", "4,2,3,0,1",
            "5,0,2,1,3", "6,2,0,3,1", "7,3,1,2,0", "8,1,3,0,2"})
    void appliesAllExifRotationsAndMirrorsForBothByteOrders(int orientation, int topLeft, int topRight,
            int bottomLeft, int bottomRight) throws Exception {
        byte[] original = quadrantJpeg();
        for (boolean little : List.of(true, false)) {
            byte[] upload = withExif(original, exif(orientation, little));
            assertEquals(orientation, JpegExifOrientation.read(upload));
            // 标准ImageIO仍返回传感器原始宽高，证明方向必须由封面规范化流程应用。
            assertEquals(80, ImageIO.read(new ByteArrayInputStream(upload)).getWidth());
            service.upload(7L, 42L, file(upload));
            byte[] normalized = service.read(7L, 42L);
            BufferedImage result = ImageIO.read(new ByteArrayInputStream(normalized));
            assertEquals(orientation >= 5 ? 40 : 80, result.getWidth());
            assertEquals(orientation >= 5 ? 80 : 40, result.getHeight());
            assertCorners(result, topLeft, topRight, bottomLeft, bottomRight);
            assertFalse(new String(normalized, StandardCharsets.ISO_8859_1).contains("Exif\u0000\u0000"));
        }
    }

    @Test
    void ignoresTruncatedOrOutOfRangeExifWithoutRejectingOtherwiseValidJpeg() throws Exception {
        byte[] original = quadrantJpeg();
        byte[] valid = exif(6, true);
        var invalidMetadata = new java.util.ArrayList<byte[]>();
        for (int length = 0; length < 28; length++) invalidMetadata.add(Arrays.copyOf(valid, length));
        for (int corruption = 0; corruption < 7; corruption++) {
            byte[] damaged = valid.clone();
            var buffer = ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN);
            switch (corruption) {
                case 0 -> buffer.putInt(10, -1); // unsigned IFD offset must not overflow
                case 1 -> buffer.putInt(10, 2); // offset inside TIFF header
                case 2 -> buffer.putShort(14, (short) 0xffff); // IFD entry count exceeds APP1
                case 3 -> buffer.putInt(20, -1); // orientation count must be exactly one
                case 4 -> buffer.putShort(18, (short) 4); // orientation must be SHORT
                case 5 -> buffer.putShort(24, (short) 9); // unsupported direction
                case 6 -> buffer.putShort(8, (short) 0); // invalid TIFF magic
                default -> throw new AssertionError();
            }
            invalidMetadata.add(damaged);
        }
        for (byte[] metadata : invalidMetadata) {
            byte[] upload = withExif(original, metadata);
            assertEquals(1, JpegExifOrientation.read(upload));
            service.upload(7L, 42L, file(upload));
            BufferedImage result = ImageIO.read(new ByteArrayInputStream(service.read(7L, 42L)));
            assertEquals(80, result.getWidth());
            assertCorners(result, 0, 1, 2, 3);
        }
        // 每一个JPEG截断前缀都可安全检查，解析器不会越过APP1或输入边界。
        byte[] complete = withExif(original, valid);
        for (int length = 0; length < complete.length; length++) {
            final byte[] prefix = Arrays.copyOf(complete, length);
            assertDoesNotThrow(() -> JpegExifOrientation.read(prefix));
        }
    }

    @Test
    void staleGenericMaterialUpdateCannotOverwriteAReplacedCover() throws Exception {
        service.upload(7L, 42L, file(image("png", 8, 8)));
        var stale = mapper.lockOwnedCover(7L, 42L);
        service.upload(7L, 42L, file(image("png", 10, 10)));
        String current = key();
        mapper.updateById(stale);
        assertEquals(current, key());
        assertEquals(1, files().size());
        assertNotNull(service.read(7L, 42L));
    }

    @Test
    void rejectsPixelBombFromHeaderBeforeAllocatingDecodedImage() throws Exception {
        byte[] bytes = image("png", 1, 1);
        java.nio.ByteBuffer.wrap(bytes).putInt(16, 5000).putInt(20, 4000);
        var crc = new java.util.zip.CRC32();
        crc.update(bytes, 12, 17);
        java.nio.ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());
        BusinessException failure = assertThrows(BusinessException.class, () -> service.upload(7L, 42L, file(bytes)));
        assertTrue(failure.getMessage().contains("1600万"));
        assertEquals(0, files().size());
    }

    @Test
    void concurrentReplacementsLeaveExactlyOneOwnedReadableCover() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return service.upload(7L, 42L, file(uncheckedImage())); });
            var second = executor.submit(() -> { start.await(); return service.upload(7L, 42L, file(uncheckedImage())); });
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, files().size());
            assertNotNull(service.read(7L, 42L));
        } finally { executor.shutdownNow(); }
    }

    private MaterialCoverService transactional(StudyMaterialMapper actualMapper) {
        ProxyFactory proxy = new ProxyFactory(new MaterialCoverService(actualMapper, storage));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (MaterialCoverService) proxy.getProxy();
    }
    private String key() { return jdbc.queryForObject("SELECT cover_url FROM study_material WHERE id=42", String.class); }
    private Path path(String key) { return Path.of(storage.getUploadDir()).resolve(key); }
    private List<Path> files() {
        Path dir = Path.of(storage.getUploadDir()).resolve("material-covers");
        if (!Files.exists(dir)) return List.of();
        try (var files = Files.list(dir)) { return files.toList(); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
    private static MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "cover.png", "image/png", bytes); }
    private static byte[] exif(int orientation, boolean little) {
        byte[] metadata = new byte[32];
        System.arraycopy(new byte[]{'E', 'x', 'i', 'f', 0, 0}, 0, metadata, 0, 6);
        var buffer = ByteBuffer.wrap(metadata).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buffer.position(6);
        buffer.put((byte) (little ? 'I' : 'M')).put((byte) (little ? 'I' : 'M'));
        buffer.putShort((short) 42).putInt(8).putShort((short) 1);
        buffer.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0).putInt(0);
        return metadata;
    }
    private static byte[] withExif(byte[] jpeg, byte[] metadata) throws Exception {
        var output = new ByteArrayOutputStream();
        output.write(jpeg, 0, 2);
        output.write(0xff);
        output.write(0xe1);
        output.write((metadata.length + 2) >> 8);
        output.write((metadata.length + 2) & 0xff);
        output.write(metadata);
        output.write(jpeg, 2, jpeg.length - 2);
        return output.toByteArray();
    }
    private static final Color[] QUADRANTS = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
    private static byte[] quadrantJpeg() throws Exception {
        var image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        for (int index = 0; index < 4; index++) {
            graphics.setColor(QUADRANTS[index]);
            graphics.fillRect(index % 2 * 40, index / 2 * 20, 40, 20);
        }
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpeg", output));
        image.flush();
        return output.toByteArray();
    }
    private static void assertCorners(BufferedImage image, int... expected) {
        for (int index = 0; index < 4; index++) {
            Color actual = new Color(image.getRGB((index % 2 * 2 + 1) * image.getWidth() / 4,
                    (index / 2 * 2 + 1) * image.getHeight() / 4));
            Color color = QUADRANTS[expected[index]];
            assertTrue(Math.abs(actual.getRed() - color.getRed()) < 25
                    && Math.abs(actual.getGreen() - color.getGreen()) < 25
                    && Math.abs(actual.getBlue() - color.getBlue()) < 25, "Unexpected quadrant " + index + ": " + actual);
        }
    }
    private static byte[] uncheckedImage() {
        try { return image("png", 8, 8); } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private static byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        image.flush();
        return output.toByteArray();
    }
}
