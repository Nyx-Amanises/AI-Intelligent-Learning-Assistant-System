package com.aiassistant.learning.service.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantPlannedTask {

    private String taskType;

    private String modelName;

    private String summaryType;

    private Boolean saveAsNote;

    private Double temperature;

    private Integer questionCount;

    private Integer singleCount;

    private Integer judgeCount;

    private Integer shortAnswerCount;

    private Integer difficultyLevel;

    private String adjustmentNote;

    private Boolean requiresQuestionTypeConfirmation;
}
