package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import com.aiassistant.learning.vo.analytics.LearningAnalyticsOverviewVO;

/**
 * 学习分析业务接口。
 */
public interface LearningAnalyticsService {

    /**
     * 汇总当前用户的学习分析看板数据。
     *
     * @param userId 当前登录用户 ID
     * @param query 查询条件
     * @return 学习分析总览
     */
    LearningAnalyticsOverviewVO overview(Long userId, LearningAnalyticsQuery query);
}
