package com.aiassistant.learning.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.service.LearningAnalyticsService;
import com.aiassistant.learning.vo.analytics.LearningAnalyticsOverviewVO;
import com.aiassistant.learning.vo.analytics.PracticeCountPointVO;
import com.aiassistant.learning.vo.analytics.PracticeCountTrendVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LearningAnalyticsCountControllerTest {
    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void countSeriesSerializesDatesCountsAndUndatedCountAlongsidePerformance() throws Exception {
        LearningAnalyticsService service = mock(LearningAnalyticsService.class);
        LocalDate date = LocalDate.of(2024, 2, 29);
        when(service.overview(eq(7L), any())).thenReturn(LearningAnalyticsOverviewVO.builder()
                .totalPracticeCount(3)
                .practiceCountTrend(new PracticeCountTrendVO("DAY", date, date.plusDays(1), List.of(
                        new PracticeCountPointVO(date, date, 2),
                        new PracticeCountPointVO(date.plusDays(1), date.plusDays(1), 0)), 1))
                .practiceTrend(List.of()).build());
        UserContext.setCurrentUserId(7L);
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var mvc = MockMvcBuilders.standaloneSetup(new LearningAnalyticsController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();

        mvc.perform(get("/api/learning-analytics/overview"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalPracticeCount").value(3))
                .andExpect(jsonPath("$.data.practiceCountTrend.granularity").value("DAY"))
                .andExpect(jsonPath("$.data.practiceCountTrend.startDate").value("2024-02-29"))
                .andExpect(jsonPath("$.data.practiceCountTrend.endDate").value("2024-03-01"))
                .andExpect(jsonPath("$.data.practiceCountTrend.points[0].startDate").value("2024-02-29"))
                .andExpect(jsonPath("$.data.practiceCountTrend.points[0].endDate").value("2024-02-29"))
                .andExpect(jsonPath("$.data.practiceCountTrend.points[0].count").value(2))
                .andExpect(jsonPath("$.data.practiceCountTrend.points[1].count").value(0))
                .andExpect(jsonPath("$.data.practiceCountTrend.undatedCount").value(1))
                .andExpect(jsonPath("$.data.practiceTrend").isEmpty());
    }
}
