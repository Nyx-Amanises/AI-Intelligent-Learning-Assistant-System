package com.aiassistant.learning.common.util;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 学习分析使用本地日历日：包含起日，止日次日零点为排他上界。null 边界表示全部。 */
public record AnalyticsDateRange(LocalDate startDate, LocalDate endDate) {
    private static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(1000, 1, 1);

    public static AnalyticsDateRange resolve(LearningAnalyticsQuery query, LocalDate today) {
        Integer days = query.getDays();
        LocalDate start = query.getStartDate();
        LocalDate end = query.getEndDate();
        if (days != null && (start != null || end != null)) {
            throw new BusinessException("近N天与自定义日期范围不能同时使用");
        }
        if ((start == null) != (end == null)) {
            throw new BusinessException("开始日期和结束日期必须同时提供");
        }
        if (days != null) {
            if (days < 1 || days > 365) {
                throw new BusinessException("统计天数必须在1至365之间");
            }
            return new AnalyticsDateRange(today.minusDays(days - 1L), today);
        }
        if (start == null) return new AnalyticsDateRange(null, null);
        if (start.isBefore(MIN_SUPPORTED_DATE)) {
            throw new BusinessException("开始日期不能早于1000-01-01");
        }
        if (start.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        if (end.isAfter(today)) {
            throw new BusinessException("结束日期不能晚于今天");
        }
        return new AnalyticsDateRange(start, end);
    }

    public LocalDateTime startInclusive() {
        return startDate == null ? null : startDate.atStartOfDay();
    }

    public LocalDateTime endExclusive() {
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }
}
