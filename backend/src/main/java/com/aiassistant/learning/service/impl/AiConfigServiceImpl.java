package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.AiProperties;
import com.aiassistant.learning.dto.ai.AiConfigUpdateRequest;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.vo.ai.AiConfigVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiConfigServiceImpl implements AiConfigService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiConfigServiceImpl(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiConfigVO getConfig() {
        PersistedAiConfig persistedConfig = loadPersistedConfig();
        ResolvedAiConfig resolvedConfig = mergeConfig(persistedConfig);
        return buildConfigVO(resolvedConfig);
    }

    @Override
    public AiConfigVO updateConfig(AiConfigUpdateRequest request) {
        PersistedAiConfig persistedConfig = loadPersistedConfig();
        ResolvedAiConfig currentConfig = mergeConfig(persistedConfig);

        PersistedAiConfig targetConfig = new PersistedAiConfig();
        targetConfig.setEnabled(request.getEnabled() == null ? currentConfig.enabled() : request.getEnabled());
        targetConfig.setMockMode(request.getMockMode() == null ? currentConfig.mockMode() : request.getMockMode());
        targetConfig.setBaseUrl(hasText(request.getBaseUrl()) ? request.getBaseUrl().trim() : currentConfig.baseUrl());
        targetConfig.setChatPath(hasText(request.getChatPath()) ? request.getChatPath().trim() : currentConfig.chatPath());
        targetConfig.setDefaultModel(hasText(request.getDefaultModel()) ? request.getDefaultModel().trim() : currentConfig.defaultModel());
        targetConfig.setApiKey(hasText(request.getApiKey()) ? request.getApiKey().trim() : currentConfig.apiKey());

        validateConfig(targetConfig);
        savePersistedConfig(targetConfig);

        return buildConfigVO(mergeConfig(targetConfig));
    }

    @Override
    public ResolvedAiConfig getResolvedConfig() {
        return mergeConfig(loadPersistedConfig());
    }

    private ResolvedAiConfig mergeConfig(PersistedAiConfig persistedConfig) {
        return new ResolvedAiConfig(
                persistedConfig.getEnabled() == null ? aiProperties.getEnabled() : persistedConfig.getEnabled(),
                persistedConfig.getMockMode() == null ? aiProperties.getMockMode() : persistedConfig.getMockMode(),
                hasText(persistedConfig.getBaseUrl()) ? persistedConfig.getBaseUrl().trim() : aiProperties.getBaseUrl(),
                hasText(persistedConfig.getChatPath()) ? persistedConfig.getChatPath().trim() : aiProperties.getChatPath(),
                hasText(persistedConfig.getApiKey()) ? persistedConfig.getApiKey().trim() : aiProperties.getApiKey(),
                hasText(persistedConfig.getDefaultModel()) ? persistedConfig.getDefaultModel().trim() : aiProperties.getDefaultModel()
        );
    }

    private AiConfigVO buildConfigVO(ResolvedAiConfig resolvedConfig) {
        return AiConfigVO.builder()
                .enabled(Boolean.TRUE.equals(resolvedConfig.enabled()))
                .mockMode(Boolean.TRUE.equals(resolvedConfig.mockMode()))
                .baseUrl(resolvedConfig.baseUrl())
                .chatPath(resolvedConfig.chatPath())
                .defaultModel(resolvedConfig.defaultModel())
                .apiKeyConfigured(hasText(resolvedConfig.apiKey()))
                .apiKeyPreview(maskApiKey(resolvedConfig.apiKey()))
                .build();
    }

    private PersistedAiConfig loadPersistedConfig() {
        Path configPath = resolveConfigPath();
        if (!Files.exists(configPath)) {
            return new PersistedAiConfig();
        }
        try {
            return objectMapper.readValue(configPath.toFile(), PersistedAiConfig.class);
        } catch (IOException exception) {
            throw new BusinessException(500, "读取 AI 配置失败: " + exception.getMessage());
        }
    }

    private void savePersistedConfig(PersistedAiConfig config) {
        Path configPath = resolveConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        } catch (IOException exception) {
            throw new BusinessException(500, "保存 AI 配置失败: " + exception.getMessage());
        }
    }

    private void validateConfig(PersistedAiConfig config) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        if (Boolean.TRUE.equals(config.getMockMode())) {
            return;
        }
        if (!hasText(config.getBaseUrl())) {
            throw new BusinessException("请填写 AI Base URL");
        }
        if (!hasText(config.getChatPath())) {
            throw new BusinessException("请填写 AI Chat Path");
        }
        if (!hasText(config.getDefaultModel())) {
            throw new BusinessException("请填写默认模型");
        }
        if (!hasText(config.getApiKey())) {
            throw new BusinessException("请填写 AI API Key");
        }
    }

    private Path resolveConfigPath() {
        if (hasText(aiProperties.getConfigFile())) {
            return Paths.get(aiProperties.getConfigFile()).toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.dir"), "runtime", "ai-config.json").toAbsolutePath();
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String maskApiKey(String apiKey) {
        if (!hasText(apiKey)) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "********";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static class PersistedAiConfig {

        private Boolean enabled;
        private Boolean mockMode;
        private String baseUrl;
        private String chatPath;
        private String apiKey;
        private String defaultModel;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Boolean getMockMode() {
            return mockMode;
        }

        public void setMockMode(Boolean mockMode) {
            this.mockMode = mockMode;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getChatPath() {
            return chatPath;
        }

        public void setChatPath(String chatPath) {
            this.chatPath = chatPath;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }
    }
}
