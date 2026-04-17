package com.aiassistant.learning.service.task;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.EmbeddingTaskPayload;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.service.AiEmbeddingService;
import com.aiassistant.learning.vo.ai.EmbeddingTaskResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingTaskProcessor implements AiTaskProcessor {

    private final AiEmbeddingService aiEmbeddingService;
    private final ObjectMapper objectMapper;

    public EmbeddingTaskProcessor(AiEmbeddingService aiEmbeddingService, ObjectMapper objectMapper) {
        this.aiEmbeddingService = aiEmbeddingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String taskType) {
        return "EMBEDDING".equalsIgnoreCase(taskType);
    }

    @Override
    public TaskExecutionResult process(AiTask task) {
        EmbeddingTaskPayload payload = readPayload(task.getPayloadJson(), EmbeddingTaskPayload.class);
        if (payload.getMaterialId() == null) {
            throw new BusinessException("materialId is required for embedding task");
        }

        EmbeddingTaskResultVO result = aiEmbeddingService.prepareMaterialEmbedding(
                task.getUserId(),
                payload.getMaterialId(),
                payload.getModelName(),
                payload.getForceRegenerate(),
                task.getId()
        );
        return new TaskExecutionResult(writeJson(result), 100);
    }

    private <T> T readPayload(String payloadJson, Class<T> payloadType) {
        try {
            return objectMapper.readValue(payloadJson, payloadType);
        } catch (Exception exception) {
            throw new BusinessException(500, "Failed to parse embedding task payload: " + exception.getMessage());
        }
    }

    private String writeJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "Failed to serialize embedding task result: " + exception.getMessage());
        }
    }
}
