package com.aiassistant.learning.dto.ai;

import lombok.Data;

@Data
public class EmbeddingTaskPayload {

    private Long materialId;

    private String modelName;

    private Boolean forceRegenerate;
}
