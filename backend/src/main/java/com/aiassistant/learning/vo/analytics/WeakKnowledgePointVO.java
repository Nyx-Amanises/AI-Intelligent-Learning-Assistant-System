package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WeakKnowledgePointVO {

    private String knowledgePoint;

    private Long materialId;

    private String materialTitle;

    private Integer attemptCount;

    private Integer wrongCount;

    private Integer masteryPercent;

    private BigDecimal scoreRate;

    private LocalDateTime lastPracticeTime;
}
