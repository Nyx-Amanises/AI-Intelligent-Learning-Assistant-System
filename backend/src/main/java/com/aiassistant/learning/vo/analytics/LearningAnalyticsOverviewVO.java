package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LearningAnalyticsOverviewVO {

    private Integer totalPracticeCount;

    private Integer totalQuestionAttempts;

    private Integer wrongAttemptCount;

    private Integer totalKnowledgePoints;

    private Integer weakKnowledgePointCount;

    private BigDecimal averageAccuracyRate;

    private BigDecimal averageScoreRate;

    private List<MasteryDistributionVO> masteryDistribution;

    private List<QuestionTypePerformanceVO> questionTypePerformance;

    private List<MaterialPerformanceVO> materialPerformance;

    private List<PracticeTrendPointVO> practiceTrend;

    private List<WeakKnowledgePointVO> weakKnowledgePoints;
}
