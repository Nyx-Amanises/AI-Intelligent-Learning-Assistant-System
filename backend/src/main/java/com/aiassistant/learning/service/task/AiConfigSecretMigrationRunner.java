package com.aiassistant.learning.service.task;

import com.aiassistant.learning.common.security.SecretCipher;
import com.aiassistant.learning.entity.AiConfig;
import com.aiassistant.learning.mapper.AiConfigMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 应用启动时把 ai_config 表里仍是明文的 API Key 就地加密。
 *
 * <p>加密上线前保存的行没有 {@code enc:v1:} 前缀，读取时按明文兼容；
 * 这里在启动阶段一次性加密回写，之后数据库里就不再有明文密钥。</p>
 */
@Slf4j
@Component
public class AiConfigSecretMigrationRunner implements ApplicationRunner {

    private final AiConfigMapper aiConfigMapper;
    private final SecretCipher secretCipher;

    public AiConfigSecretMigrationRunner(AiConfigMapper aiConfigMapper, SecretCipher secretCipher) {
        this.aiConfigMapper = aiConfigMapper;
        this.secretCipher = secretCipher;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int migrated = encryptPlainTextKeys();
            if (migrated > 0) {
                log.info("已将 {} 条 ai_config 记录中的明文 API Key 加密回写", migrated);
            }
        } catch (Exception exception) {
            log.error("加密历史 AI 配置密钥失败: {}", exception.getMessage(), exception);
        }
    }

    /**
     * 逐行检查并加密，返回被改写的记录数。
     */
    public int encryptPlainTextKeys() {
        List<AiConfig> configs = aiConfigMapper.selectList(null);
        int migrated = 0;
        for (AiConfig config : configs) {
            boolean changed = false;
            if (StringUtils.hasText(config.getApiKey()) && !secretCipher.isEncrypted(config.getApiKey())) {
                config.setApiKey(secretCipher.encrypt(config.getApiKey()));
                changed = true;
            }
            if (StringUtils.hasText(config.getEmbeddingApiKey()) && !secretCipher.isEncrypted(config.getEmbeddingApiKey())) {
                config.setEmbeddingApiKey(secretCipher.encrypt(config.getEmbeddingApiKey()));
                changed = true;
            }
            if (changed) {
                aiConfigMapper.updateById(config);
                migrated++;
            }
        }
        return migrated;
    }
}
