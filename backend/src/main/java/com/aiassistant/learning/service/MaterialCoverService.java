package com.aiassistant.learning.service;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.util.MaterialCoverFiles;
import com.aiassistant.learning.common.util.JpegExifOrientation;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaterialCoverService {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final StudyMaterialMapper mapper;
    private final MaterialCoverFiles files;

    public MaterialCoverService(StudyMaterialMapper mapper, FileStorageProperties properties) {
        this.mapper = mapper;
        this.files = new MaterialCoverFiles(properties);
    }

    @Transactional(rollbackFor = Exception.class)
    public String upload(Long userId, Long materialId, MultipartFile file) {
        StudyMaterial material = lockOwned(userId, materialId);
        byte[] normalized = normalize(file);
        String key = files.store(normalized);
        files.deleteOnRollback(key);
        try {
            if (mapper.updateCover(userId, materialId, key) != 1) throw new BusinessException(409, "资料已变更，请重试");
            files.deleteAfterCommit(material.getCoverUrl());
            return MaterialCoverFiles.publicUrl(materialId, key);
        } catch (RuntimeException exception) {
            files.deleteNow(key);
            throw exception;
        }
    }

    /** 读取期间也持有同一资料锁，避免替换提交后清理旧文件与读取相互竞态。 */
    @Transactional(rollbackFor = Exception.class)
    public byte[] read(Long userId, Long materialId) {
        return files.read(lockOwned(userId, materialId).getCoverUrl());
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreDefault(Long userId, Long materialId) {
        StudyMaterial material = lockOwned(userId, materialId);
        if (material.getCoverUrl() == null) return;
        if (mapper.updateCover(userId, materialId, null) != 1) throw new BusinessException(409, "资料已变更，请重试");
        files.deleteAfterCommit(material.getCoverUrl());
    }

    private StudyMaterial lockOwned(Long userId, Long materialId) {
        if (userId == null) throw new BusinessException(401, "请先登录");
        StudyMaterial material = mapper.lockOwnedCover(userId, materialId);
        if (material == null || !userId.equals(material.getUserId())) throw new BusinessException(404, "资料不存在");
        return material;
    }

    private static byte[] normalize(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请选择封面图片");
        if (file.getSize() > MAX_BYTES) throw new BusinessException(400, "封面图片不能超过5MB");
        try (var source = file.getInputStream()) {
            byte[] bytes = source.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new BusinessException(400, "封面图片不能超过5MB");
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidImage();
                var reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("png") && !format.equals("jpeg")) throw invalidImage();
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > 16_000_000) {
                        throw new BusinessException(400, "图片尺寸过大，请使用边长不超过8192像素、总像素不超过1600万的图片");
                    }
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null) throw invalidImage();
                    int orientation = format.equals("jpeg") ? JpegExifOrientation.read(bytes) : 1;
                    int orientedWidth = orientation >= 5 ? height : width;
                    int orientedHeight = orientation >= 5 ? width : height;
                    double scale = Math.min(1, 1600.0 / Math.max(width, height));
                    BufferedImage output = new BufferedImage(Math.max(1, (int) (orientedWidth * scale)),
                            Math.max(1, (int) (orientedHeight * scale)), BufferedImage.TYPE_INT_RGB);
                    var graphics = output.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, output.getWidth(), output.getHeight());
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        // EXIF方向先作用于像素，再缩放；合并绘制避免额外分配一张全尺寸图片。
                        AffineTransform transform = AffineTransform.getScaleInstance(
                                (double) output.getWidth() / orientedWidth, (double) output.getHeight() / orientedHeight);
                        transform.concatenate(JpegExifOrientation.transform(orientation, width, height));
                        graphics.drawImage(decoded, transform, null);
                    } finally {
                        graphics.dispose();
                        decoded.flush();
                    }
                    var encoded = new ByteArrayOutputStream();
                    var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                    try (var target = new MemoryCacheImageOutputStream(encoded)) {
                        writer.setOutput(target);
                        var params = writer.getDefaultWriteParam();
                        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        params.setCompressionQuality(0.85f);
                        writer.write(null, new IIOImage(output, null, null), params);
                    } finally {
                        writer.dispose();
                        output.flush();
                    }
                    if (encoded.size() > MAX_BYTES) throw new BusinessException(400, "封面图片过大，请压缩后重试");
                    return encoded.toByteArray();
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidImage();
        }
    }

    private static BusinessException invalidImage() { return new BusinessException(400, "请选择有效的PNG或JPEG图片"); }
}
