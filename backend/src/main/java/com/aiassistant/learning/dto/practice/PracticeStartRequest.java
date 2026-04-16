package com.aiassistant.learning.dto.practice;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PracticeStartRequest {

    @NotNull(message = "题集ID不能为空")
    private Long questionSetId;
}
