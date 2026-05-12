package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.AiProperties;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.ai.AiConfigUpdateRequest;
import com.aiassistant.learning.entity.AiConfig;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.AiConfigMapper;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.vo.ai.AiConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Resolves AI config with this priority:
 * personal user config -> administrator shared config -> legacy JSON file -> env/application config.
 */
@Service
public class AiConfigServiceImpl implements AiConfigService {

    private static final String SCOPE_GLOBAL = "GLOBAL";
    private static final String SCOPE_USER = "USER";
    private static final long GLOBAL_USER_ID = 0L;
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ADMIN_USERNAME = "admin";

    private static final String CHAT_PROVIDER_OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";
    private static final String CHAT_PROVIDER_DEEPSEEK = "DEEPSEEK";
    private static final String CHAT_PROVIDER_DOUBAO_ARK = "DOUBAO_ARK";
    private static final String EMBEDDING_PROVIDER_OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AiConfigMapper aiConfigMapper;
    private final SysUserMapper sysUserMapper;

    public AiConfigServiceImpl(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AiConfigMapper aiConfigMapper,
            SysUserMapper sysUserMapper
    ) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.aiConfigMapper = aiConfigMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public AiConfigVO getConfig(Long userId) {
        requireUser(userId);
        return buildConfigVO(userId, resolveEffectiveConfig(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConfigVO updateConfig(Long userId, AiConfigUpdateRequest request) {
        requireUser(userId);
        AiConfig existing = findConfig(SCOPE_USER, userId);
        ConfigDraft sharedFallback = resolveSharedConfig().config;
        ConfigDraft target = buildTargetConfig(
                request,
                existing == null ? null : fromEntity(existing),
                sharedFallback,
                false
        );
        validateConfig(applySharedSecretsWhenEndpointMatches(target, sharedFallback));
        upsertConfig(existing, SCOPE_USER, userId, target);
        return getConfig(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConfigVO clearUserConfig(Long userId) {
        requireUser(userId);
        aiConfigMapper.delete(new LambdaQueryWrapper<AiConfig>()
                .eq(AiConfig::getScope, SCOPE_USER)
                .eq(AiConfig::getUserId, userId));
        return getConfig(userId);
    }

    @Override
    public AiConfigVO getGlobalConfig(Long userId) {
        requireAdmin(userId);
        return buildConfigVO(userId, resolveSharedConfig());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConfigVO updateGlobalConfig(Long userId, AiConfigUpdateRequest request) {
        requireAdmin(userId);
        AiConfig existing = findConfig(SCOPE_GLOBAL, GLOBAL_USER_ID);
        ConfigDraft legacyFallback = resolveLegacyFallback();
        ConfigDraft target = buildTargetConfig(
                request,
                existing == null ? null : fromEntity(existing),
                legacyFallback,
                true
        );
        validateConfig(target);
        upsertConfig(existing, SCOPE_GLOBAL, GLOBAL_USER_ID, target);
        return getGlobalConfig(userId);
    }

    @Override
    public ResolvedAiConfig getResolvedConfig() {
        return getResolvedConfig(UserContext.getCurrentUserId());
    }

    @Override
    public ResolvedAiConfig getResolvedConfig(Long userId) {
        return toResolvedConfig(resolveEffectiveConfig(userId).config);
    }

    private EffectiveConfig resolveEffectiveConfig(Long userId) {
        EffectiveConfig sharedConfig = resolveSharedConfig();
        if (userId == null) {
            return sharedConfig;
        }

        AiConfig personalConfig = findConfig(SCOPE_USER, userId);
        if (personalConfig == null) {
            return sharedConfig;
        }

        ConfigDraft personalDraft = mergeDraft(fromEntity(personalConfig), sharedConfig.config, false);
        ConfigDraft effectivePersonalDraft = applySharedSecretsWhenEndpointMatches(personalDraft, sharedConfig.config);
        if (!isUsableConfig(effectivePersonalDraft)) {
            return sharedConfig;
        }
        return new EffectiveConfig(effectivePersonalDraft, SCOPE_USER);
    }

    private EffectiveConfig resolveSharedConfig() {
        ConfigDraft fallback = resolveLegacyFallback();
        String source = hasAnyValue(loadLegacyConfig()) ? "LEGACY" : "ENV";

        AiConfig globalConfig = findConfig(SCOPE_GLOBAL, GLOBAL_USER_ID);
        if (globalConfig != null) {
            ConfigDraft globalDraft = mergeDraft(fromEntity(globalConfig), fallback, true);
            if (isUsableConfig(globalDraft)) {
                return new EffectiveConfig(globalDraft, SCOPE_GLOBAL);
            }
        }
        return new EffectiveConfig(fallback, source);
    }

    private ConfigDraft resolveLegacyFallback() {
        return mergeDraft(loadLegacyConfig(), fromProperties(), true);
    }

    private ConfigDraft buildTargetConfig(
            AiConfigUpdateRequest request,
            ConfigDraft existing,
            ConfigDraft fallback,
            boolean allowFallbackSecrets
    ) {
        ConfigDraft target = new ConfigDraft();
        target.enabled = request.getEnabled() == null ? firstNonNull(bool(existing, "enabled"), fallback.enabled) : request.getEnabled();
        target.mockMode = request.getMockMode() == null ? firstNonNull(bool(existing, "mockMode"), fallback.mockMode) : request.getMockMode();
        target.chatProviderType = pickText(request.getChatProviderType(), text(existing, "chatProviderType"), fallback.chatProviderType);
        target.baseUrl = pickText(request.getBaseUrl(), text(existing, "baseUrl"), fallback.baseUrl);
        target.chatPath = pickText(request.getChatPath(), text(existing, "chatPath"), fallback.chatPath);
        target.defaultModel = pickText(request.getDefaultModel(), text(existing, "defaultModel"), fallback.defaultModel);
        target.embeddingProviderType = pickText(
                request.getEmbeddingProviderType(),
                text(existing, "embeddingProviderType"),
                fallback.embeddingProviderType
        );
        target.embeddingBaseUrl = pickText(request.getEmbeddingBaseUrl(), text(existing, "embeddingBaseUrl"), fallback.embeddingBaseUrl);
        target.embeddingPath = pickText(request.getEmbeddingPath(), text(existing, "embeddingPath"), fallback.embeddingPath);
        target.defaultEmbeddingModel = pickText(
                request.getDefaultEmbeddingModel(),
                text(existing, "defaultEmbeddingModel"),
                fallback.defaultEmbeddingModel
        );
        target.apiKey = pickSecret(
                request.getApiKey(),
                text(existing, "apiKey"),
                allowFallbackSecrets ? fallback.apiKey : null
        );
        target.embeddingApiKey = pickSecret(
                request.getEmbeddingApiKey(),
                text(existing, "embeddingApiKey"),
                allowFallbackSecrets ? fallback.embeddingApiKey : null
        );
        return target;
    }

    private void validateConfig(ConfigDraft draft) {
        ResolvedAiConfig config = toResolvedConfig(draft);
        if (!Boolean.TRUE.equals(config.enabled())) {
            return;
        }
        if (Boolean.TRUE.equals(config.mockMode())) {
            return;
        }
        if (!isSupportedChatProvider(config.chatProviderType())) {
            throw new BusinessException("不支持的聊天模型 Provider: " + config.chatProviderType());
        }
        if (!hasText(config.baseUrl())) {
            throw new BusinessException("请填写 AI Base URL");
        }
        if (!hasText(config.chatPath())) {
            throw new BusinessException("请填写 AI Chat Path");
        }
        if (!hasText(config.defaultModel())) {
            throw new BusinessException("请填写默认模型");
        }
        if (!hasText(config.apiKey())) {
            throw new BusinessException("请填写当前配置作用域自己的 AI API Key。普通用户不填写个人 Key 时，只有接口地址、路径和模型与共享配置一致，才会复用管理员共享 Key。");
        }
    }

    private void upsertConfig(AiConfig existing, String scope, Long userId, ConfigDraft draft) {
        AiConfig entity = existing == null ? new AiConfig() : existing;
        entity.setScope(scope);
        entity.setUserId(userId);
        entity.setEnabled(draft.enabled);
        entity.setMockMode(draft.mockMode);
        entity.setChatProviderType(normalizeText(draft.chatProviderType, true));
        entity.setBaseUrl(normalizeText(draft.baseUrl, false));
        entity.setChatPath(normalizeText(draft.chatPath, false));
        entity.setApiKey(normalizeText(draft.apiKey, false));
        entity.setDefaultModel(normalizeText(draft.defaultModel, false));
        entity.setEmbeddingProviderType(normalizeText(draft.embeddingProviderType, true));
        entity.setEmbeddingBaseUrl(normalizeText(draft.embeddingBaseUrl, false));
        entity.setEmbeddingPath(normalizeText(draft.embeddingPath, false));
        entity.setEmbeddingApiKey(normalizeText(draft.embeddingApiKey, false));
        entity.setDefaultEmbeddingModel(normalizeText(draft.defaultEmbeddingModel, false));

        if (existing == null) {
            aiConfigMapper.insert(entity);
            return;
        }
        aiConfigMapper.updateById(entity);
    }

    private AiConfigVO buildConfigVO(Long userId, EffectiveConfig effectiveConfig) {
        ResolvedAiConfig resolvedConfig = toResolvedConfig(effectiveConfig.config);
        return AiConfigVO.builder()
                .enabled(Boolean.TRUE.equals(resolvedConfig.enabled()))
                .mockMode(Boolean.TRUE.equals(resolvedConfig.mockMode()))
                .configSource(effectiveConfig.source)
                .canManageGlobal(isAdmin(userId))
                .personalConfigured(userId != null && findConfig(SCOPE_USER, userId) != null)
                .globalConfigured(findConfig(SCOPE_GLOBAL, GLOBAL_USER_ID) != null)
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

    private ResolvedAiConfig toResolvedConfig(ConfigDraft draft) {
        String chatProviderType = normalizeText(draft.chatProviderType, true);
        if (!hasText(chatProviderType)) {
            chatProviderType = CHAT_PROVIDER_OPENAI_COMPATIBLE;
        }

        String baseUrl = normalizeText(draft.baseUrl, false);
        if (!hasText(baseUrl)) {
            baseUrl = switch (chatProviderType) {
                case CHAT_PROVIDER_DEEPSEEK -> "https://api.deepseek.com";
                case CHAT_PROVIDER_DOUBAO_ARK -> "https://ark.cn-beijing.volces.com";
                default -> "https://api.openai.com";
            };
        }

        String chatPath = normalizeText(draft.chatPath, false);
        if (!hasText(chatPath)) {
            chatPath = switch (chatProviderType) {
                case CHAT_PROVIDER_DEEPSEEK -> "/chat/completions";
                case CHAT_PROVIDER_DOUBAO_ARK -> "/api/v3/responses";
                default -> "/v1/chat/completions";
            };
        }

        String defaultModel = normalizeText(draft.defaultModel, false);
        if (!hasText(defaultModel) && CHAT_PROVIDER_DEEPSEEK.equals(chatProviderType)) {
            defaultModel = "deepseek-chat";
        }

        String embeddingProviderType = normalizeText(draft.embeddingProviderType, true);
        if (!hasText(embeddingProviderType)) {
            embeddingProviderType = EMBEDDING_PROVIDER_OPENAI_COMPATIBLE;
        }

        String embeddingBaseUrl = normalizeText(draft.embeddingBaseUrl, false);
        if (!hasText(embeddingBaseUrl)) {
            embeddingBaseUrl = baseUrl;
        }

        String embeddingPath = normalizeText(draft.embeddingPath, false);
        if (!hasText(embeddingPath)) {
            embeddingPath = hasText(chatPath) && chatPath.contains("/chat/completions")
                    ? chatPath.replace("/chat/completions", "/embeddings")
                    : "/v1/embeddings";
        }

        String apiKey = normalizeText(draft.apiKey, false);
        String embeddingApiKey = normalizeText(draft.embeddingApiKey, false);
        if (!hasText(embeddingApiKey)) {
            embeddingApiKey = apiKey;
        }

        String defaultEmbeddingModel = normalizeText(draft.defaultEmbeddingModel, false);
        if (!hasText(defaultEmbeddingModel)) {
            defaultEmbeddingModel = defaultModel;
        }

        return new ResolvedAiConfig(
                draft.enabled,
                draft.mockMode,
                chatProviderType,
                baseUrl,
                chatPath,
                embeddingProviderType,
                embeddingBaseUrl,
                embeddingPath,
                apiKey,
                embeddingApiKey,
                defaultModel,
                defaultEmbeddingModel
        );
    }

    private ConfigDraft mergeDraft(ConfigDraft override, ConfigDraft fallback, boolean allowFallbackSecrets) {
        ConfigDraft result = new ConfigDraft();
        ConfigDraft safeOverride = override == null ? new ConfigDraft() : override;
        ConfigDraft safeFallback = fallback == null ? new ConfigDraft() : fallback;
        result.enabled = firstNonNull(safeOverride.enabled, safeFallback.enabled);
        result.mockMode = firstNonNull(safeOverride.mockMode, safeFallback.mockMode);
        result.chatProviderType = pickText(safeOverride.chatProviderType, safeFallback.chatProviderType);
        result.baseUrl = pickText(safeOverride.baseUrl, safeFallback.baseUrl);
        result.chatPath = pickText(safeOverride.chatPath, safeFallback.chatPath);
        result.defaultModel = pickText(safeOverride.defaultModel, safeFallback.defaultModel);
        result.embeddingProviderType = pickText(safeOverride.embeddingProviderType, safeFallback.embeddingProviderType);
        result.embeddingBaseUrl = pickText(safeOverride.embeddingBaseUrl, safeFallback.embeddingBaseUrl);
        result.embeddingPath = pickText(safeOverride.embeddingPath, safeFallback.embeddingPath);
        result.defaultEmbeddingModel = pickText(safeOverride.defaultEmbeddingModel, safeFallback.defaultEmbeddingModel);
        result.apiKey = pickSecret(safeOverride.apiKey, allowFallbackSecrets ? safeFallback.apiKey : null);
        result.embeddingApiKey = pickSecret(safeOverride.embeddingApiKey, allowFallbackSecrets ? safeFallback.embeddingApiKey : null);
        return result;
    }

    private ConfigDraft applySharedSecretsWhenEndpointMatches(ConfigDraft draft, ConfigDraft sharedConfig) {
        ConfigDraft result = copyDraft(draft);
        if (sharedConfig == null) {
            return result;
        }
        ResolvedAiConfig sharedResolved = toResolvedConfig(sharedConfig);
        if (!hasText(result.apiKey)
                && hasText(sharedResolved.apiKey())
                && usesSameChatEndpoint(result, sharedConfig)) {
            result.apiKey = sharedResolved.apiKey();
        }
        if (!hasText(result.embeddingApiKey)
                && hasText(sharedResolved.embeddingApiKey())
                && usesSameEmbeddingEndpoint(result, sharedConfig)) {
            result.embeddingApiKey = sharedResolved.embeddingApiKey();
        }
        return result;
    }

    private boolean usesSameChatEndpoint(ConfigDraft draft, ConfigDraft sharedConfig) {
        ResolvedAiConfig current = toResolvedConfig(draft);
        ResolvedAiConfig shared = toResolvedConfig(sharedConfig);
        return sameConfigValue(current.chatProviderType(), shared.chatProviderType())
                && sameUrl(current.baseUrl(), shared.baseUrl())
                && samePath(current.chatPath(), shared.chatPath())
                && sameConfigValue(current.defaultModel(), shared.defaultModel());
    }

    private boolean usesSameEmbeddingEndpoint(ConfigDraft draft, ConfigDraft sharedConfig) {
        ResolvedAiConfig current = toResolvedConfig(draft);
        ResolvedAiConfig shared = toResolvedConfig(sharedConfig);
        return sameConfigValue(current.embeddingProviderType(), shared.embeddingProviderType())
                && sameUrl(current.embeddingBaseUrl(), shared.embeddingBaseUrl())
                && samePath(current.embeddingPath(), shared.embeddingPath())
                && sameConfigValue(current.defaultEmbeddingModel(), shared.defaultEmbeddingModel());
    }

    private ConfigDraft copyDraft(ConfigDraft draft) {
        ConfigDraft source = draft == null ? new ConfigDraft() : draft;
        ConfigDraft target = new ConfigDraft();
        target.enabled = source.enabled;
        target.mockMode = source.mockMode;
        target.chatProviderType = source.chatProviderType;
        target.baseUrl = source.baseUrl;
        target.chatPath = source.chatPath;
        target.apiKey = source.apiKey;
        target.defaultModel = source.defaultModel;
        target.embeddingProviderType = source.embeddingProviderType;
        target.embeddingBaseUrl = source.embeddingBaseUrl;
        target.embeddingPath = source.embeddingPath;
        target.embeddingApiKey = source.embeddingApiKey;
        target.defaultEmbeddingModel = source.defaultEmbeddingModel;
        return target;
    }

    private boolean sameConfigValue(String left, String right) {
        String normalizedLeft = normalizeText(left, false);
        String normalizedRight = normalizeText(right, false);
        if (!hasText(normalizedLeft) && !hasText(normalizedRight)) {
            return true;
        }
        return normalizedLeft != null && normalizedLeft.equals(normalizedRight);
    }

    private boolean sameUrl(String left, String right) {
        return sameConfigValue(trimTrailingSlash(left), trimTrailingSlash(right));
    }

    private boolean samePath(String left, String right) {
        return sameConfigValue(normalizePath(left), normalizePath(right));
    }

    private String trimTrailingSlash(String value) {
        String normalized = normalizeText(value, false);
        if (!hasText(normalized)) {
            return null;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizePath(String value) {
        String normalized = normalizeText(value, false);
        if (!hasText(normalized)) {
            return null;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private boolean isUsableConfig(ConfigDraft draft) {
        ResolvedAiConfig config = toResolvedConfig(draft);
        if (!Boolean.TRUE.equals(config.enabled())) {
            return true;
        }
        if (Boolean.TRUE.equals(config.mockMode())) {
            return true;
        }
        return hasText(config.apiKey());
    }

    private ConfigDraft fromEntity(AiConfig entity) {
        ConfigDraft draft = new ConfigDraft();
        draft.enabled = entity.getEnabled();
        draft.mockMode = entity.getMockMode();
        draft.chatProviderType = entity.getChatProviderType();
        draft.baseUrl = entity.getBaseUrl();
        draft.chatPath = entity.getChatPath();
        draft.apiKey = entity.getApiKey();
        draft.defaultModel = entity.getDefaultModel();
        draft.embeddingProviderType = entity.getEmbeddingProviderType();
        draft.embeddingBaseUrl = entity.getEmbeddingBaseUrl();
        draft.embeddingPath = entity.getEmbeddingPath();
        draft.embeddingApiKey = entity.getEmbeddingApiKey();
        draft.defaultEmbeddingModel = entity.getDefaultEmbeddingModel();
        return draft;
    }

    private ConfigDraft fromProperties() {
        ConfigDraft draft = new ConfigDraft();
        draft.enabled = aiProperties.getEnabled();
        draft.mockMode = aiProperties.getMockMode();
        draft.chatProviderType = aiProperties.getChatProviderType();
        draft.baseUrl = aiProperties.getBaseUrl();
        draft.chatPath = aiProperties.getChatPath();
        draft.apiKey = aiProperties.getApiKey();
        draft.defaultModel = aiProperties.getDefaultModel();
        draft.embeddingProviderType = aiProperties.getEmbeddingProviderType();
        draft.embeddingBaseUrl = aiProperties.getEmbeddingBaseUrl();
        draft.embeddingPath = aiProperties.getEmbeddingPath();
        draft.embeddingApiKey = aiProperties.getEmbeddingApiKey();
        draft.defaultEmbeddingModel = aiProperties.getDefaultEmbeddingModel();
        return draft;
    }

    private ConfigDraft loadLegacyConfig() {
        Path configPath = resolveConfigPath();
        if (!Files.exists(configPath)) {
            return new ConfigDraft();
        }
        try {
            return objectMapper.readValue(configPath.toFile(), ConfigDraft.class);
        } catch (IOException exception) {
            throw new BusinessException(500, "读取 AI 配置失败: " + exception.getMessage());
        }
    }

    private Path resolveConfigPath() {
        if (hasText(aiProperties.getConfigFile())) {
            return Paths.get(aiProperties.getConfigFile()).toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.dir"), "runtime", "ai-config.json").toAbsolutePath();
    }

    private AiConfig findConfig(String scope, Long userId) {
        return aiConfigMapper.selectOne(new LambdaQueryWrapper<AiConfig>()
                .eq(AiConfig::getScope, scope)
                .eq(AiConfig::getUserId, userId)
                .last("limit 1"));
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        return ROLE_ADMIN.equalsIgnoreCase(normalizeText(user.getRoleCode(), false))
                || ADMIN_USERNAME.equalsIgnoreCase(normalizeText(user.getUsername(), false));
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private void requireAdmin(Long userId) {
        requireUser(userId);
        if (!isAdmin(userId)) {
            throw new BusinessException(403, "只有管理员可以维护共享 AI 配置");
        }
    }

    private boolean isSupportedChatProvider(String providerType) {
        if (!hasText(providerType)) {
            return true;
        }
        String normalized = providerType.trim().toUpperCase();
        return CHAT_PROVIDER_OPENAI_COMPATIBLE.equals(normalized)
                || CHAT_PROVIDER_DEEPSEEK.equals(normalized)
                || CHAT_PROVIDER_DOUBAO_ARK.equals(normalized);
    }

    private boolean hasAnyValue(ConfigDraft draft) {
        if (draft == null) {
            return false;
        }
        return draft.enabled != null
                || draft.mockMode != null
                || hasText(draft.chatProviderType)
                || hasText(draft.baseUrl)
                || hasText(draft.chatPath)
                || hasText(draft.apiKey)
                || hasText(draft.defaultModel)
                || hasText(draft.embeddingProviderType)
                || hasText(draft.embeddingBaseUrl)
                || hasText(draft.embeddingPath)
                || hasText(draft.embeddingApiKey)
                || hasText(draft.defaultEmbeddingModel);
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

    private String normalizeText(String value, boolean upperCase) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return upperCase ? normalized.toUpperCase() : normalized;
    }

    private String pickText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String pickSecret(String... values) {
        return pickText(values);
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Boolean bool(ConfigDraft draft, String fieldName) {
        if (draft == null) {
            return null;
        }
        return "enabled".equals(fieldName) ? draft.enabled : draft.mockMode;
    }

    private String text(ConfigDraft draft, String fieldName) {
        if (draft == null) {
            return null;
        }
        return switch (fieldName) {
            case "chatProviderType" -> draft.chatProviderType;
            case "baseUrl" -> draft.baseUrl;
            case "chatPath" -> draft.chatPath;
            case "apiKey" -> draft.apiKey;
            case "defaultModel" -> draft.defaultModel;
            case "embeddingProviderType" -> draft.embeddingProviderType;
            case "embeddingBaseUrl" -> draft.embeddingBaseUrl;
            case "embeddingPath" -> draft.embeddingPath;
            case "embeddingApiKey" -> draft.embeddingApiKey;
            case "defaultEmbeddingModel" -> draft.defaultEmbeddingModel;
            default -> null;
        };
    }

    private record EffectiveConfig(ConfigDraft config, String source) {
    }

    private static class ConfigDraft {

        private Boolean enabled;
        private Boolean mockMode;
        private String chatProviderType;
        private String baseUrl;
        private String chatPath;
        private String apiKey;
        private String defaultModel;
        private String embeddingProviderType;
        private String embeddingBaseUrl;
        private String embeddingPath;
        private String embeddingApiKey;
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

        public String getEmbeddingApiKey() {
            return embeddingApiKey;
        }

        public void setEmbeddingApiKey(String embeddingApiKey) {
            this.embeddingApiKey = embeddingApiKey;
        }

        public String getDefaultEmbeddingModel() {
            return defaultEmbeddingModel;
        }

        public void setDefaultEmbeddingModel(String defaultEmbeddingModel) {
            this.defaultEmbeddingModel = defaultEmbeddingModel;
        }
    }
}
