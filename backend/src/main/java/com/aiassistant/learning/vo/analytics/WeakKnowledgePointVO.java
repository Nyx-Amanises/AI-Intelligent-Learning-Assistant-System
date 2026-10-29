package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 薄弱知识点返回对象。
 */
@Data
@Builder
public class WeakKnowledgePointVO {

    /**
     * 知识点名称。
     */
    private String knowledgePoint;

    /**
     * 关联资料 ID。
     */
    private Long materialId;

    /**
     * 关联资料标题。
     */
    private String materialTitle;

    /**
     * 该知识点作答次数。
     */
    private Integer attemptCount;

    /** 已完成有效评分的不同题目数量；重复作答同题只计一道。 */
    private Integer uniqueQuestionCount;
    /** 判定掌握等级所需的最少不同已评分题目数。 */
    private Integer requiredQuestionCount;
    /** 样本充足且为真实知识点时才可判定掌握等级。 */
    private Boolean assessmentEligible;
    /** 本次评估或暂不评估的原因。 */
    private String assessmentReason;
    private String masteryLevel;
    private String masteryLabel;

    /**
     * 错误次数。
     */
    private Integer wrongCount;

    /**
     * 掌握度百分比。
     */
    private Integer masteryPercent;

    /**
     * 得分率。
     */
    private BigDecimal scoreRate;

    /**
     * 最近练习时间。
     */
    private LocalDateTime lastPracticeTime;
}
