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

    /** 用户输入的查询语句。 */
    private String queryText;

    /** 本次期望返回的最大命中数量。 */
    private Integer limit;

    /** 实际命中的分段数量。 */
    private Integer hitCount;

    /** 命中的资料分段列表。 */
    private List<RetrievedSegmentVO> segments;
}
