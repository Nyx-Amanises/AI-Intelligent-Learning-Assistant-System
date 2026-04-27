package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 学习分析总览返回对象。
 */
@Data
@Builder
public class LearningAnalyticsOverviewVO {

    /**
     * 已提交练习次数。
     */
    private Integer totalPracticeCount;

    /**
     * 总作答题次数。
     */
    private Integer totalQuestionAttempts;

    /**
     * 错误作答次数。
     */
    private Integer wrongAttemptCount;

    /**
     * 累计练习耗时，单位秒。
     */
    private Long totalStudySeconds;

    /**
     * 涉及知识点总数。
     */
    private Integer totalKnowledgePoints;

    /**
     * 薄弱知识点数量。
     */
    private Integer weakKnowledgePointCount;

    /**
     * 平均正确率。
     */
    private BigDecimal averageAccuracyRate;

    /**
     * 平均得分率。
     */
    private BigDecimal averageScoreRate;

    /**
     * 掌握度等级分布。
     */
    private List<MasteryDistributionVO> masteryDistribution;

    /**
     * 不同题型的表现统计。
     */
    private List<QuestionTypePerformanceVO> questionTypePerformance;

    /**
     * 不同学习资料的练习表现统计。
     */
    private List<MaterialPerformanceVO> materialPerformance;

    /**
     * 最近练习趋势。
     */
    private List<PracticeTrendPointVO> practiceTrend;

    /**
     * 薄弱知识点列表。
     */
    private List<WeakKnowledgePointVO> weakKnowledgePoints;
}
