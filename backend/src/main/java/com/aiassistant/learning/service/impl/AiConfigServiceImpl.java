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

/**
 * AI 配置服务实现类。
 *
 * <p>这个类的核心思路是：优先读取运行时 JSON 配置，如果某个字段没有配置，
 * 再回退到 application.yml 中的默认配置。</p>
 */
@Service
public class AiConfigServiceImpl implements AiConfigService {

    /** OpenAI 兼容接口，很多国产或本地模型也会提供这种协议。 */
    private static final String CHAT_PROVIDER_OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";
    /** DeepSeek 官方接口。 */
    private static final String CHAT_PROVIDER_DEEPSEEK = "DEEPSEEK";
    /** 火山引擎豆包 Ark 接口。 */
    private static final String CHAT_PROVIDER_DOUBAO_ARK = "DOUBAO_ARK";

    /** yml 中注入的默认 AI 配置。 */
    private final AiProperties aiProperties;
    /** 读写 runtime/ai-config.json 时使用的 JSON 工具。 */
    private final ObjectMapper objectMapper;

    public AiConfigServiceImpl(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询配置时返回的是“合并后”的结果，而不是只返回配置文件里的原始值。
     */
    @Override
    public AiConfigVO getConfig() {
        PersistedAiConfig persistedConfig = loadPersistedConfig();
        ResolvedAiConfig resolvedConfig = mergeConfig(persistedConfig);
        return buildConfigVO(resolvedConfig);
    }

    /**
     * 更新配置时，前端没有传的字段会沿用当前运行时配置。
     */
    @Override
    public AiConfigVO updateConfig(AiConfigUpdateRequest request) {
        PersistedAiConfig persistedConfig = loadPersistedConfig();
        ResolvedAiConfig currentConfig = mergeConfig(persistedConfig);

        PersistedAiConfig targetConfig = new PersistedAiConfig();
        targetConfig.setEnabled(request.getEnabled() == null ? currentConfig.enabled() : request.getEnabled());
        targetConfig.setMockMode(request.getMockMode() == null ? currentConfig.mockMode() : request.getMockMode());
        targetConfig.setChatProviderType(hasText(request.getChatProviderType())
                ? request.getChatProviderType().trim().toUpperCase()
                : currentConfig.chatProviderType());
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

    /**
     * 业务代码调用大模型前会通过这里拿到最终配置。
     */
    @Override
    public ResolvedAiConfig getResolvedConfig() {
        return mergeConfig(loadPersistedConfig());
    }

    /**
     * 合并运行时配置和默认配置。
     */
    private ResolvedAiConfig mergeConfig(PersistedAiConfig persistedConfig) {
        return new ResolvedAiConfig(
                persistedConfig.getEnabled() == null ? aiProperties.getEnabled() : persistedConfig.getEnabled(),
                persistedConfig.getMockMode() == null ? aiProperties.getMockMode() : persistedConfig.getMockMode(),
                resolveChatProviderType(persistedConfig),
                resolveChatBaseUrl(persistedConfig),
                resolveChatPath(persistedConfig),
                resolveEmbeddingProviderType(persistedConfig),
                resolveEmbeddingBaseUrl(persistedConfig),
                resolveEmbeddingPath(persistedConfig),
                hasText(persistedConfig.getApiKey()) ? persistedConfig.getApiKey().trim() : aiProperties.getApiKey(),
                resolveEmbeddingApiKey(persistedConfig),
                resolveDefaultChatModel(persistedConfig),
                hasText(persistedConfig.getDefaultEmbeddingModel())
                        ? persistedConfig.getDefaultEmbeddingModel().trim()
                        : resolveDefaultEmbeddingModel()
        );
    }

    /**
     * 把内部配置对象转换成前端展示对象，同时对 API Key 做脱敏。
     */
    private AiConfigVO buildConfigVO(ResolvedAiConfig resolvedConfig) {
        return AiConfigVO.builder()
                .enabled(Boolean.TRUE.equals(resolvedConfig.enabled()))
                .mockMode(Boolean.TRUE.equals(resolvedConfig.mockMode()))
                .chatProviderType(resolvedConfig.chatProviderType())
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

    /**
     * 从磁盘读取运行时配置；文件不存在时返回空配置。
     */
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

    /**
     * 将配置保存到 runtime/ai-config.json 或自定义路径。
     */
    private void savePersistedConfig(PersistedAiConfig config) {
        Path configPath = resolveConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        } catch (IOException exception) {
            throw new BusinessException(500, "保存 AI 配置失败: " + exception.getMessage());
        }
    }

    /**
     * 校验启用真实 AI 调用时必须填写的关键配置。
     */
    private void validateConfig(PersistedAiConfig config) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        if (Boolean.TRUE.equals(config.getMockMode())) {
            return;
        }
        if (!isSupportedChatProvider(config.getChatProviderType())) {
            throw new BusinessException("不支持的聊天模型 Provider: " + config.getChatProviderType());
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

    /**
     * 获取配置文件路径。
     */
    private Path resolveConfigPath() {
        if (hasText(aiProperties.getConfigFile())) {
            return Paths.get(aiProperties.getConfigFile()).toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.dir"), "runtime", "ai-config.json").toAbsolutePath();
    }

    /**
     * StringUtils.hasText 的简单包装，让下面代码读起来更短。
     */
    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    /**
     * 解析聊天模型提供商类型。
     */
    private String resolveChatProviderType(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getChatProviderType())) {
            return persistedConfig.getChatProviderType().trim().toUpperCase();
        }
        if (hasText(aiProperties.getChatProviderType())) {
            return aiProperties.getChatProviderType().trim().toUpperCase();
        }
        return CHAT_PROVIDER_OPENAI_COMPATIBLE;
    }

    /**
     * 根据提供商推导默认聊天接口地址。
     */
    private String resolveChatBaseUrl(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getBaseUrl())) {
            return persistedConfig.getBaseUrl().trim();
        }
        String providerType = resolveChatProviderType(persistedConfig);
        if (!CHAT_PROVIDER_OPENAI_COMPATIBLE.equals(providerType)) {
            return switch (providerType) {
                case CHAT_PROVIDER_DEEPSEEK -> "https://api.deepseek.com";
                case CHAT_PROVIDER_DOUBAO_ARK -> "https://ark.cn-beijing.volces.com";
                default -> null;
            };
        }
        if (hasText(aiProperties.getBaseUrl())) {
            return aiProperties.getBaseUrl().trim();
        }
        return "https://api.openai.com";
    }

