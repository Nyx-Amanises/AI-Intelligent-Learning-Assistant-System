package com.aiassistant.learning.vo.ai;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 资料向量化任务结果。
 */
@Data
@Builder
public class EmbeddingTaskResultVO {

    /**
     * 资料 ID。
     */
    private Long materialId;

    /**
     * 资料标题。
     */
    private String materialTitle;

    /**
     * 使用的向量模型名称。
     */
    private String modelName;

    /**
     * 资料总分段数。
     */
    private Integer totalSegments;

    /**
     * 本次加入向量化队列的分段数。
     */
    private Integer queuedSegments;

    /**
     * 本次跳过的分段数。
     */
    private Integer skippedSegments;

    /**
     * 已成功存储向量的分段数。
     */
    private Integer storedSegments;

    /**
     * 向量库集合名称。
     */
    private String collectionName;

    /**
     * 向量库是否可用。
     */
    private Boolean vectorStoreReady;

    /**
     * 任务创建时间。
     */
    private LocalDateTime createdAt;
}
