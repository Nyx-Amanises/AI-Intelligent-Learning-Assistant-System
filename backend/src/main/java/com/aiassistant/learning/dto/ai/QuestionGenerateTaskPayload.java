package com.aiassistant.learning.dto.ai;

import lombok.Data;

@Data
public class QuestionGenerateTaskPayload {

    private Long materialId;

    private String modelName;

    private Integer questionCount;

    private Integer difficultyLevel;
}
