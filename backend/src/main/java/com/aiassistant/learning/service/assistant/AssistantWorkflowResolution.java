package com.aiassistant.learning.service.assistant;

import java.util.List;

/**
 * 某条工作流处理用户消息后的结果。
 *
 * @param handled      是否已经由该工作流处理完毕（为 false 时编排器继续尝试下一条路径）
 * @param useModel     是否还需要把上下文交给大模型生成最终回复
 * @param executions   本轮已经执行的工具结果
 * @param replyText    不调用模型时直接返回给用户的文本，可为空
 * @param planSnapshot 调试用的计划快照
 */
public record AssistantWorkflowResolution(
        boolean handled,
        boolean useModel,
        List<AssistantTool.ToolExecutionResult> executions,
        String replyText,
        Object planSnapshot
) {

    /**
     * 表示“本条工作流不处理这条消息”。
     */
    public static AssistantWorkflowResolution notHandled() {
        return new AssistantWorkflowResolution(false, false, List.of(), null, List.of());
    }
}
