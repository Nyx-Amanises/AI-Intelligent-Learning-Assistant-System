package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;

/**
 * 助手工具基类。
 *
 * <p>封装成功、失败、等待确认三种标准返回结构，具体工具只关注自己的业务逻辑。</p>
 */
public abstract class AbstractAssistantTool implements AssistantTool {

    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;

    protected AbstractAssistantTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 构造成功结果。
     */
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

    /**
     * 构造失败结果。
     */
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

    /**
     * 构造等待用户确认的结果。
     */
    protected ToolExecutionResult waiting(
            String toolName,
            Object args,
            Object result,
            String summaryText,
            LocalDateTime startedAt
    ) {
        return new ToolExecutionResult(
                toolName,
                "WAITING",
                toJson(args),
                toJson(result),
                summaryText,
                null,
                startedAt,
                LocalDateTime.now()
        );
    }

    /**
     * 将工具参数或结果转换成 JSON 字符串。
     */
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
