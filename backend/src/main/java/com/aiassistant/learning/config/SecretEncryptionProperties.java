package com.aiassistant.learning.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 落库敏感信息（目前是 AI 配置里的 API Key）的加密配置。
 *
 * <p>密钥只能来自环境变量 {@code APP_SECRET_ENCRYPTION_KEY}，不进代码库、不进数据库；
 * 缺失或过短时应用启动失败。更换密钥会导致已加密数据无法解密，请谨慎轮换。</p>
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app.secret")
public class SecretEncryptionProperties {

    /**
     * 加密口令，任意长度不少于 16 个字符的随机字符串；程序内部会用 SHA-256 派生成 256 位 AES 密钥。
     */
    @NotBlank(message = "app.secret.encryption-key 不能为空，请通过环境变量 APP_SECRET_ENCRYPTION_KEY 配置")
    @Size(min = 16, message = "app.secret.encryption-key 长度至少 16 个字符")
    private String encryptionKey;
}
