package com.aiassistant.learning.service.task;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.EmbeddingTaskPayload;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.service.AiEmbeddingService;
import com.aiassistant.learning.vo.ai.EmbeddingTaskResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 资料向量化任务处理器。
 */
@Component
public class EmbeddingTaskProcessor implements AiTaskProcessor {

    /** 真正执行向量化的业务服务。 */
    private final AiEmbeddingService aiEmbeddingService;
    /** JSON 工具，用于把任务参数和任务结果在对象与字符串之间转换。 */
    private final ObjectMapper objectMapper;

    public EmbeddingTaskProcessor(AiEmbeddingService aiEmbeddingService, ObjectMapper objectMapper) {
        this.aiEmbeddingService = aiEmbeddingService;
        this.objectMapper = objectMapper;
    }

    /**
     * 只处理 EMBEDDING 类型的任务。
     */
    @Override
    public boolean supports(String taskType) {
        return "EMBEDDING".equalsIgnoreCase(taskType);
    }

    /**
     * 解析任务参数，调用向量化服务，并把结果保存成 JSON。
     */
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

    /**
     * 将任务表中的 payloadJson 反序列化为指定类型的参数对象。
     */
    private <T> T readPayload(String payloadJson, Class<T> payloadType) {
        try {
            return objectMapper.readValue(payloadJson, payloadType);
        } catch (Exception exception) {
            throw new BusinessException(500, "Failed to parse embedding task payload: " + exception.getMessage());
        }
    }

    /**
     * 将任务执行结果序列化为 JSON，便于统一写回任务表。
     */
    private String writeJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "Failed to serialize embedding task result: " + exception.getMessage());
        }
    }
}
