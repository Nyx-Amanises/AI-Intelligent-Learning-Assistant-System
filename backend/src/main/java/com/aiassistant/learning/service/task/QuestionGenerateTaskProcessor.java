package com.aiassistant.learning.service.task;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.QuestionGenerateTaskPayload;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.service.AiQuestionService;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * AI 题目生成任务处理器。
 */
@Component
public class QuestionGenerateTaskProcessor implements AiTaskProcessor {

    /** 题目生成服务。 */
    private final AiQuestionService aiQuestionService;
    private final QuestionSetService questionSetService;
    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    public QuestionGenerateTaskProcessor(
            AiQuestionService aiQuestionService, QuestionSetService questionSetService, ObjectMapper objectMapper
    ) {
        this.aiQuestionService = aiQuestionService;
        this.questionSetService = questionSetService;
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

        QuestionSetDetailVO result;
        if (payload.getQuestionSetId() == null) {
            // 兼容升级前已入队且尚未关联题集的任务。
            result = aiQuestionService.generateQuestionSet(task.getUserId(), payload.getMaterialId(), payload.toRequest());
        } else {
            result = questionSetService.getQuestionSetDetail(task.getUserId(), payload.getQuestionSetId());
            if (!Objects.equals(result.getMaterialId(), payload.getMaterialId())) {
                throw new BusinessException("题集与任务资料不匹配");
            }
            // 题目已落库但任务结果写入失败时，重试只恢复结果，避免重复生成。
            if (!"PUBLISHED".equals(result.getStatus())) {
                result = aiQuestionService.generateQuestionSet(
                        task.getUserId(), payload.getMaterialId(), payload.toRequest(), payload.getQuestionSetId());
            }
        }
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
