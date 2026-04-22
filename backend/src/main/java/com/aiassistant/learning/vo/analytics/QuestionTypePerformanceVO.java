package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * 按题型统计的练习表现。
 */
@Data
@Builder
public class QuestionTypePerformanceVO {

    /**
     * 题型编码。
     */
    private String questionType;

    /**
     * 题型中文标签。
     */
    private String questionTypeLabel;

    /**
     * 作答次数。
     */
    private Integer attemptCount;

    /**
     * 正确次数。
     */
    private Integer correctCount;

    /**
     * 错误次数。
     */
    private Integer wrongCount;

    /**
     * 正确率。
     */
    private BigDecimal accuracyRate;

    /**
     * 得分率。
     */
    private BigDecimal scoreRate;
}
