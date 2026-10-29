package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiChatService.ChatMessage;
import java.util.List;

/**
 * 助手 Agent 编排器接口。
 *
 * <p>负责编排一轮助手回复：识别意图、调用工具、拼接提示词、决定是否调用大模型。</p>
 */
public interface AssistantAgentOrchestrator {

    /**
     * 预处理一轮对话，为流式回复准备系统提示词、用户提示词和工具结果。
     */
    AssistantPreparedResult prepare(Long userId, AssistantSession session, String userMessage, String modelName);

    /** 同一轮的规划与最终回复共用这份会话历史快照。 */
    AssistantPreparedResult prepare(Long userId, AssistantSession session, String userMessage, String modelName,
                                    List<ChatMessage> history);

    /**
     * 非流式完整执行一轮对话并返回助手回复。
     */
    AssistantAgentResult respond(Long userId, AssistantSession session, String userMessage, String modelName);

    AssistantAgentResult respond(Long userId, AssistantSession session, String userMessage, String modelName,
                                 List<ChatMessage> history);

    /**
     * 从对话中沉淀长期记忆。
     */
    void captureConversationMemory(Long userId, AssistantSession session, String userMessage, String assistantReply);

    /**
     * 已完成工具准备但还没生成最终回复的中间结果。
     */
    record AssistantPreparedResult(
            /** 是否需要继续调用大模型生成最终回复。 */
            boolean useModel,
            /** 系统提示词。 */
            String systemPrompt,
            /** 用户提示词。 */
            String userPrompt,
            /** 不调用模型时的兜底回复。 */
            String fallbackReply,
            /** 推理过程 JSON。 */
            String reasoningJson,
            /** 工具计划 JSON。 */
            String toolPlanJson,
            /** 使用的模型名称。 */
            String modelName,
            /** 工具执行结果。 */
            List<AssistantTool.ToolExecutionResult> toolExecutions,
            /** 本轮使用的记忆。 */
            List<MemoryUsage> usedMemories,
            /** 按角色排序的会话历史与本轮提示词，供同步和流式调用共用。 */
            List<ChatMessage> messages
    ) {
        public AssistantPreparedResult {
            messages = List.copyOf(messages);
        }

        public AssistantPreparedResult(boolean useModel, String systemPrompt, String userPrompt, String fallbackReply,
                                       String reasoningJson, String toolPlanJson, String modelName,
                                       List<AssistantTool.ToolExecutionResult> toolExecutions, List<MemoryUsage> usedMemories) {
            this(useModel, systemPrompt, userPrompt, fallbackReply, reasoningJson, toolPlanJson, modelName,
                    toolExecutions, usedMemories, AssistantConversationMessages.withCurrent(List.of(), userPrompt));
        }
    }

    /**
     * 一轮非流式助手回复结果。
     */
    record AssistantAgentResult(
            /** 最终助手回复。 */
            String assistantReply,
            /** 推理过程 JSON。 */
            String reasoningJson,
            /** 工具计划 JSON。 */
            String toolPlanJson,
            /** 使用的模型名称。 */
            String modelName,
            /** 工具执行结果。 */
            List<AssistantTool.ToolExecutionResult> toolExecutions,
            /** 本轮使用的记忆。 */
            List<MemoryUsage> usedMemories
    ) {
    }

    /**
     * 本轮对话使用到的长期记忆。
     */
    record MemoryUsage(
            /** 记忆 ID。 */
            Long id,
            /** 记忆范围。 */
            String memoryScope,
            /** 记忆类型。 */
            String memoryType,
            /** 主题名称。 */
            String topicName,
            /** 记忆摘要。 */
            String summaryText
    ) {
    }
}
