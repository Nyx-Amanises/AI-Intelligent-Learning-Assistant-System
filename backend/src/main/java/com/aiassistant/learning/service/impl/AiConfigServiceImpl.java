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
        targetConfig.setEmbeddingProviderType(hasText(request.getEmbeddingProviderType())
                ? request.getEmbeddingProviderType().trim()
                : currentConfig.embeddingProviderType());
        targetConfig.setEmbeddingBaseUrl(hasText(request.getEmbeddingBaseUrl())
                ? request.getEmbeddingBaseUrl().trim()
                : currentConfig.embeddingBaseUrl());
        targetConfig.setEmbeddingPath(hasText(request.getEmbeddingPath())
                ? request.getEmbeddingPath().trim()
                : currentConfig.embeddingPath());
        targetConfig.setDefaultModel(hasText(request.getDefaultModel()) ? request.getDefaultModel().trim() : currentConfig.defaultModel());
        targetConfig.setDefaultEmbeddingModel(hasText(request.getDefaultEmbeddingModel())
                ? request.getDefaultEmbeddingModel().trim()
                : currentConfig.defaultEmbeddingModel());
        targetConfig.setApiKey(hasText(request.getApiKey()) ? request.getApiKey().trim() : currentConfig.apiKey());
        targetConfig.setEmbeddingApiKey(hasText(request.getEmbeddingApiKey())
                ? request.getEmbeddingApiKey().trim()
                : currentConfig.embeddingApiKey());

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
                resolveEmbeddingProviderType(persistedConfig),
                resolveEmbeddingBaseUrl(persistedConfig),
                resolveEmbeddingPath(persistedConfig),
                hasText(persistedConfig.getApiKey()) ? persistedConfig.getApiKey().trim() : aiProperties.getApiKey(),
                resolveEmbeddingApiKey(persistedConfig),
                hasText(persistedConfig.getDefaultModel()) ? persistedConfig.getDefaultModel().trim() : aiProperties.getDefaultModel(),
                hasText(persistedConfig.getDefaultEmbeddingModel())
                        ? persistedConfig.getDefaultEmbeddingModel().trim()
                        : resolveDefaultEmbeddingModel()
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
                .embeddingProviderType(resolvedConfig.embeddingProviderType())
                .embeddingBaseUrl(resolvedConfig.embeddingBaseUrl())
                .embeddingPath(resolvedConfig.embeddingPath())
                .defaultEmbeddingModel(resolvedConfig.defaultEmbeddingModel())
                .embeddingApiKeyConfigured(hasText(resolvedConfig.embeddingApiKey()))
                .embeddingApiKeyPreview(maskApiKey(resolvedConfig.embeddingApiKey()))
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

    private String resolveEmbeddingPath(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getEmbeddingPath())) {
            return persistedConfig.getEmbeddingPath().trim();
        }
        if (hasText(aiProperties.getEmbeddingPath())) {
            return aiProperties.getEmbeddingPath().trim();
        }
        if (hasText(persistedConfig.getChatPath()) && persistedConfig.getChatPath().contains("/chat/completions")) {
            return persistedConfig.getChatPath().replace("/chat/completions", "/embeddings").trim();
        }
        if (hasText(aiProperties.getChatPath()) && aiProperties.getChatPath().contains("/chat/completions")) {
            return aiProperties.getChatPath().replace("/chat/completions", "/embeddings").trim();
        }
        return "/v1/embeddings";
    }

    private String resolveEmbeddingProviderType(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getEmbeddingProviderType())) {
            return persistedConfig.getEmbeddingProviderType().trim().toUpperCase();
        }
        if (hasText(aiProperties.getEmbeddingProviderType())) {
            return aiProperties.getEmbeddingProviderType().trim().toUpperCase();
        }
        return "OPENAI_COMPATIBLE";
    }

    private String resolveEmbeddingBaseUrl(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getEmbeddingBaseUrl())) {
            return persistedConfig.getEmbeddingBaseUrl().trim();
        }
        if (hasText(aiProperties.getEmbeddingBaseUrl())) {
            return aiProperties.getEmbeddingBaseUrl().trim();
        }
        return hasText(persistedConfig.getBaseUrl()) ? persistedConfig.getBaseUrl().trim() : aiProperties.getBaseUrl();
    }

    private String resolveEmbeddingApiKey(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getEmbeddingApiKey())) {
            return persistedConfig.getEmbeddingApiKey().trim();
        }
        if (hasText(aiProperties.getEmbeddingApiKey())) {
            return aiProperties.getEmbeddingApiKey().trim();
        }
        return hasText(persistedConfig.getApiKey()) ? persistedConfig.getApiKey().trim() : aiProperties.getApiKey();
    }

    private String resolveDefaultEmbeddingModel() {
        if (hasText(aiProperties.getDefaultEmbeddingModel())) {
            return aiProperties.getDefaultEmbeddingModel().trim();
        }
        return aiProperties.getDefaultModel();
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
        private String embeddingProviderType;
        private String embeddingBaseUrl;
        private String embeddingPath;
        private String apiKey;
        private String embeddingApiKey;
        private String defaultModel;
        private String defaultEmbeddingModel;

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

        public String getEmbeddingProviderType() {
            return embeddingProviderType;
        }

        public void setEmbeddingProviderType(String embeddingProviderType) {
            this.embeddingProviderType = embeddingProviderType;
        }

        public String getEmbeddingBaseUrl() {
            return embeddingBaseUrl;
        }

        public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
            this.embeddingBaseUrl = embeddingBaseUrl;
        }

        public String getEmbeddingPath() {
            return embeddingPath;
        }

        public void setEmbeddingPath(String embeddingPath) {
            this.embeddingPath = embeddingPath;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getEmbeddingApiKey() {
            return embeddingApiKey;
        }

        public void setEmbeddingApiKey(String embeddingApiKey) {
            this.embeddingApiKey = embeddingApiKey;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public String getDefaultEmbeddingModel() {
            return defaultEmbeddingModel;
        }

        public void setDefaultEmbeddingModel(String defaultEmbeddingModel) {
            this.defaultEmbeddingModel = defaultEmbeddingModel;
        }
    }
}
