package com.aiassistant.learning.vo.question;

import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 题集详情返回对象。
 *
 * <p>详情页除了题集基础信息，还会返回题目列表和用于说明出题依据的资料片段。</p>
 */
@Data
@Builder
public class QuestionSetDetailVO {

    /**
     * 题集 ID。
     */
    private Long id;

    /**
     * 关联资料 ID。
     */
    private Long materialId;

    /**
     * 题集标题。
     */
    private String title;

    /**
     * 题目数量。
     */
    private Integer questionCount;

    /**
     * 总分。
     */
    private Integer totalScore;

    /**
     * 难度等级。
     */
    private Integer difficultyLevel;

    /**
     * 题集状态。
     */
    private String status;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 题集生成时实际使用的资料片段快照，不随资料修改或重新检索变化。
     */
    private List<RetrievedSegmentVO> sourceSegments;

    /** GENERATION_SNAPSHOT 为出题时快照；UNAVAILABLE 表示没有可靠的历史快照。 */
    private String sourceMode;

    /**
     * 题目列表。
     */
    private List<QuestionItemVO> questions;
}
