package com.aiassistant.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 登录令牌配置。
 *
 * <p>绑定 application.yml 中 app.jwt 开头的配置，供 {@code JwtTokenUtil} 生成和解析 token 使用。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * token 签名密钥。解析 token 时必须使用同一个密钥，否则会校验失败。
     */
    private String secret;

    /**
     * token 过期小时数，超过这个时间后用户需要重新登录。
     */
    private Long expireHours;
}
