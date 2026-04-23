package com.aiassistant.learning.vo.rag;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 单条样本在一次 RAG 评测运行中的明细结果。
 */
@Data
@Builder
public class RagEvalRunItemVO {

    /** 明细记录 ID。 */
    private Long id;

    /** 对应的评测样本 ID。 */
    private Long sampleId;

    /** 样本检索的资料 ID。 */
    private Long materialId;

    /** 本次评测使用的问题文本。 */
    private String queryText;

    /** 标注的期望命中分段 ID。 */
    private List<Long> expectedSegmentIds;

    /** 标注的期望命中页码。 */
    private List<Integer> expectedPageNos;

    /** 实际检索返回的分段 ID。 */
    private List<Long> retrievedSegmentIds;

    /** 实际检索返回的页码。 */
    private List<Integer> retrievedPageNos;

    /** 首次命中的排名；为空表示没有命中。 */
    private Integer hitRank;

    /** 倒数排名，命中第 1 名为 1，第 2 名为 0.5。 */
    private Double reciprocalRank;

    /** 当前样本的 Top 1 召回率。 */
    private Double recallAt1;

    /** 当前样本的 Top 3 召回率。 */
    private Double recallAt3;

    /** 当前样本的 Top 5 召回率。 */
    private Double recallAt5;

    /** 当前样本的检索耗时，单位毫秒。 */
    private Long latencyMs;

    /** 当前样本评测失败时的错误信息。 */
    private String errorMessage;
}
