package com.aiassistant.learning.dto.ai;

import lombok.Data;

/**
 * 练习简答题判分任务载荷。
 */
@Data
public class PracticeReviewTaskPayload {

    /**
     * 练习会话 ID。
     */
    private Long sessionId;
}
