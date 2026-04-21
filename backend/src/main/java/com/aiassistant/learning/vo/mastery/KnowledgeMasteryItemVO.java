package com.aiassistant.learning.vo.mastery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeMasteryItemVO {

    private String knowledgePoint;

    private Long materialId;

    private String materialTitle;

    private Integer attemptCount;

    private Integer uniqueQuestionCount;

    private Integer correctCount;

    private Integer wrongCount;

    private Integer totalScore;

    private Integer obtainedScore;

    private BigDecimal accuracyRate;

    private BigDecimal scoreRate;

    private Integer masteryPercent;

    private String masteryLevel;

    private String masteryLabel;

    private String suggestion;

    private String questionTypes;

    private LocalDateTime lastPracticeTime;
}
