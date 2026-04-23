package com.aiassistant.learning.service.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 助手准备提交的 AI 任务参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantPlannedTask {

    /** 任务类型：SUMMARY 或 QUESTION_GENERATE。 */
    private String taskType;

    /** 使用的模型名称。 */
    private String modelName;

    /** 总结类型。 */
    private String summaryType;

    /** 生成总结后是否保存为笔记。 */
    private Boolean saveAsNote;

    /** 模型温度参数。 */
    private Double temperature;

    /** 出题总数。 */
    private Integer questionCount;

    /** 单选题数量。 */
    private Integer singleCount;

    /** 判断题数量。 */
    private Integer judgeCount;

    /** 简答题数量。 */
    private Integer shortAnswerCount;

    /** 题目难度。 */
    private Integer difficultyLevel;

    /** 参数自动调整说明。 */
    private String adjustmentNote;

    /** 是否还需要用户确认题型分布。 */
    private Boolean requiresQuestionTypeConfirmation;
}
