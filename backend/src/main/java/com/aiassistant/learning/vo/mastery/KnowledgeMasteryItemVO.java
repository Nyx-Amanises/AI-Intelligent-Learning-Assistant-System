package com.aiassistant.learning.vo.mastery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 单个知识点掌握度展示对象。
 */
@Data
@Builder
public class KnowledgeMasteryItemVO {

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
     * 该知识点被练习的次数。
     */
    private Integer attemptCount;

    /**
     * 涉及的不同题目数量。
     */
    private Integer uniqueQuestionCount;

    /**
     * 正确次数。
     */
    private Integer correctCount;

    /**
     * 错误次数。
     */
    private Integer wrongCount;

    /**
     * 相关题目总分。
     */
    private Integer totalScore;

    /**
     * 用户获得分数。
     */
    private Integer obtainedScore;

    /**
     * 正确率百分比。
     */
    private BigDecimal accuracyRate;

    /**
     * 得分率百分比。
     */
    private BigDecimal scoreRate;

    /**
     * 掌握度百分比，优先使用得分率计算。
     */
    private Integer masteryPercent;

    /**
     * 掌握等级编码，例如 MASTERED、GOOD、WEAK、RISK。
     */
    private String masteryLevel;

    /**
     * 掌握等级中文标签。
     */
    private String masteryLabel;

    /**
     * 针对该知识点的学习建议。
     */
    private String suggestion;

    /**
     * 涉及题型，多个题型用逗号拼接。
     */
    private String questionTypes;

    /**
     * 最近一次练习该知识点的时间。
     */
    private LocalDateTime lastPracticeTime;
}
