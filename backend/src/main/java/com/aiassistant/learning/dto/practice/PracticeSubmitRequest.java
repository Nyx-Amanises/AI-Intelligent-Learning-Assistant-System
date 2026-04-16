package com.aiassistant.learning.dto.practice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class PracticeSubmitRequest {

    @NotNull(message = "练习会话ID不能为空")
    private Long sessionId;

    @Valid
    @NotEmpty(message = "答案列表不能为空")
    private List<PracticeAnswerRequest> answers;
}
