package com.aiassistant.learning.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnalyticsDateRangeTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    @Test
    void allTimeHasNoBoundaries() {
        AnalyticsDateRange range = AnalyticsDateRange.resolve(new LearningAnalyticsQuery(), TODAY);
        assertNull(range.startDate());
        assertNull(range.endDate());
        assertNull(range.startInclusive());
        assertNull(range.endExclusive());
    }

    @Test
    void recentSevenDaysIncludesTodayAndSixPreviousCalendarDays() {
        LearningAnalyticsQuery query = new LearningAnalyticsQuery();
        query.setDays(7);
        AnalyticsDateRange range = AnalyticsDateRange.resolve(query, TODAY);
        assertEquals(LocalDateTime.of(2026, 10, 23, 0, 0), range.startInclusive());
        assertEquals(LocalDateTime.of(2026, 10, 30, 0, 0), range.endExclusive());
    }

    @Test
    void aSingleDayIncludesTheWholeDayAcrossMonthEnd() {
        AnalyticsDateRange range = AnalyticsDateRange.resolve(query("2024-02-29", "2024-02-29"), TODAY);
        assertEquals(LocalDateTime.of(2024, 2, 29, 0, 0), range.startInclusive());
        assertEquals(LocalDateTime.of(2024, 3, 1, 0, 0), range.endExclusive());
    }

    @Test
    void longHistoricalRangesAreNotClampedToRecentDays() {
        AnalyticsDateRange range = AnalyticsDateRange.resolve(query("2018-01-01", "2025-12-31"), TODAY);
        assertEquals(LocalDate.of(2018, 1, 1), range.startDate());
        assertEquals(LocalDate.of(2025, 12, 31), range.endDate());
    }

    @Test
    void databaseDateLowerBoundIsValidatedRatherThanClamped() {
        AnalyticsDateRange earliest = AnalyticsDateRange.resolve(query("1000-01-01", "1000-01-01"), TODAY);
        assertEquals(LocalDate.of(1000, 1, 1), earliest.startDate());
        assertEquals(400, assertThrows(BusinessException.class,
                () -> AnalyticsDateRange.resolve(query("0999-12-31", "1000-01-01"), TODAY)).getCode());
        assertEquals(400, assertThrows(BusinessException.class,
                () -> AnalyticsDateRange.resolve(query("-0001-01-01", "1000-01-01"), TODAY)).getCode());
    }

    @Test
    void rejectsPartialReversedFutureAndConflictingRanges() {
        assertThrows(BusinessException.class, () -> AnalyticsDateRange.resolve(query("2026-10-01", null), TODAY));
        assertThrows(BusinessException.class, () -> AnalyticsDateRange.resolve(query(null, "2026-10-01"), TODAY));
        assertThrows(BusinessException.class, () -> AnalyticsDateRange.resolve(query("2026-10-29", "2026-10-28"), TODAY));
        assertThrows(BusinessException.class, () -> AnalyticsDateRange.resolve(query("2026-10-01", "2026-10-30"), TODAY));
        LearningAnalyticsQuery conflict = query("2026-10-01", "2026-10-29");
        conflict.setDays(7);
        assertThrows(BusinessException.class, () -> AnalyticsDateRange.resolve(conflict, TODAY));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 366, Integer.MAX_VALUE})
    void invalidDaysAreRejectedOutsideHttpValidation(int days) {
        LearningAnalyticsQuery query = new LearningAnalyticsQuery();
        query.setDays(days);
        assertThrows(BusinessException.class, () -> AnalyticsDateRange.resolve(query, TODAY));
    }

    private LearningAnalyticsQuery query(String start, String end) {
        LearningAnalyticsQuery query = new LearningAnalyticsQuery();
        query.setStartDate(start == null ? null : LocalDate.parse(start));
        query.setEndDate(end == null ? null : LocalDate.parse(end));
        return query;
    }
}
