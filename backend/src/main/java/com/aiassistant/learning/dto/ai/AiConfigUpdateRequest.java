package com.aiassistant.learning.dto.ai;

import lombok.Data;

@Data
public class AiConfigUpdateRequest {

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
}
