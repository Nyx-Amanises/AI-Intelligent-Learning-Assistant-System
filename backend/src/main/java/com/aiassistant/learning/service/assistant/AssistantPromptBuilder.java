package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AssistantMemoryService.MemorySnippet;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 最终回复阶段的提示词与兜底回复构造。
 *
 * <p>只做字符串拼装，不依赖任何外部服务。</p>
 */
@Component
public class AssistantPromptBuilder {

    /**
     * 构造最终回复阶段的系统提示词。
     */
    public String buildSystemPrompt() {
        return """
                你是系统内置的 AI 学习助手。
                你必须优先依据当前会话上下文、检索片段、练习记录、题集信息、任务状态和记忆摘要回答。
                不要假装看过系统里没有提供的数据，不要编造页码、进度和知识点。
                如果工具已经创建了任务，请直接告诉用户任务号和当前状态。
                如果工具结果已经给出确定的资料、题量、任务号或等待补充项，你要忠实复述，不要擅自改动这些事实。
                如果工具状态里有 WAITING，请自然地说明还缺什么信息，同时保持可以继续闲聊的语气。
                如果给出了资料片段，请尽量结合片段内容给出清晰、简洁、可执行的回答。
                回答保持中文，风格像学习教练，不要输出 Markdown 表格。
                """;
    }

    /**
     * 构造最终回复阶段的用户提示词，包含会话上下文、工具结果和记忆。
     */
    public String buildUserPrompt(
            AssistantSession session,
            String userMessage,
            List<MemorySnippet> memories,
            List<AssistantTool.ToolExecutionResult> executions,
            String interactionMode
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前会话上下文：").append(System.lineSeparator())
                .append("contextType=").append(session == null ? null : session.getCurrentContextType()).append(System.lineSeparator())
                .append("contextId=").append(session == null ? null : session.getCurrentContextId()).append(System.lineSeparator())
                .append("materialId=").append(session == null ? null : session.getCurrentMaterialId()).append(System.lineSeparator())
                .append("questionSetId=").append(session == null ? null : session.getCurrentQuestionSetId()).append(System.lineSeparator())
                .append("practiceSessionId=").append(session == null ? null : session.getCurrentPracticeSessionId()).append(System.lineSeparator())
                .append("interactionMode=").append(interactionMode).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("用户消息：").append(System.lineSeparator()).append(userMessage).append(System.lineSeparator()).append(System.lineSeparator())
                .append("相关记忆：").append(System.lineSeparator());
        if (memories.isEmpty()) {
            builder.append("暂无").append(System.lineSeparator());
        } else {
            for (MemorySnippet memory : memories) {
                builder.append("- [")
                        .append(memory.memoryScope())
                        .append("] ")
                        .append(StringUtils.hasText(memory.summaryText()) ? memory.summaryText() : memory.contentText())
                        .append(System.lineSeparator());
            }
        }
        builder.append(System.lineSeparator()).append("工具结果：").append(System.lineSeparator());
        if (executions.isEmpty()) {
            builder.append("暂无工具结果，请在上下文不足时明确说明。");
        } else {
            for (AssistantTool.ToolExecutionResult execution : executions) {
                builder.append("- [")
                        .append(execution.toolName())
                        .append(" / ")
                        .append(execution.status())
                        .append("] ")
                        .append(StringUtils.hasText(execution.summaryText()) ? execution.summaryText() : execution.errorMessage())
                        .append(System.lineSeparator());
            }
        }
        return builder.toString().trim();
    }

    /**
     * 当不能或不需要调用模型时，构造兜底回复。
     */
    public String buildFallbackReply(
            AssistantSession session,
            List<MemorySnippet> memories,
            List<AssistantTool.ToolExecutionResult> executions
    ) {
        if (!executions.isEmpty()) {
            StringBuilder builder = new StringBuilder("我已经结合当前系统数据帮你处理这条请求：");
            int index = 1;
            for (AssistantTool.ToolExecutionResult execution : executions) {
                builder.append(System.lineSeparator())
                        .append(index++)
                        .append(". ")
                        .append(StringUtils.hasText(execution.summaryText()) ? execution.summaryText() : execution.errorMessage());
            }
            return builder.toString().trim();
        }
        if (!memories.isEmpty()) {
            MemorySnippet memory = memories.get(0);
            return "我先结合你之前的学习信息来回答："
                    + System.lineSeparator()
                    + (StringUtils.hasText(memory.summaryText()) ? memory.summaryText() : memory.contentText());
        }
        if (session != null && AssistantToolSupport.resolveMaterialId(session) != null) {
            return "当前会话已经绑定资料。你可以继续直接问知识点，或者明确说“生成总结”“出题”“看任务进度”。";
        }
        return "我已经收到你的问题。当前会话还没有固定上下文；如果需要基于资料回答，请直接告诉我资料标题或资料 ID。";
    }
}
