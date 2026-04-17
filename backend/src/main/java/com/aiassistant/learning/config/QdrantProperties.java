package com.aiassistant.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.qdrant")
public class QdrantProperties {

    private Boolean enabled;

    private String baseUrl;

    private String apiKey;

    private String collectionName;

    private String distance;

    private Integer upsertBatchSize;

    private Integer retrievalLimit;
}