    /**
     * 根据提供商推导默认聊天接口路径。
     */
    private String resolveChatPath(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getChatPath())) {
            return persistedConfig.getChatPath().trim();
        }
        String providerType = resolveChatProviderType(persistedConfig);
        if (!CHAT_PROVIDER_OPENAI_COMPATIBLE.equals(providerType)) {
            return switch (providerType) {
                case CHAT_PROVIDER_DEEPSEEK -> "/chat/completions";
                case CHAT_PROVIDER_DOUBAO_ARK -> "/api/v3/responses";
                default -> null;
            };
        }
        if (hasText(aiProperties.getChatPath())) {
            return aiProperties.getChatPath().trim();
        }
        return "/v1/chat/completions";
    }

    /**
     * 解析默认聊天模型名。
     */
    private String resolveDefaultChatModel(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getDefaultModel())) {
            return persistedConfig.getDefaultModel().trim();
        }
        String providerType = resolveChatProviderType(persistedConfig);
        if (CHAT_PROVIDER_DEEPSEEK.equals(providerType)) {
            return "deepseek-chat";
        }
        if (hasText(aiProperties.getDefaultModel())) {
            return aiProperties.getDefaultModel().trim();
        }
        return null;
    }

    /**
     * 判断聊天模型提供商是否在当前系统支持范围内。
     */
    private boolean isSupportedChatProvider(String providerType) {
        if (!hasText(providerType)) {
            return true;
        }
        String normalized = providerType.trim().toUpperCase();
        return CHAT_PROVIDER_OPENAI_COMPATIBLE.equals(normalized)
                || CHAT_PROVIDER_DEEPSEEK.equals(normalized)
                || CHAT_PROVIDER_DOUBAO_ARK.equals(normalized);
    }

    /**
     * 解析 Embedding 接口路径；OpenAI 兼容接口通常把 chat/completions 替换成 embeddings。
     */
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

    /**
     * 解析 Embedding 提供商类型。
     */
    private String resolveEmbeddingProviderType(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getEmbeddingProviderType())) {
            return persistedConfig.getEmbeddingProviderType().trim().toUpperCase();
        }
        if (hasText(aiProperties.getEmbeddingProviderType())) {
            return aiProperties.getEmbeddingProviderType().trim().toUpperCase();
        }
        return "OPENAI_COMPATIBLE";
    }

    /**
     * 解析 Embedding 接口基础地址；默认复用聊天接口地址。
     */
    private String resolveEmbeddingBaseUrl(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getEmbeddingBaseUrl())) {
            return persistedConfig.getEmbeddingBaseUrl().trim();
        }
        if (hasText(aiProperties.getEmbeddingBaseUrl())) {
            return aiProperties.getEmbeddingBaseUrl().trim();
        }
        return hasText(persistedConfig.getBaseUrl()) ? persistedConfig.getBaseUrl().trim() : aiProperties.getBaseUrl();
    }

    /**
     * 解析 Embedding API Key；没有单独配置时复用聊天模型 Key。
     */
    private String resolveEmbeddingApiKey(PersistedAiConfig persistedConfig) {
        if (hasText(persistedConfig.getEmbeddingApiKey())) {
            return persistedConfig.getEmbeddingApiKey().trim();
        }
        if (hasText(aiProperties.getEmbeddingApiKey())) {
            return aiProperties.getEmbeddingApiKey().trim();
        }
        return hasText(persistedConfig.getApiKey()) ? persistedConfig.getApiKey().trim() : aiProperties.getApiKey();
    }

    /**
     * 解析默认 Embedding 模型。
     */
    private String resolveDefaultEmbeddingModel() {
        if (hasText(aiProperties.getDefaultEmbeddingModel())) {
            return aiProperties.getDefaultEmbeddingModel().trim();
        }
        return aiProperties.getDefaultModel();
    }

    /**
     * API Key 脱敏：只保留前后少量字符，避免完整密钥暴露到前端。
     */
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

    /**
     * 持久化到 JSON 文件中的配置结构。
     */
    private static class PersistedAiConfig {

        private Boolean enabled;
        private Boolean mockMode;
        private String chatProviderType;
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

        public String getChatProviderType() {
            return chatProviderType;
        }

        public void setChatProviderType(String chatProviderType) {
            this.chatProviderType = chatProviderType;
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
