package com.aiassistant.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关基础配置。
 *
 * <p>本项目目前没有使用完整的 Spring Security 登录流程，
 * 但复用了它提供的密码加密器来安全保存用户密码。</p>
 */
@Configuration
public class SecurityConfig {

    /**
     * 注册密码加密器。
     *
     * <p>BCrypt 会给密码加盐并做哈希处理，数据库中保存的是不可逆密文，
     * 登录时通过 {@link PasswordEncoder#matches(CharSequence, String)} 校验。</p>
     *
     * @return BCrypt 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
