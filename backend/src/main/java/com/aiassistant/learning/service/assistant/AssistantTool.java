package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import java.time.LocalDateTime;

/**
 * 智能助手工具接口。
 *
 * <p>工具是助手可以调用的“系统能力”，例如查资料、创建总结任务、查看题集详情。</p>
 */
public interface AssistantTool {

    /**
     * 工具唯一名称，规划器会通过这个名称找到工具。
     */
    String name();

    /**
     * 判断当前上下文是否适合执行该工具。
     */
    boolean supports(ToolContext context);

    /**
     * 执行工具。
     */
    ToolExecutionResult execute(ToolContext context);

    /**
     * 工具执行上下文。
     */
    record ToolContext(
            /** 当前用户 ID。 */
            Long userId,
            /** 当前助手会话。 */
            AssistantSession session,
            /** 用户原始消息。 */
            String userMessage,
            /** 用户指定或系统默认的模型名称。 */
            String modelName,
            /** 结构化意图提取结果。 */
            AssistantStructuredIntent structuredIntent
    ) {
    }

    /**
     * 工具执行结果。
     */
    record ToolExecutionResult(
            /** 工具名称。 */
            String toolName,
            /** 执行状态。 */
            String status,
            /** 工具入参 JSON。 */
            String toolArgsJson,
            /** 工具返回结果 JSON。 */
            String toolResultJson,
            /** 给用户看的简短总结。 */
            String summaryText,
            /** 错误信息。 */
            String errorMessage,
            /** 开始时间。 */
            LocalDateTime startedAt,
            /** 结束时间。 */
            LocalDateTime finishedAt
    ) {
    }
}
