package com.aiassistant.learning.common.util;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.FileStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 私有封面只保存随机存储键，不向客户端暴露文件路径。 */
public final class MaterialCoverFiles {
    private static final Logger log = LoggerFactory.getLogger(MaterialCoverFiles.class);
    private static final String DIRECTORY = "material-covers";
    private static final Pattern KEY = Pattern.compile("material-covers/([a-f0-9]{32})\\.jpg");
    private final FileStorageProperties properties;

    public MaterialCoverFiles(FileStorageProperties properties) {
        this.properties = properties;
    }

    public static String publicUrl(Long materialId, String key) {
        if (key == null) return null;
        var match = KEY.matcher(key);
        return match.matches() ? "/api/material/" + materialId + "/cover?v=" + match.group(1) : null;
    }

    public String store(byte[] bytes) {
        String key = DIRECTORY + "/" + UUID.randomUUID().toString().replace("-", "") + ".jpg";
        Path target = null;
        boolean created = false;
        try {
            target = directory(true).resolve(Path.of(key).getFileName());
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                output.write(bytes);
            }
            return key;
        } catch (IOException exception) {
            if (created) deleteNow(key);
            throw new BusinessException(500, "封面保存失败，请稍后重试");
        }
    }

    public byte[] read(String key) {
        try {
            Path target = resolve(key);
            if (target == null || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw missing();
            try (var input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(5 * 1024 * 1024 + 1);
                if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) throw missing();
                return bytes;
            }
        } catch (IOException exception) {
            throw missing();
        }
    }

    public void deleteOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) deleteNow(key);
            }
        });
    }

    public void deleteAfterCommit(String key) {
        if (key == null || !KEY.matcher(key).matches()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteNow(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { deleteNow(key); }
        });
    }

    public void deleteNow(String key) {
        try {
            Path target = resolve(key);
            if (target != null) Files.deleteIfExists(target);
        } catch (IOException exception) {
            // 提交后文件清理失败不能把已成功的数据库提交报告为失败。
            log.warn("私有封面文件清理失败: {}", exception.getClass().getSimpleName());
        }
    }

    private Path resolve(String key) throws IOException {
        if (key == null || !KEY.matcher(key).matches()) return null;
        return directory(false).resolve(Path.of(key).getFileName());
    }

    private Path directory(boolean create) throws IOException {
        Path upload = Path.of(properties.getUploadDir()).toAbsolutePath().normalize();
        if (create) Files.createDirectories(upload);
        Path root = upload.toRealPath();
        Path directory = root.resolve(DIRECTORY);
        if (Files.isSymbolicLink(directory)) throw new IOException("Invalid cover directory");
        if (create) Files.createDirectories(directory);
        if (!directory.toRealPath().startsWith(root)) throw new IOException("Invalid cover directory");
        return directory;
    }

    private static BusinessException missing() { return new BusinessException(404, "资料封面不存在"); }
}
