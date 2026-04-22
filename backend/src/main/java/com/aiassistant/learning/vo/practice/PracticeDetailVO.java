package com.aiassistant.learning.vo.practice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 练习详情返回对象。
 *
 * <p>用于开始练习、提交练习和查看练习详情等接口。</p>
 */
@Data
@Builder
public class PracticeDetailVO {

    /**
     * 练习会话 ID。
     */
    private Long sessionId;

    /**
     * 题集 ID。
     */
    private Long questionSetId;

    /**
     * 练习名称。
     */
    private String sessionName;

    /**
     * 总题数。
     */
    private Integer totalQuestions;

    /**
     * 正确题数。
     */
    private Integer correctCount;

    /**
     * 总分。
     */
    private Integer totalScore;

    /**
     * 获得分数。
     */
    private Integer obtainedScore;

    /**
     * 正确率。
     */
    private BigDecimal accuracyRate;

    /**
     * 练习状态。
     */
    private String sessionStatus;

    /**
     * 开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 提交时间。
     */
    private LocalDateTime submitTime;

    /**
     * 每道题的作答详情。
     */
    private List<PracticeAnswerVO> answers;
}
