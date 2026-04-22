package com.aiassistant.learning.dto.ai;

import lombok.Data;

/**
 * 题集生成任务载荷。
 */
@Data
public class QuestionGenerateTaskPayload {

    /**
     * 资料 ID。
     */
    private Long materialId;

    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 题集标题。
     */
    private String title;

    /**
     * 总题目数量。
     */
    private Integer questionCount;

    /**
     * 单选题数量。
     */
    private Integer singleCount;

    /**
     * 判断题数量。
     */
    private Integer judgeCount;

    /**
     * 简答题数量。
     */
    private Integer shortAnswerCount;

    /**
     * 难度等级。
     */
    private Integer difficultyLevel;
}
