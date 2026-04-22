package com.aiassistant.learning.dto.ai;

import lombok.Data;

/**
 * AI 配置更新请求。
 *
 * <p>字段为空时通常表示不覆盖对应配置，具体合并逻辑由 AiConfigServiceImpl 处理。</p>
 */
@Data
public class AiConfigUpdateRequest {

    /**
     * 是否启用真实 AI 能力。
     */
    private Boolean enabled;

    /**
     * 是否启用模拟模式。
     */
    private Boolean mockMode;

    /**
     * 聊天模型服务提供方类型。
     */
    private String chatProviderType;

    /**
     * 聊天模型接口基础地址。
     */
    private String baseUrl;

    /**
     * 聊天模型接口路径。
     */
    private String chatPath;

    /**
     * 聊天模型 API Key。
     */
    private String apiKey;

    /**
     * 默认聊天模型名称。
     */
    private String defaultModel;

    /**
     * 向量嵌入服务提供方类型。
     */
    private String embeddingProviderType;

    /**
     * 向量嵌入接口基础地址。
     */
    private String embeddingBaseUrl;

    /**
     * 向量嵌入接口路径。
     */
    private String embeddingPath;

    /**
     * 向量嵌入 API Key。
     */
    private String embeddingApiKey;

    /**
     * 默认向量嵌入模型名称。
     */
    private String defaultEmbeddingModel;
}
