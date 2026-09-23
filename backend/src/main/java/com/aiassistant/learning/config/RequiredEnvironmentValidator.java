package com.aiassistant.learning.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 启动前校验必填的敏感配置是否真的提供了。
 *
 * <p>Spring Boot 绑定 {@code @ConfigurationProperties} 时对无法解析的占位符是“宽松”的：
 * {@code ${APP_JWT_SECRET}} 解析失败不会报错，而是把这串字面量当成值继续用。
 * 这会让“忘记配置密钥”变成“用一个众所周知的固定字符串当密钥”。
 * 这里在环境准备完成、任何 Bean 创建之前用严格模式逐项解析，缺失就直接终止启动并列出缺哪些环境变量。</p>
 */
public class RequiredEnvironmentValidator implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /**
     * 必填项：配置键 -> 对应环境变量名。
     */
    static final List<RequiredProperty> REQUIRED_PROPERTIES = List.of(
            new RequiredProperty("spring.datasource.password", "SPRING_DATASOURCE_PASSWORD", 1),
            new RequiredProperty("app.jwt.secret", "APP_JWT_SECRET", 32),
            new RequiredProperty("app.secret.encryption-key", "APP_SECRET_ENCRYPTION_KEY", 16)
    );

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        validate(event.getEnvironment());
    }

    /**
     * 逐项严格解析；任何一项缺失、仍是占位符或长度不足都会抛出异常。
     */
    public static void validate(ConfigurableEnvironment environment) {
        List<String> problems = new ArrayList<>();
        for (RequiredProperty property : REQUIRED_PROPERTIES) {
            String value;
            try {
                value = environment.getProperty(property.key());
            } catch (IllegalArgumentException unresolvedPlaceholder) {
                value = null;
            }
            if (value == null || value.isBlank() || value.contains("${")) {
                problems.add("缺少环境变量 " + property.environmentVariable() + "（配置项 " + property.key() + "）");
            } else if (value.trim().length() < property.minLength()) {
                problems.add("环境变量 " + property.environmentVariable() + " 长度至少 " + property.minLength() + " 个字符");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("启动中止，必填配置不完整：" + String.join("；", problems)
                    + "。请参考项目根目录 .env.example 补齐后重试。");
        }
    }

    /**
     * 必填配置描述。
     */
    record RequiredProperty(String key, String environmentVariable, int minLength) {
    }
}
