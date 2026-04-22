package com.aiassistant.learning.service.task;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.QuestionGenerateTaskPayload;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.service.AiQuestionService;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * AI 题目生成任务处理器。
 */
@Component
public class QuestionGenerateTaskProcessor implements AiTaskProcessor {

    /** 题目生成服务。 */
    private final AiQuestionService aiQuestionService;
    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    public QuestionGenerateTaskProcessor(AiQuestionService aiQuestionService, ObjectMapper objectMapper) {
        this.aiQuestionService = aiQuestionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 只处理 QUESTION_GENERATE 类型的任务。
     */
    @Override
    public boolean supports(String taskType) {
        return "QUESTION_GENERATE".equalsIgnoreCase(taskType);
    }

    /**
     * 从任务参数恢复生成题目的请求对象，并调用题目生成服务。
     */
    @Override
    public TaskExecutionResult process(AiTask task) {
        QuestionGenerateTaskPayload payload = readPayload(task.getPayloadJson(), QuestionGenerateTaskPayload.class);
        if (payload.getMaterialId() == null) {
            throw new BusinessException("materialId is required for question generation task");
        }

        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setModelName(payload.getModelName());
        request.setTitle(payload.getTitle());
        request.setQuestionCount(payload.getQuestionCount());
        request.setSingleCount(payload.getSingleCount());
        request.setJudgeCount(payload.getJudgeCount());
        request.setShortAnswerCount(payload.getShortAnswerCount());
        request.setDifficultyLevel(payload.getDifficultyLevel());

        QuestionSetDetailVO result = aiQuestionService.generateQuestionSet(task.getUserId(), payload.getMaterialId(), request);
        return new TaskExecutionResult(writeJson(result), 100);
    }

    /**
     * 读取任务参数 JSON。
     */
    private <T> T readPayload(String payloadJson, Class<T> payloadType) {
        try {
            return objectMapper.readValue(payloadJson, payloadType);
        } catch (Exception exception) {
            throw new BusinessException(500, "Failed to parse question task payload: " + exception.getMessage());
        }
    }

    /**
     * 写出任务结果 JSON。
     */
    private String writeJson(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "Failed to serialize question task result: " + exception.getMessage());
        }
    }
}
