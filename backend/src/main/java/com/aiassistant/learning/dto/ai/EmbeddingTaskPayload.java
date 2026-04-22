package com.aiassistant.learning.dto.ai;

import lombok.Data;

/**
 * 资料向量化任务载荷。
 *
 * <p>任务中心会把这个对象序列化为 JSON 保存到 ai_task.payload_json。</p>
 */
@Data
public class EmbeddingTaskPayload {

    /**
     * 资料 ID。
     */
    private Long materialId;

    /**
     * 向量嵌入模型名称。
     */
    private String modelName;

    /**
     * 是否强制重新生成。
     */
    private Boolean forceRegenerate;
}
