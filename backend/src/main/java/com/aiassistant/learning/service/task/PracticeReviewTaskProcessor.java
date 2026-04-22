package com.aiassistant.learning.service.task;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.PracticeReviewTaskPayload;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 主观题批改任务处理器。
 */
@Component
public class PracticeReviewTaskProcessor implements AiTaskProcessor {

    /** 练习服务，内部包含主观题 AI 批改逻辑。 */
    private final PracticeService practiceService;
    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    public PracticeReviewTaskProcessor(PracticeService practiceService, ObjectMapper objectMapper) {
        this.practiceService = practiceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 只处理 PRACTICE_REVIEW 类型的任务。
     */
    @Override
    public boolean supports(String taskType) {
        return "PRACTICE_REVIEW".equalsIgnoreCase(taskType);
    }

    /**
     * 触发当前练习中待批改主观题的立即批改，并返回更新后的练习详情。
     */
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

    /**
     * 读取任务参数 JSON。
     */
    private <T> T readPayload(String payloadJson, Class<T> payloadType) {
        try {
            return objectMapper.readValue(payloadJson, payloadType);
        } catch (Exception exception) {
            throw new BusinessException(500, "Failed to parse practice review task payload: " + exception.getMessage());
        }
    }

    /**
     * 写出任务结果 JSON。
     */
    private String writeJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "Failed to serialize practice review task result: " + exception.getMessage());
        }
    }
}
