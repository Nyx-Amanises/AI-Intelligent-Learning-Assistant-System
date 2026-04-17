package com.aiassistant.learning.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiTaskCreateRequest {

    @NotBlank(message = "taskType is required")
    private String taskType;

    private String bizType;

    private Long bizId;

    private Integer priority;

    private String modelName;

    private String payloadJson;
}
