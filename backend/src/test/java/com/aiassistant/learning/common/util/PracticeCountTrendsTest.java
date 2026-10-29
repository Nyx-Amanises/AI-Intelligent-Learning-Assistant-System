package com.aiassistant.learning.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.vo.analytics.PracticeCountPointVO;
import com.aiassistant.learning.vo.analytics.PracticeCountTrendVO;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PracticeCountTrendsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    @ParameterizedTest
    @CsvSource({"1, DAY", "90, DAY", "91, WEEK", "730, WEEK", "731, MONTH"})
    void choosesGranularityFromInclusiveRangeLength(int days, String expected) {
        LocalDate start = TODAY.minusDays(days - 1L);
        PracticeCountTrendVO trend = PracticeCountTrends.build(List.of(), start, TODAY, TODAY);
        assertEquals(expected, trend.granularity());
        assertEquals(start, trend.startDate());
        assertEquals(TODAY, trend.endDate());
        assertContinuous(trend);
        assertTrue(trend.points().stream().allMatch(point -> point.count() == 0));
    }

    @Test
    void countsEverySubmittedSessionAndFillsDailyGapsWithoutATrendLimit() {
        LocalDate start = TODAY.minusDays(4);
        List<PracticeSession> sessions = new ArrayList<>();
        for (int index = 0; index < 35; index++) sessions.add(session(start));
        PracticeSession redo = session(TODAY);
        redo.setQuestionIdsJson("[42]");
        sessions.add(redo);
        PracticeCountTrendVO trend = PracticeCountTrends.build(sessions, start, TODAY, TODAY);
        assertEquals(List.of(35L, 0L, 0L, 0L, 1L), trend.points().stream().map(PracticeCountPointVO::count).toList());
        assertTotal(sessions.size(), trend);
    }

    @Test
    void includesEntireDaysAndLeapDay() {
        LocalDate start = LocalDate.of(2024, 2, 28);
        LocalDate end = LocalDate.of(2024, 3, 1);
        PracticeSession first = session(start);
        first.setSubmitTime(start.atStartOfDay());
        PracticeSession last = session(end);
        last.setSubmitTime(end.atTime(LocalTime.MAX));
        PracticeCountTrendVO trend = PracticeCountTrends.build(List.of(first, session(start.plusDays(1)), last), start, end, TODAY);
        assertEquals(List.of(1L, 1L, 1L), trend.points().stream().map(PracticeCountPointVO::count).toList());
        assertEquals(LocalDate.of(2024, 2, 29), trend.points().get(1).startDate());
    }

    @Test
    void clipsCalendarWeeksAtBothEndsAcrossYearBoundary() {
        LocalDate start = LocalDate.of(2023, 12, 30);
        LocalDate end = LocalDate.of(2024, 4, 2);
        List<PracticeSession> sessions = List.of(session(start), session(LocalDate.of(2023, 12, 31)),
                session(LocalDate.of(2024, 1, 1)), session(LocalDate.of(2024, 1, 7)), session(end));
        PracticeCountTrendVO trend = PracticeCountTrends.build(sessions, start, end, TODAY);
        assertEquals("WEEK", trend.granularity());
        assertEquals(new PracticeCountPointVO(start, LocalDate.of(2023, 12, 31), 2), trend.points().get(0));
        assertEquals(new PracticeCountPointVO(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7), 2), trend.points().get(1));
        assertEquals(new PracticeCountPointVO(LocalDate.of(2024, 4, 1), end, 1), trend.points().get(trend.points().size() - 1));
        assertContinuous(trend);
        assertTotal(sessions.size(), trend);
    }

    @Test
    void advancesByCalendarMonthsWithoutDriftingAfterJanuary31() {
        LocalDate start = LocalDate.of(2022, 1, 31);
        LocalDate end = LocalDate.of(2024, 3, 2);
        List<PracticeSession> sessions = List.of(session(start), session(LocalDate.of(2022, 2, 28)),
                session(LocalDate.of(2024, 2, 29)), session(end));
        PracticeCountTrendVO trend = PracticeCountTrends.build(sessions, start, end, TODAY);
        assertEquals("MONTH", trend.granularity());
        assertEquals(27, trend.points().size());
        assertEquals(new PracticeCountPointVO(start, start, 1), trend.points().get(0));
        assertEquals(new PracticeCountPointVO(LocalDate.of(2022, 2, 1), LocalDate.of(2022, 2, 28), 1), trend.points().get(1));
        assertEquals(new PracticeCountPointVO(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), 1), trend.points().get(25));
        assertEquals(new PracticeCountPointVO(LocalDate.of(2024, 3, 1), end, 1), trend.points().get(26));
        assertContinuous(trend);
        assertTotal(sessions.size(), trend);
    }

    @Test
    void allTimeStartsAtEarliestValidSubmissionAndAccountsForUnplaceableRecords() {
        LocalDate earliest = TODAY.minusYears(4);
        List<PracticeSession> sessions = Arrays.asList(session(TODAY), session(null),
                session(TODAY.plusDays(1)), session(earliest), null);
        PracticeCountTrendVO trend = PracticeCountTrends.build(sessions, null, null, TODAY);
        assertEquals(earliest, trend.startDate());
        assertEquals(TODAY, trend.endDate());
        assertEquals("MONTH", trend.granularity());
        assertEquals(49, trend.points().size());
        assertEquals(3, trend.undatedCount());
        assertTotal(sessions.size(), trend);
    }

    @Test
    void noDatedAllTimeRecordsProduceOneZeroBucketToday() {
        PracticeCountTrendVO empty = PracticeCountTrends.build(List.of(), null, null, TODAY);
        assertEquals(List.of(new PracticeCountPointVO(TODAY, TODAY, 0)), empty.points());
        assertEquals(0, empty.undatedCount());
        List<PracticeSession> sessions = List.of(session(null), session(TODAY.plusYears(1)));
        PracticeCountTrendVO undated = PracticeCountTrends.build(sessions, null, null, TODAY);
        assertEquals(empty.points(), undated.points());
        assertEquals(2, undated.undatedCount());
        assertTotal(sessions.size(), undated);
    }

    @Test
    void invalidAncientSubmissionDoesNotExtendAllTimeTimeline() {
        List<PracticeSession> sessions = List.of(session(LocalDate.of(-1, 1, 1)), session(TODAY));
        PracticeCountTrendVO trend = PracticeCountTrends.build(sessions, null, null, TODAY);
        assertEquals(List.of(new PracticeCountPointVO(TODAY, TODAY, 1)), trend.points());
        assertEquals(1, trend.undatedCount());
        assertTotal(sessions.size(), trend);
    }

    @Test
    void explicitRangeDoesNotSilentlyLoseUnexpectedRecordsOutsideIt() {
        LocalDate start = TODAY.minusDays(6);
        List<PracticeSession> sessions = List.of(session(start.minusDays(1)), session(start), session(null), session(TODAY.plusDays(1)));
        PracticeCountTrendVO trend = PracticeCountTrends.build(sessions, start, TODAY, TODAY);
        assertEquals(7, trend.points().size());
        assertEquals(3, trend.undatedCount());
        assertTotal(sessions.size(), trend);
    }

    @Test
    void rejectsHalfOrReversedRanges() {
        assertThrows(IllegalArgumentException.class, () -> PracticeCountTrends.build(List.of(), TODAY, null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> PracticeCountTrends.build(List.of(), null, TODAY, TODAY));
        assertThrows(IllegalArgumentException.class, () -> PracticeCountTrends.build(List.of(), TODAY, TODAY.minusDays(1), TODAY));
    }

    private static PracticeSession session(LocalDate date) {
        PracticeSession session = new PracticeSession();
        session.setSessionStatus("SUBMITTED");
        session.setSubmitTime(date == null ? null : date.atTime(12, 0));
        return session;
    }

    private static void assertTotal(long total, PracticeCountTrendVO trend) {
        assertEquals(total, trend.undatedCount() + trend.points().stream().mapToLong(PracticeCountPointVO::count).sum());
    }

    private static void assertContinuous(PracticeCountTrendVO trend) {
        assertEquals(trend.startDate(), trend.points().get(0).startDate());
        assertEquals(trend.endDate(), trend.points().get(trend.points().size() - 1).endDate());
        for (int index = 1; index < trend.points().size(); index++) {
            assertEquals(trend.points().get(index - 1).endDate().plusDays(1), trend.points().get(index).startDate());
        }
    }
}
