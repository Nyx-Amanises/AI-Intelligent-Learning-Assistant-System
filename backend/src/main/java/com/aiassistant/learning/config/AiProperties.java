package com.aiassistant.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 服务相关配置。
 *
 * <p>这些字段会自动绑定 application.yml 中 app.ai 开头的配置。
 * 使用配置类的好处是：业务代码不需要到处读取字符串配置，只要注入这个类即可。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /**
     * 是否启用真实 AI 能力。
     */
    private Boolean enabled;

    /**
     * 是否使用模拟模式。开发或演示时没有真实 API Key，也可以返回模拟结果。
     */
    private Boolean mockMode;

    /**
     * 聊天模型服务提供方类型，用于在不同模型服务之间切换。
     */
    private String chatProviderType;

    /**
     * 聊天模型接口基础地址。
     */
    private String baseUrl;

    /**
     * 聊天补全接口路径。
     */
    private String chatPath;

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
     * 聊天模型 API Key。
     */
    private String apiKey;

    /**
     * 向量嵌入模型 API Key，单独配置便于和聊天模型使用不同服务。
     */
    private String embeddingApiKey;

    /**
     * 默认聊天模型名称。
     */
    private String defaultModel;

    /**
     * 默认向量嵌入模型名称。
     */
    private String defaultEmbeddingModel;

    /**
     * AI 配置文件路径，用于保存或读取运行时配置。
     */
    private String configFile;
}
