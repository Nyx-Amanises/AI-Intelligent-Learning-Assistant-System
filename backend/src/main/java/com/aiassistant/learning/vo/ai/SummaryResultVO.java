package com.aiassistant.learning.vo.ai;

import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 摘要生成结果返回对象。
 */
@Data
@Builder
public class SummaryResultVO {

    /** 生成时固定的内容来源：MOCK（模拟数据）、LIVE（真实模型）、UNKNOWN（历史来源未知）。 */
    private String generationMode;

    /**
     * 资料 ID。
     */
    private Long materialId;

    /**
     * AI 生成记录 ID。
     */
    private Long recordId;

    /**
     * 如果保存为笔记，这里返回笔记 ID。
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
