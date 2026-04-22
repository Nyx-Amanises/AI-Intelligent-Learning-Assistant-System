package com.aiassistant.learning.vo.ai;

import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 摘要历史记录返回对象。
 */
@Data
@Builder
public class SummaryHistoryVO {

    /**
     * AI 生成记录 ID。
     */
    private Long recordId;

    /**
     * 资料 ID。
     */
    private Long materialId;

    /**
     * 资料标题。
     */
    private String materialTitle;

    /**
     * 关联笔记 ID。
     */
    private Long noteId;

    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 摘要类型。
     */
    private String summaryType;

    /**
     * 摘要正文。
     */
    private String summaryText;

    /**
     * 生成摘要时参考的资料片段。
     */
    private List<RetrievedSegmentVO> sourceSegments;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
