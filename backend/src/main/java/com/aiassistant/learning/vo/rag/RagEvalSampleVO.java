package com.aiassistant.learning.vo.rag;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * RAG 评测样本展示对象。
 */
@Data
@Builder
public class RagEvalSampleVO {

    /** 样本 ID。 */
    private Long id;

    /** 所属评测集 ID。 */
    private Long datasetId;

    /** 样本实际检索的资料 ID。 */
    private Long materialId;

    /** 资料标题。 */
    private String materialTitle;

    /** 评测问题文本。 */
    private String queryText;

    /** 期望命中的资料分段 ID 列表。 */
    private List<Long> expectedSegmentIds;

    /** 期望命中的页码列表。 */
    private List<Integer> expectedPageNos;

    /** 期望答案关键词，便于人工查看。 */
    private String expectedKeywords;

    /** 样本标签。 */
    private String tag;

    /** 难度等级，通常为 1 到 5。 */
    private Integer difficulty;

    /** 样本来源类型，例如 HUMAN 或 IMPORTED。 */
    private String sourceType;

    /** 样本备注。 */
    private String note;

    /** 样本创建时间。 */
    private LocalDateTime createdAt;
}
