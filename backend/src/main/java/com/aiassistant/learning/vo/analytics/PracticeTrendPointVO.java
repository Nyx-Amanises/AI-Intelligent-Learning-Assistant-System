package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 练习趋势图中的单个数据点。
 */
@Data
@Builder
public class PracticeTrendPointVO {

    /**
     * 练习会话 ID。
     */
    private Long sessionId;

    /**
     * 练习名称。
     */
    private String sessionName;

    /**
     * 关联资料 ID。
     */
    private Long materialId;

    /**
     * 关联资料标题。
     */
    private String materialTitle;

    /**
     * 总题数。
     */
    private Integer totalQuestions;

    /**
     * 正确题数。
     */
    private Integer correctCount;

    /**
     * 错误题数。
     */
    private Integer wrongCount;

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
     * 得分率。
     */
    private BigDecimal scoreRate;

    /**
     * 提交时间。
     */
    private LocalDateTime submitTime;
}
