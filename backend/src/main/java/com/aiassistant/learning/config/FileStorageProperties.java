package com.aiassistant.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置。
 *
 * <p>绑定 application.yml 中 app.file 开头的配置，目前主要用于指定上传文件保存目录。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.file")
public class FileStorageProperties {

    /**
     * 用户上传资料保存到服务器本地的目录。
     */
    private String uploadDir;
}
