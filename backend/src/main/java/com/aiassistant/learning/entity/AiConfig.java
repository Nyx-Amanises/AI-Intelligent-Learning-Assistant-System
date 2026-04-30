package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 配置实体。
 *
 * <p>GLOBAL 作用域保存管理员共享配置，USER 作用域保存单个用户的个人配置。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_config")
public class AiConfig extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置作用域：GLOBAL / USER。
     */
    private String scope;

    /**
     * GLOBAL 固定为 0，USER 为实际用户 ID。
     */
    private Long userId;

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
