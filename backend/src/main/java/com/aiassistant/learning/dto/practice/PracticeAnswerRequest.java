package com.aiassistant.learning.dto.practice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单题作答请求参数。
 */
@Data
public class PracticeAnswerRequest {

    /** 与 Java String.length() 和前端 String.length 一致，按 UTF-16 代码单元计数。 */
    public static final int MAX_USER_ANSWER_LENGTH = 5000;

    /**
     * 题目 ID，不能为空。
     */
    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    /**
     * 用户填写的答案。未作答时可以为空。
     */
    @Size(max = MAX_USER_ANSWER_LENGTH, message = "答案不能超过{max}个字符")
    private String userAnswer;
}
