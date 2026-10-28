package com.aiassistant.learning.vo.rag;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * RAG 检索预览结果。
 */
@Data
@Builder
public class RetrievalPreviewVO {

    /** 被检索的资料 ID。 */
    private Long materialId;

    /** 资料标题。 */
    private String materialTitle;

    /** 用户输入的查询语句。 */
    private String queryText;

    /** 本次期望返回的最大命中数量。 */
    private Integer limit;

    /** 实际命中的分段数量。 */
    private Integer hitCount;

    /** 实际使用的检索方式：VECTOR（向量召回 + 词面重排）或 TEXT（数据库文本检索）。 */
    private String retrievalMode;

    /** 是否因向量检索不可用而降级为文本检索。 */
    private Boolean degraded;

    /** 降级原因，未降级时为空。 */
    private String degradeReason;

    /** 查询向量是否来自 Mock 模式，此时向量相似度没有语义意义。 */
    private Boolean mockEmbedding;

    /** 命中的资料分段列表。 */
    private List<RetrievedSegmentVO> segments;
}
