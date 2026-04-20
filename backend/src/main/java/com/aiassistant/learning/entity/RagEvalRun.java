package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rag_eval_run")
public class RagEvalRun extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private Long userId;

    private Long materialId;

    private String status;

    private Integer retrievalLimit;

    private Integer totalSamples;

    private Integer evaluatedSamples;

    private Integer failedSamples;

    @TableField("hit_at_1")
    private Double hitAt1;

    @TableField("hit_at_3")
    private Double hitAt3;

    @TableField("hit_at_5")
    private Double hitAt5;

    @TableField("recall_at_1")
    private Double recallAt1;

    @TableField("recall_at_3")
    private Double recallAt3;

    @TableField("recall_at_5")
    private Double recallAt5;

    private Double mrr;

    private Double avgLatencyMs;

    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
