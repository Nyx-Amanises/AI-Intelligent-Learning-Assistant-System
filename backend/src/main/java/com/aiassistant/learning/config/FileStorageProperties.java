package com.aiassistant.learning.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 文件存储配置。
 *
 * <p>绑定 application.yml 中 app.file 开头的配置，目前主要用于指定上传文件保存目录。
 * 默认值为相对于工作目录的 {@code ./uploads}，可通过环境变量 {@code APP_FILE_UPLOAD_DIR} 覆盖。</p>
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app.file")
public class FileStorageProperties {

    /**
     * 用户上传资料保存到服务器本地的目录。
     */
    @NotBlank(message = "app.file.upload-dir 不能为空，请通过环境变量 APP_FILE_UPLOAD_DIR 配置")
    private String uploadDir;
}
