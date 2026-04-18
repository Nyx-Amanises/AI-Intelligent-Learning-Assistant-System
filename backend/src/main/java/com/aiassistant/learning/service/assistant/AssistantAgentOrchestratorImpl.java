package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AssistantMemoryService;
import com.aiassistant.learning.service.AssistantMemoryService.MemorySnippet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AssistantAgentOrchestratorImpl implements AssistantAgentOrchestrator {

    private static final List<String> MATERIAL_DETAIL_KEYWORDS = List.of("资料信息", "资料详情", "这份资料", "页数", "标题", "难度", "字符数");
    private static final List<String> SUMMARY_TASK_KEYWORDS = List.of("生成总结", "生成ai总结", "帮我总结", "总结一下", "做个总结");
    private static final List<String> QUESTION_TASK_KEYWORDS = List.of("生成题", "生成练习题", "出题", "来一套题", "生成题集");
    private static final List<String> TASK_STATUS_KEYWORDS = List.of("任务", "进度", "执行到哪", "状态");
    private static final List<String> PRACTICE_KEYWORDS = List.of("练习", "错题", "判分", "这次练习", "为什么错");
    private static final List<String> QUESTION_SET_KEYWORDS = List.of("题集", "这套题", "题目分布", "题型");

    private final AssistantToolRegistry toolRegistry;
    private final AssistantMemoryService assistantMemoryService;
    private final AiConfigService aiConfigService;
    private final AiChatService aiChatService;
    private final AssistantTaskIntentParser taskIntentParser;
    private final ObjectMapper objectMapper;

    public AssistantAgentOrchestratorImpl(
            AssistantToolRegistry toolRegistry,
            AssistantMemoryService assistantMemoryService,
            AiConfigService aiConfigService,
            AiChatService aiChatService,
            AssistantTaskIntentParser taskIntentParser,
            ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.assistantMemoryService = assistantMemoryService;
        this.aiConfigService = aiConfigService;
        this.aiChatService = aiChatService;
        this.taskIntentParser = taskIntentParser;
        this.objectMapper = objectMapper;
    }

    @Override
    public AssistantPreparedResult prepare(Long userId, AssistantSession session, String userMessage, String modelName) {
        List<MemorySnippet> memories = assistantMemoryService.findRelevantMemories(userId, userMessage, 3);
        List<PlannedTool> plan = planTools(session, userMessage);
        List<AssistantTool.ToolExecutionResult> executions = executeTools(userId, session, userMessage, modelName, plan);
        String resolvedModelName = resolveModelName(modelName);
        List<MemoryUsage> usedMemories = memories.stream()
                .map(memory -> new MemoryUsage(
                        memory.id(),
                        memory.memoryScope(),
                        memory.memoryType(),
                        memory.topicName(),
                        memory.summaryText()
                ))
                .toList();
        return new AssistantPreparedResult(
                shouldUseAiModel(),
                buildSystemPrompt(),
                buildUserPrompt(session, userMessage, memories, executions),
                buildFallbackReply(session, memories, executions),
                toJson(Map.of(
                        "strategy", executions.isEmpty() ? "DIRECT_REPLY" : "TOOL_AUGMENTED",
                        "memoryCount", memories.size(),
                        "toolCount", executions.size()
                )),
                toJson(plan),
                resolvedModelName,
                executions,
                usedMemories
        );
    }

    @Override
    public AssistantAgentResult respond(Long userId, AssistantSession session, String userMessage, String modelName) {
        AssistantPreparedResult preparedResult = prepare(userId, session, userMessage, modelName);
        String assistantReply = preparedResult.fallbackReply();
        if (preparedResult.useModel()) {
            try {
                assistantReply = aiChatService.chat(
                        preparedResult.systemPrompt(),
                        preparedResult.userPrompt(),
                        preparedResult.modelName(),
                        0.3
                );
            } catch (Exception ignored) {
                assistantReply = preparedResult.fallbackReply();
            }
        }
        captureConversationMemory(userId, session, userMessage, assistantReply);
        return new AssistantAgentResult(
                assistantReply,
                preparedResult.reasoningJson(),
                preparedResult.toolPlanJson(),
                preparedResult.modelName(),
                preparedResult.toolExecutions(),
                preparedResult.usedMemories()
        );
    }

    @Override
    public void captureConversationMemory(Long userId, AssistantSession session, String userMessage, String assistantReply) {
        assistantMemoryService.captureConversationMemory(userId, session, userMessage, assistantReply);
    }

    private List<PlannedTool> planTools(AssistantSession session, String userMessage) {
        LinkedHashSet<String> toolNames = new LinkedHashSet<>();
        List<PlannedTool> plan = new ArrayList<>();
        String normalizedMessage = userMessage == null ? "" : userMessage.trim();
        boolean summaryIntent = taskIntentParser.looksLikeSummaryRequest(normalizedMessage)
                || AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, SUMMARY_TASK_KEYWORDS);
        boolean questionIntent = taskIntentParser.looksLikeQuestionRequest(normalizedMessage)
                || AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, QUESTION_TASK_KEYWORDS);

        if (AssistantToolSupport.resolveTaskId(session, normalizedMessage) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, TASK_STATUS_KEYWORDS)) {
            addPlan(plan, toolNames, "task.get_status", "用户正在询问任务进度");
        }
        if (AssistantToolSupport.resolvePracticeSessionId(session) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, PRACTICE_KEYWORDS)) {
            addPlan(plan, toolNames, "practice.detail", "当前会话绑定了练习记录");
        }
        if (AssistantToolSupport.resolveQuestionSetId(session) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, QUESTION_SET_KEYWORDS)) {
            addPlan(plan, toolNames, "question_set.detail", "当前会话绑定了题集");
        }
        if (AssistantToolSupport.resolveMaterialId(session) != null) {
            if (summaryIntent) {
                addPlan(plan, toolNames, "task.submit_summary", "用户明确提出生成总结");
            } else if (questionIntent) {
                addPlan(plan, toolNames, "task.submit_question_generate", "用户明确提出生成题目");
            } else if (AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, MATERIAL_DETAIL_KEYWORDS)) {
                addPlan(plan, toolNames, "material.detail", "用户更像在问当前资料基本信息");
            } else {
                addPlan(plan, toolNames, "rag.retrieve", "默认走资料检索问答");
            }
        }
        return plan.stream().limit(4).toList();
    }

    private List<AssistantTool.ToolExecutionResult> executeTools(
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            List<PlannedTool> plan
    ) {
        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        AssistantTool.ToolContext toolContext = new AssistantTool.ToolContext(userId, session, userMessage, modelName);
        for (PlannedTool plannedTool : plan) {
            AssistantTool tool = toolRegistry.findTool(plannedTool.toolName());
            if (tool == null || !tool.supports(toolContext)) {
                continue;
            }
            executions.add(tool.execute(toolContext));
        }
        return executions;
    }

    private boolean shouldUseAiModel() {
        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        return Boolean.TRUE.equals(config.enabled())
                && !Boolean.TRUE.equals(config.mockMode())
                && StringUtils.hasText(config.apiKey());
    }

    private String buildSystemPrompt() {
        return """
                你是系统内置的 AI 学习助手。
                你必须优先依据当前会话上下文、检索片段、练习记录、题集信息、任务状态和记忆摘要回答。
                不要假装看过系统里没有提供的数据，不要编造页码、进度和知识点。
                如果工具已经创建了任务，请直接告诉用户任务号和当前状态。
                如果给出了资料片段，请尽量结合片段内容给出清晰、简洁、可执行的回答。
                回答保持中文，风格像学习教练，不要输出 Markdown 表格。
                """;
    }

    private String buildUserPrompt(
            AssistantSession session,
            String userMessage,
            List<MemorySnippet> memories,
            List<AssistantTool.ToolExecutionResult> executions
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前会话上下文：").append(System.lineSeparator())
                .append("contextType=").append(session == null ? null : session.getCurrentContextType()).append(System.lineSeparator())
                .append("contextId=").append(session == null ? null : session.getCurrentContextId()).append(System.lineSeparator())
                .append("materialId=").append(session == null ? null : session.getCurrentMaterialId()).append(System.lineSeparator())
                .append("questionSetId=").append(session == null ? null : session.getCurrentQuestionSetId()).append(System.lineSeparator())
                .append("practiceSessionId=").append(session == null ? null : session.getCurrentPracticeSessionId()).append(System.lineSeparator())
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

    private String buildFallbackReply(
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
        return "我已经收到你的问题，但当前会话还没有绑定具体资料、题集或练习记录。你可以从某份资料或某次练习进入后继续问，我就能用系统里的真实数据来回答。";
    }

    private String resolveModelName(String modelName) {
        if (StringUtils.hasText(modelName)) {
            return modelName.trim();
        }
        return aiConfigService.getResolvedConfig().defaultModel();
    }

    private void addPlan(List<PlannedTool> plan, LinkedHashSet<String> toolNames, String toolName, String reason) {
        if (toolNames.add(toolName)) {
            plan.add(new PlannedTool(toolName, reason));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"json_serialize_failed\"}";
        }
    }

    private record PlannedTool(String toolName, String reason) {
    }
}
