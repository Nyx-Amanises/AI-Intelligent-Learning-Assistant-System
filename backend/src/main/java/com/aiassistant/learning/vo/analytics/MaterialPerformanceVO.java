package com.aiassistant.learning.vo.analytics;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialPerformanceVO {

    private Long materialId;

    private String materialTitle;

    private Integer practiceCount;

    private Integer attemptCount;

    private Integer wrongCount;

    private BigDecimal accuracyRate;

    private BigDecimal scoreRate;
}
