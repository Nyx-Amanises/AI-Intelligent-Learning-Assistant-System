package com.aiassistant.learning.dto.ai;

import lombok.Data;

@Data
public class QuestionGenerateTaskPayload {

    private Long materialId;

    private String modelName;

    private String title;

    private Integer questionCount;

    private Integer singleCount;

    private Integer judgeCount;

    private Integer shortAnswerCount;

    private Integer difficultyLevel;
}
