package com.aiassistant.learning.vo.analytics;

import java.time.LocalDate;
import java.util.List;

/** 完整时间范围内的练习数量趋势；undatedCount 表示未计入时间轴的记录。 */
public record PracticeCountTrendVO(
        String granularity,
        LocalDate startDate,
        LocalDate endDate,
        List<PracticeCountPointVO> points,
        long undatedCount
) {
    public PracticeCountTrendVO {
        points = List.copyOf(points);
    }
}
