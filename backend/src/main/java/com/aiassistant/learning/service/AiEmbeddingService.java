package com.aiassistant.learning.service;

import com.aiassistant.learning.vo.ai.EmbeddingTaskResultVO;

public interface AiEmbeddingService {

    EmbeddingTaskResultVO prepareMaterialEmbedding(
            Long userId,
            Long materialId,
            String modelName,
            Boolean forceRegenerate,
            Long taskId
    );
}
