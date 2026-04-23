package com.aiassistant.learning.vo.rag;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 单次 RAG 评测运行的汇总结果。
 */
@Data
@Builder
public class RagEvalRunVO {

    /** 评测运行 ID。 */
    private Long id;

    /** 所属评测集 ID。 */
    private Long datasetId;

    /** 被评测的资料 ID。 */
    private Long materialId;

    /** 运行状态，例如 SUCCESS、FAILED、PARTIAL_FAILED。 */
    private String status;

    /** 每条样本检索时取回的候选数量。 */
    private Integer retrievalLimit;

    /** 本次应评测的样本总数。 */
    private Integer totalSamples;

    /** 成功完成评测的样本数。 */
    private Integer evaluatedSamples;

    /** 评测失败的样本数。 */
    private Integer failedSamples;

    /** Top 1 命中率。 */
    private Double hitAt1;

    /** Top 3 命中率。 */
    private Double hitAt3;

    /** Top 5 命中率。 */
    private Double hitAt5;

    /** Top 1 召回率。 */
    private Double recallAt1;

    /** Top 3 召回率。 */
    private Double recallAt3;

    /** Top 5 召回率。 */
    private Double recallAt5;

    /** 平均倒数排名，越接近 1 表示相关内容越靠前。 */
    private Double mrr;

    /** 平均检索耗时，单位毫秒。 */
    private Double avgLatencyMs;

    /** 运行级错误信息，通常用于提示部分样本失败。 */
    private String errorMessage;

    /** 运行开始时间。 */
    private LocalDateTime startedAt;

    /** 运行结束时间。 */
    private LocalDateTime finishedAt;

    /** 每条样本的评测明细。 */
    private List<RagEvalRunItemVO> items;
}
