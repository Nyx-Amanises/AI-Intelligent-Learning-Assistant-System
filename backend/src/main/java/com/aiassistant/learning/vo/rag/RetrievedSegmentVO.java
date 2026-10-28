package com.aiassistant.learning.vo.rag;

import lombok.Builder;
import lombok.Data;

/**
 * 单个 RAG 检索命中的资料分段。
 */
@Data
@Builder
public class RetrievedSegmentVO {

    /** 资料分段 ID。 */
    private Long segmentId;

    /** 分段在资料中的顺序号。 */
    private Integer segmentNo;

    /** 分段所在页码，文本资料可能为空。 */
    private Integer pageNo;

    /** 分段标题或章节标题。 */
    private String sectionTitle;

    /** 分段正文内容。 */
    private String contentText;

    /** 分段关键词，便于前端辅助展示。 */
    private String keywords;

    /** 检索得分，分数越高表示越相关。 */
    private Double score;

    /** 向量相似度；文本检索时为空。 */
    private Double semanticScore;

    /** 词面覆盖分：问题中的词在分段里出现的比例。 */
    private Double lexicalScore;
}
