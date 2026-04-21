package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import com.aiassistant.learning.service.LearningAnalyticsService;
import com.aiassistant.learning.vo.analytics.LearningAnalyticsOverviewVO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/learning-analytics")
public class LearningAnalyticsController {

    private final LearningAnalyticsService learningAnalyticsService;

    public LearningAnalyticsController(LearningAnalyticsService learningAnalyticsService) {
        this.learningAnalyticsService = learningAnalyticsService;
    }

    @GetMapping("/overview")
    public ApiResponse<LearningAnalyticsOverviewVO> overview(@Valid LearningAnalyticsQuery query) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(learningAnalyticsService.overview(userId, query));
    }
}
