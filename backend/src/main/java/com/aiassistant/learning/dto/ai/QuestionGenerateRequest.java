package com.aiassistant.learning.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 题集生成请求参数。
 */
@Data
public class QuestionGenerateRequest {

    /**
     * 指定模型名称，空值时使用默认模型。
     */
    private String modelName;

    /**
     * 题集标题。
     */
    @Size(max = 200, message = "题集名称长度不能超过200个字符")
    private String title;

    /**
     * 总题目数量。
     */
    @Min(value = 1, message = "题目数量最少为1")
    @Max(value = 20, message = "题目数量最多为20")
    private Integer questionCount = 5;

    /**
     * 单选题数量。
     */
    @Min(value = 0, message = "单选题数量不能小于0")
    @Max(value = 20, message = "单选题数量不能超过20")
    private Integer singleCount;

    /**
     * 判断题数量。
     */
    @Min(value = 0, message = "判断题数量不能小于0")
    @Max(value = 20, message = "判断题数量不能超过20")
    private Integer judgeCount;

    /**
     * 简答题数量。
     */
    @Min(value = 0, message = "简答题数量不能小于0")
    @Max(value = 20, message = "简答题数量不能超过20")
    private Integer shortAnswerCount;

    /**
     * 题目难度等级，范围 1 到 5。
     */
    @Min(value = 1, message = "难度等级最小为1")
    @Max(value = 5, message = "难度等级最大为5")
    private Integer difficultyLevel = 3;
}
