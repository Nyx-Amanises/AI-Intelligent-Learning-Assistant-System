package com.aiassistant.learning.service.task;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryTaskPayload;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.service.AiSummaryService;
import com.aiassistant.learning.vo.ai.SummaryResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 学习资料总结任务处理器。
 */
@Component
public class SummaryTaskProcessor implements AiTaskProcessor {

    /** 总结生成服务。 */
    private final AiSummaryService aiSummaryService;
    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    public SummaryTaskProcessor(AiSummaryService aiSummaryService, ObjectMapper objectMapper) {
        this.aiSummaryService = aiSummaryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 只处理 SUMMARY 类型的任务。
     */
    @Override
    public boolean supports(String taskType) {
        return "SUMMARY".equalsIgnoreCase(taskType);
    }

    /**
     * 给任务参数补上默认值后，调用总结服务生成结果。
     */
    @Override
    public TaskExecutionResult process(AiTask task) {
        SummaryTaskPayload payload = readPayload(task.getPayloadJson(), SummaryTaskPayload.class);
        if (payload.getMaterialId() == null) {
            throw new BusinessException("materialId is required for summary task");
        }

        SummaryGenerateRequest request = new SummaryGenerateRequest();
        request.setModelName(payload.getModelName());
        request.setSummaryType(StringUtils.hasText(payload.getSummaryType()) ? payload.getSummaryType() : "STANDARD");
        request.setSaveAsNote(payload.getSaveAsNote() == null ? Boolean.TRUE : payload.getSaveAsNote());
        request.setTemperature(payload.getTemperature() == null ? 0.7 : payload.getTemperature());

        SummaryResultVO result = aiSummaryService.generateMaterialSummary(task.getUserId(), payload.getMaterialId(), request);
        return new TaskExecutionResult(writeJson(result), 100);
    }

    /**
     * 读取任务参数 JSON。
     */
    private <T> T readPayload(String payloadJson, Class<T> payloadType) {
        try {
            return objectMapper.readValue(payloadJson, payloadType);
        } catch (Exception exception) {
            throw new BusinessException(500, "Failed to parse summary task payload: " + exception.getMessage());
        }
    }

    /**
     * 写出任务结果 JSON。
     */
    private String writeJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "Failed to serialize summary task result: " + exception.getMessage());
        }
    }
}
