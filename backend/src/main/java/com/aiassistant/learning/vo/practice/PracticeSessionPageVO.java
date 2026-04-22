package com.aiassistant.learning.vo.practice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 练习记录列表项返回对象。
 */
@Data
@Builder
public class PracticeSessionPageVO {

    /**
     * 练习会话 ID。
     */
    private Long id;

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
     * 提交时间。
     */
    private LocalDateTime submitTime;
}
