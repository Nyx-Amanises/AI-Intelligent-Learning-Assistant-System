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

/**
 * 学习分析看板接口。
 *
 * <p>用于汇总练习次数、题目作答、薄弱知识点、题型表现、资料表现和练习趋势等指标。</p>
 */
@Validated
@RestController
@RequestMapping("/api/learning-analytics")
public class LearningAnalyticsController {

    private final LearningAnalyticsService learningAnalyticsService;

    /**
     * 构造方法注入学习分析服务。
     *
     * @param learningAnalyticsService 学习分析业务服务
     */
    public LearningAnalyticsController(LearningAnalyticsService learningAnalyticsService) {
        this.learningAnalyticsService = learningAnalyticsService;
    }

    /**
     * 获取学习分析总览。
     *
     * @param query 筛选条件
     * @return 学习分析总览数据
     */
    @GetMapping("/overview")
    public ApiResponse<LearningAnalyticsOverviewVO> overview(@Valid LearningAnalyticsQuery query) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(learningAnalyticsService.overview(userId, query));
    }
}
