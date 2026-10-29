package com.aiassistant.learning.vo.analytics;

import java.time.LocalDate;

/** 一个日、周或月区间内的练习次数；起止日期均包含在区间内。 */
public record PracticeCountPointVO(LocalDate startDate, LocalDate endDate, long count) {
}
