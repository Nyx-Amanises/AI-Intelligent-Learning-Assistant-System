package com.aiassistant.learning.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 登录令牌配置。
 *
 * <p>绑定 application.yml 中 app.jwt 开头的配置，供 {@code JwtTokenUtil} 生成和解析 token 使用。
 * 密钥没有默认值：必须通过环境变量 {@code APP_JWT_SECRET} 提供，且至少 32 个字节，否则应用启动失败。</p>
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * token 签名密钥。解析 token 时必须使用同一个密钥，否则会校验失败。
     * HMAC-SHA256 要求密钥不少于 32 字节。
     */
    @NotBlank(message = "app.jwt.secret 不能为空，请通过环境变量 APP_JWT_SECRET 配置")
    @Size(min = 32, message = "app.jwt.secret 长度至少 32 个字符")
    private String secret;

    /**
     * token 过期小时数，超过这个时间后用户需要重新登录。
     */
    @NotNull(message = "app.jwt.expire-hours 不能为空")
    @Min(value = 1, message = "app.jwt.expire-hours 至少为 1")
    private Long expireHours;
}
