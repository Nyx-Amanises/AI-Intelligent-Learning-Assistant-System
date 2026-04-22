package com.aiassistant.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qdrant 向量数据库配置。
 *
 * <p>系统把学习资料切分成文本片段后，会将片段向量保存到 Qdrant，
 * 后续检索增强生成（RAG）可以根据问题召回相关片段。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.qdrant")
public class QdrantProperties {

    /**
     * 是否启用 Qdrant 向量存储。
     */
    private Boolean enabled;

    /**
     * Qdrant 服务基础地址。
     */
    private String baseUrl;

    /**
     * Qdrant API Key；如果本地服务未开启鉴权，可以为空。
     */
    private String apiKey;

    /**
     * 存放学习资料向量的集合名称。
     */
    private String collectionName;

    /**
     * 向量距离计算方式，例如 Cosine 表示余弦相似度。
     */
    private String distance;

    /**
     * 批量写入向量时每批的数量，避免一次请求过大。
     */
    private Integer upsertBatchSize;

    /**
     * 检索时返回的最大片段数量。
     */
    private Integer retrievalLimit;
}
