package com.aiassistant.learning.dto.analytics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class LearningAnalyticsQuery {

    private Long materialId;

    @Min(value = 5, message = "趋势数量最小为5")
    @Max(value = 30, message = "趋势数量最大为30")
    private Integer trendLimit = 12;
}
