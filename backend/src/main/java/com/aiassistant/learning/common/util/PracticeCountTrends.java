package com.aiassistant.learning.common.util;

import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.vo.analytics.PracticeCountPointVO;
import com.aiassistant.learning.vo.analytics.PracticeCountTrendVO;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** 按提交日期聚合练习数量，与题目、答案、评分状态以及最近练习展示数量无关。 */
public final class PracticeCountTrends {
    private static final LocalDate MIN_SUBMISSION_DATE = LocalDate.of(1000, 1, 1);

    private PracticeCountTrends() { }

    /**
     * 调用方先按用户、资料、SUBMITTED 状态和日期范围筛选会话。
     * 起止日期同时为 null 时统计全部，从最早有效提交日到 today；否则按传入闭区间补零。
     * 缺失提交时间或不在有效区间内的记录计入 undatedCount，不会伪造日期或静默丢失。
     */
    public static PracticeCountTrendVO build(
            List<PracticeSession> sessions,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate today
    ) {
        Objects.requireNonNull(today, "today");
        if ((startDate == null) != (endDate == null)) {
            throw new IllegalArgumentException("开始和结束日期必须同时提供");
        }
        List<PracticeSession> records = sessions == null ? List.of() : sessions;
        LocalDate start = startDate;
        LocalDate end = endDate;
        if (start == null) {
            end = today;
            start = today;
            for (PracticeSession session : records) {
                LocalDate date = submittedDate(session);
                if (date != null && !date.isBefore(MIN_SUBMISSION_DATE)
                        && !date.isAfter(today) && date.isBefore(start)) start = date;
            }
        }
        if (start.isAfter(end)) throw new IllegalArgumentException("开始日期不能晚于结束日期");

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        Granularity granularity = days <= 90 ? Granularity.DAY : days <= 730 ? Granularity.WEEK : Granularity.MONTH;
        Map<LocalDate, Long> counts = new TreeMap<>();
        long undatedCount = 0;
        for (PracticeSession session : records) {
            LocalDate date = submittedDate(session);
            if (date == null || date.isBefore(start) || date.isAfter(end)) {
                undatedCount++;
            } else {
                counts.merge(date, 1L, Long::sum);
            }
        }

        List<PracticeCountPointVO> points = new ArrayList<>();
        Iterator<Map.Entry<LocalDate, Long>> iterator = counts.entrySet().iterator();
        Map.Entry<LocalDate, Long> entry = iterator.hasNext() ? iterator.next() : null;
        LocalDate cursor = start;
        while (true) {
            LocalDate bucketEnd = bucketEnd(cursor, end, granularity);
            long count = 0;
            while (entry != null && !entry.getKey().isAfter(bucketEnd)) {
                count += entry.getValue();
                entry = iterator.hasNext() ? iterator.next() : null;
            }
            points.add(new PracticeCountPointVO(cursor, bucketEnd, count));
            if (bucketEnd.equals(end)) break;
            cursor = bucketEnd.plusDays(1);
        }
        return new PracticeCountTrendVO(granularity.name(), start, end, points, undatedCount);
    }

    private static LocalDate submittedDate(PracticeSession session) {
        return session == null || session.getSubmitTime() == null ? null : session.getSubmitTime().toLocalDate();
    }

    private static LocalDate bucketEnd(LocalDate start, LocalDate end, Granularity granularity) {
        return switch (granularity) {
            case DAY -> start;
            case WEEK -> start.plusDays(Math.min(
                    7 - start.getDayOfWeek().getValue(), ChronoUnit.DAYS.between(start, end)));
            case MONTH -> {
                LocalDate monthEnd = start.with(TemporalAdjusters.lastDayOfMonth());
                yield monthEnd.isAfter(end) ? end : monthEnd;
            }
        };
    }

    private enum Granularity { DAY, WEEK, MONTH }
}
