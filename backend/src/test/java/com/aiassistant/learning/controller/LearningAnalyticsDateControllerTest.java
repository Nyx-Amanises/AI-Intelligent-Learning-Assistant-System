package com.aiassistant.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.aiassistant.learning.common.exception.GlobalExceptionHandler;
import com.aiassistant.learning.common.util.AnalyticsDateRange;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import com.aiassistant.learning.service.LearningAnalyticsService;
import com.aiassistant.learning.vo.analytics.LearningAnalyticsOverviewVO;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LearningAnalyticsDateControllerTest {
    private final LearningAnalyticsService service = mock(LearningAnalyticsService.class);
    private MockMvc mvc;

    @BeforeEach
    void configureApi() {
        UserContext.setCurrentUserId(7L);
        mvc = MockMvcBuilders.standaloneSetup(new LearningAnalyticsController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.overview(eq(7L), any())).thenAnswer(invocation -> {
            AnalyticsDateRange.resolve(invocation.getArgument(1), LocalDate.now());
            return LearningAnalyticsOverviewVO.builder().totalPracticeCount(0).build();
        });
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void bindsIsoDatesAndCombinesMaterialWithAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/learning-analytics/overview").param("startDate", "2024-02-28")
                        .param("endDate", "2024-02-29").param("materialId", "42").param("userId", "99"))
                .andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<LearningAnalyticsQuery> query = ArgumentCaptor.forClass(LearningAnalyticsQuery.class);
        verify(service).overview(eq(7L), query.capture());
        assertEquals(LocalDate.of(2024, 2, 28), query.getValue().getStartDate());
        assertEquals(LocalDate.of(2024, 2, 29), query.getValue().getEndDate());
        assertEquals(42L, query.getValue().getMaterialId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-date", "2024-02-30", "2024/02/29", "2024-02-29T12:00:00"})
    void malformedDatesReturnValidationCodeWithoutCallingService(String date) throws Exception {
        mvc.perform(get("/api/learning-analytics/overview").param("startDate", date).param("endDate", "2024-03-01"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void dateRuleFailuresSurfaceAsValidationErrors() throws Exception {
        mvc.perform(get("/api/learning-analytics/overview").param("startDate", "0999-12-31").param("endDate", "2024-02-29"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/learning-analytics/overview").param("startDate", "2024-02-29"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/learning-analytics/overview").param("startDate", "2024-03-01").param("endDate", "2024-02-29"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/learning-analytics/overview").param("days", "7")
                        .param("startDate", "2024-02-28").param("endDate", "2024-02-29"))
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/learning-analytics/overview").param("startDate", "2024-02-28")
                        .param("endDate", LocalDate.now().plusDays(2).toString()))
                .andExpect(jsonPath("$.code").value(400));
    }
}
