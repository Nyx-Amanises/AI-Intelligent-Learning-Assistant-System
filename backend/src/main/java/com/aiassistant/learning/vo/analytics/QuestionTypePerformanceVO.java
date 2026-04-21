package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionTypePerformanceVO {

    private String questionType;

    private String questionTypeLabel;

    private Integer attemptCount;

    private Integer correctCount;

    private Integer wrongCount;

    private BigDecimal accuracyRate;

    private BigDecimal scoreRate;
}
