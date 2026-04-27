package com.aiassistant.learning.dto.analytics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 学习分析查询参数。
 */
@Data
public class LearningAnalyticsQuery {

    /**
     * 按资料 ID 筛选统计范围。
     */
    private Long materialId;

    /**
     * 趋势图最多展示的练习记录数量。
     */
    @Min(value = 5, message = "趋势数量最小为5")
    @Max(value = 30, message = "趋势数量最大为30")
    private Integer trendLimit = 12;

    @Min(value = 1, message = "统计天数最小为1")
    @Max(value = 365, message = "统计天数最大为365")
    private Integer days;
}
