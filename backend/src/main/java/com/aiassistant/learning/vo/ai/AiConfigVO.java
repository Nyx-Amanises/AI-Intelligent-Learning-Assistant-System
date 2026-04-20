package com.aiassistant.learning.vo.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiConfigVO {

    private Boolean enabled;

    private Boolean mockMode;

    private String chatProviderType;

    private String baseUrl;

    private String chatPath;

    private String defaultModel;

    private Boolean apiKeyConfigured;

    private String apiKeyPreview;

    private String embeddingProviderType;

    private String embeddingBaseUrl;

    private String embeddingPath;

    private String defaultEmbeddingModel;

    private Boolean embeddingApiKeyConfigured;

    private String embeddingApiKeyPreview;
}
