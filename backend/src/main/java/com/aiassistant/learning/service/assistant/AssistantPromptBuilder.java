package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AssistantMemoryService.MemorySnippet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /** 资料片段正文写入提示词时的字数上限，避免单条片段挤占上下文。 */
    private static final int EVIDENCE_CONTENT_LIMIT = 600;

    /** 只用于读取工具结果 JSON。 */
    private static final ObjectMapper JSON = new ObjectMapper();

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
                回答资料内容相关的问题时：
                - 只依据“资料片段”作答，在用到片段内容的句子末尾用 [1]、[2] 这样的编号标注出处，编号必须与资料片段的编号一致；
                - 资料片段不足以回答问题时，要明确说“资料中没有找到足够依据”，可以补充一般性知识，但要说明这部分不是来自资料；
                - 不要编造资料名称、页码或片段编号。
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
            for (AssistantTool.ToolExecutionResult execution : executions) {
                appendRagEvidence(builder, execution);
            }
        }
        return builder.toString().trim();
    }

    /**
     * 把 rag.retrieve 的检索结果展开成带编号的“资料片段”，供模型引用。
     *
     * <p>工具摘要里只有片段开头，这里给出正文（截断到 {@value #EVIDENCE_CONTENT_LIMIT} 字），
     * 编号与前端展示的「参考来源」一一对应。</p>
     */
    private void appendRagEvidence(StringBuilder builder, AssistantTool.ToolExecutionResult execution) {
        if (!"rag.retrieve".equals(execution.toolName())
                || !"SUCCESS".equalsIgnoreCase(execution.status())
                || !StringUtils.hasText(execution.toolResultJson())) {
            return;
        }
        JsonNode evidence;
        try {
            evidence = JSON.readTree(execution.toolResultJson());
        } catch (Exception exception) {
            return;
        }
        JsonNode citations = evidence.path("citations");
        if (!citations.isArray()) {
            return;
        }

        String materialTitle = evidence.path("materialTitle").asText("");
        builder.append(System.lineSeparator())
                .append("资料片段")
                .append(StringUtils.hasText(materialTitle) ? "（来自《" + materialTitle + "》）" : "")
                .append("，回答时用 [编号] 标注出处：")
                .append(System.lineSeparator());
        if (citations.isEmpty()) {
            builder.append("没有检索到相关片段，请明确告诉用户资料中没有找到依据。").append(System.lineSeparator());
            return;
        }
        for (JsonNode citation : citations) {
            String content = citation.path("contentText").asText("").replaceAll("\\s+", " ").trim();
            if (content.length() > EVIDENCE_CONTENT_LIMIT) {
                content = content.substring(0, EVIDENCE_CONTENT_LIMIT) + "…";
            }
            builder.append('[').append(citation.path("index").asInt()).append("] ")
                    .append(citation.path("locationText").asText("资料片段"))
                    .append(System.lineSeparator())
                    .append(content)
                    .append(System.lineSeparator());
        }
        if ("WEAK".equals(evidence.path("evidenceLevel").asText())) {
            builder.append("检索提示：以上片段与问题的相关度都较低，如果片段无法回答问题，请明确说明资料中没有找到足够依据。")
                    .append(System.lineSeparator());
        }
        if (evidence.path("degraded").asBoolean(false)) {
            builder.append("检索提示：").append(evidence.path("degradeReason").asText("向量检索不可用"))
                    .append("，结果可能不如语义检索准确。")
                    .append(System.lineSeparator());
        }
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
