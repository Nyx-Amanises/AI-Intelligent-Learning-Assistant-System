package com.aiassistant.learning.vo.practice;

import lombok.Builder;
import lombok.Data;

/**
 * AI 判分状态返回对象。
 */
@Data
@Builder
public class PracticeReviewStatusVO {

    /**
     * 练习会话 ID。
     */
    private Long sessionId;

    /**
     * 是否已经全部判分完成。
     */
    private Boolean completed;

    /**
     * 是否仍有题目等待 AI 判分。
     */
    private Boolean pending;
}
