package com.aiassistant.learning.dto.ai;

import lombok.Data;

@Data
public class EmbeddingTaskRequest {

    private String modelName;

    private Boolean forceRegenerate = false;
}
