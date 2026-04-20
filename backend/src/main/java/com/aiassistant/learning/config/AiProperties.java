package com.aiassistant.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

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

    private String configFile;
}
