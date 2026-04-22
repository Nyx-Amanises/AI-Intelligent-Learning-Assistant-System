package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.AiConfigUpdateRequest;
import com.aiassistant.learning.vo.ai.AiConfigVO;

/**
 * AI 配置服务接口。
 *
 * <p>负责把配置文件中的默认值、数据库中的覆盖值合并成运行时真正使用的配置。</p>
 */
public interface AiConfigService {

    /**
     * 查询当前 AI 配置，用于管理页面展示。
     */
    AiConfigVO getConfig();

    /**
     * 更新 AI 配置。
     */
    AiConfigVO updateConfig(AiConfigUpdateRequest request);

    /**
     * 获取已经解析好的运行时配置。
     */
    ResolvedAiConfig getResolvedConfig();

    /**
     * 运行时 AI 配置快照。
     *
     * <p>record 是 Java 里的不可变数据载体，适合用来表达“只读的一组配置值”。</p>
     */
    record ResolvedAiConfig(
            /** AI 功能总开关。 */
            Boolean enabled,
            /** 是否使用模拟模式；模拟模式不会真正请求大模型。 */
            Boolean mockMode,
            /** 聊天模型提供商类型，例如 openai-compatible。 */
            String chatProviderType,
            /** 聊天接口基础地址。 */
            String baseUrl,
            /** 聊天接口路径。 */
            String chatPath,
            /** Embedding 模型提供商类型。 */
            String embeddingProviderType,
            /** Embedding 接口基础地址。 */
            String embeddingBaseUrl,
            /** Embedding 接口路径。 */
            String embeddingPath,
            /** 聊天模型 API Key。 */
            String apiKey,
            /** Embedding 模型 API Key。 */
            String embeddingApiKey,
            /** 默认聊天模型名称。 */
            String defaultModel,
            /** 默认 Embedding 模型名称。 */
            String defaultEmbeddingModel
    ) {
    }
}
