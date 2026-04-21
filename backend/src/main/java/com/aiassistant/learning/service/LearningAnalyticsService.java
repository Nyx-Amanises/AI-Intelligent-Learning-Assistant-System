package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import com.aiassistant.learning.vo.analytics.LearningAnalyticsOverviewVO;

public interface LearningAnalyticsService {

    LearningAnalyticsOverviewVO overview(Long userId, LearningAnalyticsQuery query);
}
