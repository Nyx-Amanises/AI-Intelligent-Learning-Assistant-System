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

@Component
public class SummaryTaskProcessor implements AiTaskProcessor {

    private final AiSummaryService aiSummaryService;
    private final ObjectMapper objectMapper;

    public SummaryTaskProcessor(AiSummaryService aiSummaryService, ObjectMapper objectMapper) {
        this.aiSummaryService = aiSummaryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String taskType) {
        return "SUMMARY".equalsIgnoreCase(taskType);
    }

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

    private <T> T readPayload(String payloadJson, Class<T> payloadType) {
        try {
            return objectMapper.readValue(payloadJson, payloadType);
        } catch (Exception exception) {
            throw new BusinessException(500, "Failed to parse summary task payload: " + exception.getMessage());
        }
    }

    private String writeJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "Failed to serialize summary task result: " + exception.getMessage());
        }
    }
}
