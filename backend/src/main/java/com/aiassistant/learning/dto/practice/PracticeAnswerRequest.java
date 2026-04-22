package com.aiassistant.learning.dto.practice;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单题作答请求参数。
 */
@Data
public class PracticeAnswerRequest {

    /**
     * 题目 ID，不能为空。
     */
    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    /**
     * 用户填写的答案。未作答时可以为空。
     */
    private String userAnswer;
}
