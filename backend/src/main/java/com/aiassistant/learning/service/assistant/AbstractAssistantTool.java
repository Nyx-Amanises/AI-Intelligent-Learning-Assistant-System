package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;

public abstract class AbstractAssistantTool implements AssistantTool {

    private final ObjectMapper objectMapper;

    protected AbstractAssistantTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected ToolExecutionResult success(
            String toolName,
            Object args,
            Object result,
            String summaryText,
            LocalDateTime startedAt
    ) {
        return new ToolExecutionResult(
                toolName,
                "SUCCESS",
                toJson(args),
                toJson(result),
                summaryText,
                null,
                startedAt,
                LocalDateTime.now()
        );
    }

    protected ToolExecutionResult failure(
            String toolName,
            Object args,
            String errorMessage,
            LocalDateTime startedAt
    ) {
        return new ToolExecutionResult(
                toolName,
                "FAILED",
                toJson(args),
                null,
                null,
                errorMessage,
                startedAt,
                LocalDateTime.now()
        );
    }

    protected String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "助手工具结果序列化失败: " + exception.getMessage());
        }
    }
}
