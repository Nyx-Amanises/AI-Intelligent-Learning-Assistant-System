package com.aiassistant.learning.dto.ai;

import lombok.Data;

/**
 * 资料向量化任务请求参数。
 */
@Data
public class EmbeddingTaskRequest {

    /**
     * 指定向量嵌入模型名称，空值时使用默认模型。
     */
    private String modelName;

    /**
     * 是否强制重新生成已有向量。
     */
    private Boolean forceRegenerate = false;
}
