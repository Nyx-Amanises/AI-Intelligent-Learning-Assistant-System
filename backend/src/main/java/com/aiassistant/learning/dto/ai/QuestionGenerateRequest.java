package com.aiassistant.learning.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class QuestionGenerateRequest {

    private String modelName;

    @Min(value = 1, message = "题目数量最少为1")
    @Max(value = 20, message = "题目数量最多为20")
    private Integer questionCount = 5;

    @Min(value = 1, message = "难度等级最小为1")
    @Max(value = 5, message = "难度等级最大为5")
    private Integer difficultyLevel = 3;
}
