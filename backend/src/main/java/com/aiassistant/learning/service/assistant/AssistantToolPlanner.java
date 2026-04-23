package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AI 工具规划器。
 *
 * <p>当规则解析不足以判断用户意图时，会调用大模型生成结构化工具计划。</p>
 */
@Component
public class AssistantToolPlanner {

    /** 单轮最多允许规划的工具调用数量。 */
    private static final int MAX_TOOL_CALLS = 4;

    /** AI 聊天服务。 */
    private final AiChatService aiChatService;
    /** AI 配置服务。 */
    private final AiConfigService aiConfigService;
    /** 工具注册表。 */
    private final AssistantToolRegistry toolRegistry;
    /** JSON 工具。 */
    private final ObjectMapper objectMapper;

    public AssistantToolPlanner(
            AiChatService aiChatService,
            AiConfigService aiConfigService,
            AssistantToolRegistry toolRegistry,
            ObjectMapper objectMapper
    ) {
        this.aiChatService = aiChatService;
        this.aiConfigService = aiConfigService;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用大模型生成工具计划。
     */
    public AssistantToolPlan plan(
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent
    ) {
        if (!shouldUsePlanner(userMessage)) {
            return AssistantToolPlan.empty();
        }
        try {
            String content = aiChatService.chat(
                    buildSystemPrompt(),
                    buildUserPrompt(session, userMessage, structuredIntent),
                    resolveModelName(modelName),
                    0.05
            );
            return parseResponse(content);
        } catch (Exception ignored) {
            return AssistantToolPlan.empty();
        }
    }

    /**
     * 判断当前环境是否允许使用模型规划器。
     */
    private boolean shouldUsePlanner(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        return Boolean.TRUE.equals(config.enabled())
                && !Boolean.TRUE.equals(config.mockMode())
                && StringUtils.hasText(config.apiKey())
                && StringUtils.hasText(config.defaultModel());
    }

    /**
     * 解析规划器使用的模型名称。
     */
    private String resolveModelName(String modelName) {
        if (StringUtils.hasText(modelName)) {
            return modelName.trim();
        }
        return aiConfigService.getResolvedConfig().defaultModel();
    }

    /**
     * 构造工具规划器的系统提示词。
     */
    private String buildSystemPrompt() {
        return """
                你是学习系统 Agent 的“工具规划器”。你的任务是把用户消息规划成可执行工具计划。
                只输出一个 JSON 对象，不要解释，不要输出 Markdown 代码块。

                你必须遵守：
                1. interactionMode 仅允许：TASK_CREATE、TASK_CONFIG_REPLY、MATERIAL_SELECTION、MATERIAL_BROWSE、TASK_BROWSE、QUESTION_SET_BROWSE、CHAPTER_BROWSE、CONTEXT_CHALLENGE、STUDY_QA、CHAT、UNSUPPORTED、UNKNOWN。
                2. replyStrategy 仅允许：EXECUTE_TOOLS、ASK_CLARIFICATION、DIRECT_REPLY、USE_MODEL、UNSUPPORTED、FALLBACK。
                3. toolName 仅允许使用下面工具：
                   - material.search：按标题/关键词定位资料。参数：queryText。
                   - material.list：查看资料列表。参数：keyword、embeddingReadyOnly。
                   - material.detail：查看当前资料详情和 Embedding 状态。参数：无。
                   - material.chapter_outline：查看资料章节/目录。参数：materialQuery、chapterKeyword。
                   - rag.retrieve：围绕当前资料检索回答。参数：queryText。
                   - task.submit_summary：创建 AI 总结任务。参数：materialQuery、summaryType(STANDARD/OUTLINE/EXAM)、saveAsNote。
                   - task.submit_question_generate：创建 AI 出题任务。参数：materialQuery、questionCount、singleCount、judgeCount、shortAnswerCount、difficultyLevel。
                   - task.list：查看任务列表。参数：taskTypeFilter、taskStatusFilter。
                   - task.get_status：查看当前/指定任务状态。参数：taskId。
                   - question_set.list：查看题集列表。参数：keyword、status、difficultyLevel、currentMaterialOnly。
                   - question_set.detail：查看当前题集详情。参数：无。
                   - practice.detail：查看当前练习记录详情。参数：无。
                4. 不要编造资料 ID、题集 ID、任务 ID。用户只给标题或关键词时，用 material.search 或 materialQuery。
                5. 如果用户要创建总结/出题，但资料不明确，应先规划 material.search；同名资料由后端继续反问。
                6. 如果用户只是问候、感谢或普通聊天，不要规划检索工具，interactionMode=CHAT。
                7. 如果用户是在问资料内容、要求讲解、带学、解释概念，优先规划 rag.retrieve；如果没有资料上下文但有资料名，先规划 material.search。
                8. 如果缺少必须参数，把缺失项写入 missingSlots，并设置 replyStrategy=ASK_CLARIFICATION。
                9. 如果系统明显不支持用户要求，设置 interactionMode=UNSUPPORTED、replyStrategy=UNSUPPORTED，并写 unsupportedFeature。
                10. 如果用户正在补充出题配置，例如“10道单选”“全出选择题”“默认”，必须设置 interactionMode=TASK_CONFIG_REPLY，并规划 task.submit_question_generate 工具调用，把 questionCount、singleCount、judgeCount、shortAnswerCount、difficultyLevel 尽量填入 arguments。

                输出 JSON schema：
                {
                  "interactionMode": "UNKNOWN",
                  "intent": null,
                  "replyStrategy": "FALLBACK",
                  "directReply": null,
                  "clarificationPrompt": null,
                  "unsupportedFeature": null,
                  "missingSlots": [],
                  "toolCalls": [
                    {
                      "toolName": "material.search",
                      "arguments": {"queryText": "Java"},
                      "reason": "先定位资料"
                    }
                  ]
                }
                """;
    }

    /**
     * 构造工具规划器的用户提示词，包含当前会话上下文和已有规则解析结果。
     */
    private String buildUserPrompt(
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前会话上下文：").append(System.lineSeparator())
                .append("contextType=").append(session == null ? null : session.getCurrentContextType()).append(System.lineSeparator())
                .append("contextId=").append(session == null ? null : session.getCurrentContextId()).append(System.lineSeparator())
                .append("materialId=").append(session == null ? null : session.getCurrentMaterialId()).append(System.lineSeparator())
                .append("questionSetId=").append(session == null ? null : session.getCurrentQuestionSetId()).append(System.lineSeparator())
                .append("practiceSessionId=").append(session == null ? null : session.getCurrentPracticeSessionId()).append(System.lineSeparator())
                .append("pendingActionType=").append(session == null ? null : session.getPendingActionType()).append(System.lineSeparator())
                .append("可用工具：").append(String.join(", ", toolRegistry.listToolNames())).append(System.lineSeparator())
                .append("已有结构化意图：").append(structuredIntent == null ? "{}" : toJson(structuredIntent)).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("用户消息：").append(System.lineSeparator())
                .append(userMessage == null ? "" : userMessage.trim());
        return builder.toString();
    }

    /**
     * 将模型返回内容解析成工具计划。
     */
    private AssistantToolPlan parseResponse(String content) {
        if (!StringUtils.hasText(content)) {
            return AssistantToolPlan.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            List<AssistantToolPlan.ToolCall> toolCalls = parseToolCalls(root.get("toolCalls"));
            return AssistantToolPlan.builder()
                    .planned(true)
                    .interactionMode(normalizeUpper(readNullableText(root, "interactionMode")))
                    .intent(readNullableText(root, "intent"))
                    .replyStrategy(normalizeUpper(readNullableText(root, "replyStrategy")))
                    .directReply(readNullableText(root, "directReply"))
                    .clarificationPrompt(readNullableText(root, "clarificationPrompt"))
                    .unsupportedFeature(readNullableText(root, "unsupportedFeature"))
                    .missingSlots(readStringList(root, "missingSlots"))
                    .toolCalls(toolCalls)
                    .build();
        } catch (Exception ignored) {
            return AssistantToolPlan.empty();
        }
    }

    /**
     * 解析工具调用数组，并过滤不存在的工具名称。
     */
    private List<AssistantToolPlan.ToolCall> parseToolCalls(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        Set<String> allowedTools = Set.copyOf(toolRegistry.listToolNames());
        List<AssistantToolPlan.ToolCall> calls = new ArrayList<>();
        for (JsonNode item : node) {
            if (calls.size() >= MAX_TOOL_CALLS) {
                break;
            }
            String toolName = readNullableText(item, "toolName");
            if (!StringUtils.hasText(toolName) || !allowedTools.contains(toolName)) {
                continue;
            }
            Map<String, Object> arguments = readArguments(item.get("arguments"));
            calls.add(AssistantToolPlan.ToolCall.builder()
                    .toolName(toolName)
                    .arguments(arguments)
                    .reason(readNullableText(item, "reason"))
                    .build());
        }
        return calls;
    }

    /**
     * 读取工具参数。
     */
    private Map<String, Object> readArguments(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    /**
     * 从模型输出中提取 JSON 对象。
     */
    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*", "").trim();
            trimmed = trimmed.replaceFirst("```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    /**
     * 读取字符串数组字段。
     */
    private List<String> readStringList(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().trim());
            }
        }
        return values;
    }

    /**
     * 读取可空字符串字段。
     */
    private String readNullableText(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        if (node == null || node.isNull() || !StringUtils.hasText(node.asText())) {
            return null;
        }
        return node.asText().trim();
    }

    /**
     * 将字符串转成大写。
     */
    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    /**
     * 安全序列化对象为 JSON。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
