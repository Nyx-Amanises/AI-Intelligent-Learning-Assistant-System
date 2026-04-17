package com.aiassistant.learning.service.task;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.PracticeReviewTaskPayload;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PracticeReviewTaskProcessor implements AiTaskProcessor {

    private final PracticeService practiceService;
    private final ObjectMapper objectMapper;

    public PracticeReviewTaskProcessor(PracticeService practiceService, ObjectMapper objectMapper) {
        this.practiceService = practiceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String taskType) {
        return "PRACTICE_REVIEW".equalsIgnoreCase(taskType);
    }

    @Override
    public TaskExecutionResult process(AiTask task) {
        PracticeReviewTaskPayload payload = readPayload(task.getPayloadJson(), PracticeReviewTaskPayload.class);
        if (payload.getSessionId() == null) {
            throw new BusinessException("sessionId is required for practice review task");
        }

        practiceService.reviewPendingShortAnswersNow(payload.getSessionId());
        PracticeDetailVO result = practiceService.getPracticeDetail(task.getUserId(), payload.getSessionId());
        return new TaskExecutionResult(writeJson(result), 100);
    }

    private <T> T readPayload(String payloadJson, Class<T> payloadType) {
        try {
            return objectMapper.readValue(payloadJson, payloadType);
        } catch (Exception exception) {
            throw new BusinessException(500, "Failed to parse practice review task payload: " + exception.getMessage());
        }
    }

    private String writeJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "Failed to serialize practice review task result: " + exception.getMessage());
        }
    }
}
