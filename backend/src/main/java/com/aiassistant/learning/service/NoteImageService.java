package com.aiassistant.learning.service;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.util.JpegExifOrientation;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.vo.note.NoteImageVO;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Image storage belongs to the authenticated user and is never a public static directory. */
@Service
public class NoteImageService {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_OUTPUT_EDGE = 2400;
    private static final Pattern FILE_NAME = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpg)");
    private final FileStorageProperties storage;

    public NoteImageService(FileStorageProperties storage) { this.storage = storage; }

    public record ImageFile(byte[] bytes, String contentType) {}
    private record Normalized(byte[] bytes, String extension, int width, int height) {}

    public NoteImageVO upload(Long userId, MultipartFile file) {
        requireUser(userId);
        Normalized image = normalize(file);
        String name = UUID.randomUUID() + "." + image.extension();
        Path target = null;
        boolean created = false;
        try {
            target = directory(userId, true).resolve(name);
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                output.write(image.bytes());
            }
            return new NoteImageVO("/api/notes/images/" + name, image.width(), image.height());
        } catch (IOException exception) {
            if (created && target != null) {
                try { Files.deleteIfExists(target); }
                catch (IOException ignored) { /* Partial files have no returned URL and cannot replace existing images. */ }
            }
            throw new BusinessException(500, "笔记图片保存失败，请稍后重试");
        }
    }

    public ImageFile read(Long userId, String name) {
        requireUser(userId);
        if (name == null || !FILE_NAME.matcher(name).matches()) throw missing();
        try {
            Path path = directory(userId, false).resolve(name);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw missing();
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAX_BYTES) throw missing();
                return new ImageFile(bytes, name.endsWith(".png") ? "image/png" : "image/jpeg");
            }
        } catch (IOException exception) {
            throw missing();
        }
    }

    private Path directory(Long userId, boolean create) throws IOException {
        Path upload = Path.of(storage.getUploadDir()).toAbsolutePath().normalize();
        if (create) Files.createDirectories(upload);
        Path root = upload.toRealPath();
        Path images = childDirectory(root, "notes", create);
        return childDirectory(images, userId.toString(), create);
    }

    private static Path childDirectory(Path parent, String name, boolean create) throws IOException {
        Path directory = parent.resolve(name);
        if (Files.isSymbolicLink(directory)) throw new IOException("Invalid private image directory");
        if (create) Files.createDirectories(directory);
        // Equality also rejects junctions redirected to another user's directory inside the upload root.
        if (!directory.toRealPath().equals(directory)) throw new IOException("Invalid private image directory");
        return directory;
    }

    private static Normalized normalize(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请选择笔记图片");
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        try (var source = file.getInputStream()) {
            byte[] bytes = source.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw tooLarge();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidImage();
                var reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("png") && !format.equals("jpeg")) throw invalidImage();
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > 16_000_000)
                        throw new BusinessException(400, "图片尺寸过大，请使用单边不超过8192像素、总像素不超过1600万的图片");
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null) throw invalidImage();
                    int orientation = format.equals("jpeg") ? JpegExifOrientation.read(bytes) : 1;
                    int orientedWidth = orientation >= 5 ? height : width;
                    int orientedHeight = orientation >= 5 ? width : height;
                    double scale = Math.min(1, (double) MAX_OUTPUT_EDGE / Math.max(width, height));
                    boolean png = format.equals("png");
                    BufferedImage output = new BufferedImage(Math.max(1, (int) Math.round(orientedWidth * scale)),
                            Math.max(1, (int) Math.round(orientedHeight * scale)),
                            png && decoded.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                    try {
                        var graphics = output.createGraphics();
                        try {
                            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                            AffineTransform transform = AffineTransform.getScaleInstance(
                                    (double) output.getWidth() / orientedWidth, (double) output.getHeight() / orientedHeight);
                            transform.concatenate(JpegExifOrientation.transform(orientation, width, height));
                            graphics.drawImage(decoded, transform, null);
                        } finally { graphics.dispose(); decoded.flush(); }
                        var encoded = new ByteArrayOutputStream();
                        var writer = ImageIO.getImageWritersByFormatName(png ? "png" : "jpeg").next();
                        try (var destination = new MemoryCacheImageOutputStream(encoded)) {
                            writer.setOutput(destination);
                            var params = writer.getDefaultWriteParam();
                            if (!png) {
                                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                                params.setCompressionQuality(0.88f);
                            }
                            // New pixel-only output strips uploaded scripts, metadata, EXIF and trailing payloads.
                            writer.write(null, new IIOImage(output, null, null), params);
                        } finally { writer.dispose(); }
                        if (encoded.size() > MAX_BYTES) throw tooLarge();
                        return new Normalized(encoded.toByteArray(), png ? "png" : "jpg", output.getWidth(), output.getHeight());
                    } finally { output.flush(); }
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidImage();
        }
    }

    private static void requireUser(Long userId) {
        if (userId == null || userId <= 0) throw new BusinessException(401, "请先登录");
    }
    private static BusinessException invalidImage() { return new BusinessException(400, "请选择有效的PNG或JPEG图片"); }
    private static BusinessException tooLarge() { return new BusinessException(400, "笔记图片不能超过5MB，请压缩后重试"); }
    private static BusinessException missing() { return new BusinessException(404, "笔记图片不存在"); }
}
