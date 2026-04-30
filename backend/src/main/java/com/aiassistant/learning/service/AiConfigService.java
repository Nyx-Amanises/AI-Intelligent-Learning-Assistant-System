package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.AiConfigUpdateRequest;
import com.aiassistant.learning.vo.ai.AiConfigVO;

/**
 * AI config service.
 */
public interface AiConfigService {

    /**
     * Query the effective AI config for the current user.
     */
    AiConfigVO getConfig(Long userId);

    /**
     * Save the current user's personal AI config.
     */
    AiConfigVO updateConfig(Long userId, AiConfigUpdateRequest request);

    /**
     * Remove the current user's personal AI config and fall back to shared config.
     */
    AiConfigVO clearUserConfig(Long userId);

    /**
     * Query the administrator shared AI config.
     */
    AiConfigVO getGlobalConfig(Long userId);

    /**
     * Save the administrator shared AI config.
     */
    AiConfigVO updateGlobalConfig(Long userId, AiConfigUpdateRequest request);

    /**
     * Resolve runtime config using UserContext.
     */
    ResolvedAiConfig getResolvedConfig();

    /**
     * Resolve runtime config for a specific user.
     */
    ResolvedAiConfig getResolvedConfig(Long userId);

    /**
     * Immutable runtime AI config snapshot.
     */
    record ResolvedAiConfig(
            Boolean enabled,
            Boolean mockMode,
            String chatProviderType,
            String baseUrl,
            String chatPath,
            String embeddingProviderType,
            String embeddingBaseUrl,
            String embeddingPath,
            String apiKey,
            String embeddingApiKey,
            String defaultModel,
            String defaultEmbeddingModel
    ) {
    }
}
