package com.aiassistant.learning.vo.ai;

import lombok.Builder;
import lombok.Data;

/**
 * AI 配置展示对象。
 *
 * <p>出于安全考虑，返回给前端时不会直接暴露完整 API Key，
 * 只返回是否已配置和脱敏预览。</p>
 */
@Data
@Builder
public class AiConfigVO {

    /**
     * 是否启用 AI。
     */
    private Boolean enabled;

    /**
     * 是否启用模拟模式。
     */
    private Boolean mockMode;

    /**
     * 聊天模型服务提供方。
     */
    private String chatProviderType;

    /**
     * 聊天接口基础地址。
     */
    private String baseUrl;

    /**
     * 聊天接口路径。
     */
    private String chatPath;

    /**
     * 默认聊天模型。
     */
    private String defaultModel;

    /**
     * 是否已配置聊天 API Key。
     */
    private Boolean apiKeyConfigured;

    /**
     * 脱敏后的聊天 API Key 预览。
     */
    private String apiKeyPreview;

    /**
     * 向量嵌入服务提供方。
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
     * 默认向量嵌入模型。
     */
    private String defaultEmbeddingModel;

    /**
     * 是否已配置向量嵌入 API Key。
     */
    private Boolean embeddingApiKeyConfigured;

    /**
     * 脱敏后的向量嵌入 API Key 预览。
     */
    private String embeddingApiKeyPreview;
}
