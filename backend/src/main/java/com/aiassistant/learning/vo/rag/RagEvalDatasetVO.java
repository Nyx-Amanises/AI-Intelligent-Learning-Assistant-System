package com.aiassistant.learning.vo.rag;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * RAG 评测集展示对象。
 */
@Data
@Builder
public class RagEvalDatasetVO {

    /** 评测集 ID。 */
    private Long id;

    /** 评测集绑定的资料 ID。 */
    private Long materialId;

    /** 资料标题，前端展示时比单独 ID 更友好。 */
    private String materialTitle;

    /** 评测集名称。 */
    private String name;

    /** 评测集说明。 */
    private String description;

    /** 评测集状态，例如 ACTIVE。 */
    private String status;

    /** 当前评测集下的样本数量。 */
    private Integer sampleCount;

    /** 最近一次评测运行 ID。 */
    private Long lastRunId;

    /** 最近一次评测完成时间。 */
    private LocalDateTime lastRunAt;

    /** 评测集创建时间。 */
    private LocalDateTime createdAt;
}
