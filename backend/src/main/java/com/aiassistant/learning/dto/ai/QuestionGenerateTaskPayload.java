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
     * 提交任务时创建的题集 ID；旧任务允许为空。
     */
    private Long questionSetId;

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

    /** 多选题数量，旧任务为空时按 0 处理。 */
    private Integer multipleCount;

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

    public QuestionGenerateRequest toRequest() {
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setModelName(modelName);
        request.setTitle(title);
        request.setQuestionCount(questionCount);
        request.setSingleCount(singleCount);
        request.setMultipleCount(multipleCount);
        request.setJudgeCount(judgeCount);
        request.setShortAnswerCount(shortAnswerCount);
        request.setDifficultyLevel(difficultyLevel);
        return request;
    }
}
