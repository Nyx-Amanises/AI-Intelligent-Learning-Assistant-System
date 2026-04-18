package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import java.util.List;

public interface AssistantAgentOrchestrator {

    AssistantPreparedResult prepare(Long userId, AssistantSession session, String userMessage, String modelName);

    AssistantAgentResult respond(Long userId, AssistantSession session, String userMessage, String modelName);

    void captureConversationMemory(Long userId, AssistantSession session, String userMessage, String assistantReply);

    record AssistantPreparedResult(
            boolean useModel,
            String systemPrompt,
            String userPrompt,
            String fallbackReply,
            String reasoningJson,
            String toolPlanJson,
            String modelName,
            List<AssistantTool.ToolExecutionResult> toolExecutions,
            List<MemoryUsage> usedMemories
    ) {
    }

    record AssistantAgentResult(
            String assistantReply,
            String reasoningJson,
            String toolPlanJson,
            String modelName,
            List<AssistantTool.ToolExecutionResult> toolExecutions,
            List<MemoryUsage> usedMemories
    ) {
    }

    record MemoryUsage(
            Long id,
            String memoryScope,
            String memoryType,
            String topicName,
            String summaryText
    ) {
    }
}
