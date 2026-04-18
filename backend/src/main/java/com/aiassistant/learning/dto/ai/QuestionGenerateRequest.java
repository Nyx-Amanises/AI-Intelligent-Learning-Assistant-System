package com.aiassistant.learning.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QuestionGenerateRequest {

    private String modelName;

    @Size(max = 200, message = "题集名称长度不能超过200个字符")
    private String title;

    @Min(value = 1, message = "题目数量最少为1")
    @Max(value = 20, message = "题目数量最多为20")
    private Integer questionCount = 5;

    @Min(value = 0, message = "单选题数量不能小于0")
    @Max(value = 20, message = "单选题数量不能超过20")
    private Integer singleCount;

    @Min(value = 0, message = "判断题数量不能小于0")
    @Max(value = 20, message = "判断题数量不能超过20")
    private Integer judgeCount;

    @Min(value = 0, message = "简答题数量不能小于0")
    @Max(value = 20, message = "简答题数量不能超过20")
    private Integer shortAnswerCount;

    @Min(value = 1, message = "难度等级最小为1")
    @Max(value = 5, message = "难度等级最大为5")
    private Integer difficultyLevel = 3;
}
