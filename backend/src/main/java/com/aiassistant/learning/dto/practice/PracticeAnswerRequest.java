package com.aiassistant.learning.dto.practice;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PracticeAnswerRequest {

    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    private String userAnswer;
}
