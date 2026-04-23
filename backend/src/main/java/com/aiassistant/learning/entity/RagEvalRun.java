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

/**
 * RAG 评测运行实体，对应数据库表 rag_eval_run。
 *
 * <p>每启动一次评测就生成一条运行记录，用来保存汇总指标和运行状态。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rag_eval_run")
public class RagEvalRun extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属评测集 ID。 */
    private Long datasetId;

    /** 运行所属用户 ID。 */
    private Long userId;

    /** 被评测的资料 ID。 */
    private Long materialId;

    /** 运行状态，例如 RUNNING、SUCCESS、FAILED。 */
    private String status;

    /** 每个问题检索返回的候选数量。 */
    private Integer retrievalLimit;

    /** 本次运行应评测的样本总数。 */
    private Integer totalSamples;

    /** 成功完成评测的样本数。 */
    private Integer evaluatedSamples;

    /** 执行失败的样本数。 */
    private Integer failedSamples;

    /** Top 1 命中率。 */
    @TableField("hit_at_1")
    private Double hitAt1;

    /** Top 3 命中率。 */
    @TableField("hit_at_3")
    private Double hitAt3;

    /** Top 5 命中率。 */
    @TableField("hit_at_5")
    private Double hitAt5;

    /** Top 1 召回率。 */
    @TableField("recall_at_1")
    private Double recallAt1;

    /** Top 3 召回率。 */
    @TableField("recall_at_3")
    private Double recallAt3;

    /** Top 5 召回率。 */
    @TableField("recall_at_5")
    private Double recallAt5;

    /** 平均倒数排名 Mean Reciprocal Rank。 */
    private Double mrr;

    /** 平均检索耗时，单位毫秒。 */
    private Double avgLatencyMs;

    /** 运行错误信息。 */
    private String errorMessage;

    /** 运行开始时间。 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 运行结束时间。 */
    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
