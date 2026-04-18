package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import java.time.LocalDateTime;

public interface AssistantTool {

    String name();

    boolean supports(ToolContext context);

    ToolExecutionResult execute(ToolContext context);

    record ToolContext(
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent
    ) {
    }

    record ToolExecutionResult(
            String toolName,
            String status,
            String toolArgsJson,
            String toolResultJson,
            String summaryText,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }
}
