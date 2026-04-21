package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PracticeTrendPointVO {

    private Long sessionId;

    private String sessionName;

    private Long materialId;

    private String materialTitle;

    private Integer totalQuestions;

    private Integer correctCount;

    private Integer wrongCount;

    private Integer totalScore;

    private Integer obtainedScore;

    private BigDecimal accuracyRate;

    private BigDecimal scoreRate;

    private LocalDateTime submitTime;
}
