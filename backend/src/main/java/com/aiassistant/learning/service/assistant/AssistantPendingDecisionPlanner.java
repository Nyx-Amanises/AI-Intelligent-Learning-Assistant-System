package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * pending 状态决策器。
 *
 * <p>它专门判断“当前用户消息是否还在回复上一轮待确认动作”，避免把所有多轮状态判断都写成关键词规则。</p>
 */
@Component
public class AssistantPendingDecisionPlanner {

    /** AI 聊天服务。 */
    private final AiChatService aiChatService;
    /** AI 配置服务。 */
    private final AiConfigService aiConfigService;
    /** JSON 工具。 */
    private final ObjectMapper objectMapper;

    public AssistantPendingDecisionPlanner(
            AiChatService aiChatService,
            AiConfigService aiConfigService,
            ObjectMapper objectMapper
    ) {
        this.aiChatService = aiChatService;
        this.aiConfigService = aiConfigService;
        this.objectMapper = objectMapper;
    }

    /**
     * 判断当前消息对 pending 状态的影响。
     */
    public AssistantPendingDecision decide(
            AssistantSession session,
            AssistantPendingActionPayload pendingPayload,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantToolPlan toolPlan,
            String modelName
    ) {
        if (!shouldUsePlanner(session, userMessage)) {
            return AssistantPendingDecision.unknown();
        }
        try {
            String content = aiChatService.chat(
                    buildSystemPrompt(),
                    buildUserPrompt(session, pendingPayload, userMessage, structuredIntent, toolPlan),
                    resolveModelName(modelName),
                    0.05
            );
            return parseResponse(content);
        } catch (Exception ignored) {
            return AssistantPendingDecision.unknown();
        }
    }

    /**
     * 判断是否启用模型 pending 决策。
     */
    private boolean shouldUsePlanner(AssistantSession session, String userMessage) {
        if (session == null
                || !StringUtils.hasText(session.getPendingActionType())
                || !StringUtils.hasText(userMessage)) {
            return false;
        }
        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        return Boolean.TRUE.equals(config.enabled())
                && !Boolean.TRUE.equals(config.mockMode())
                && StringUtils.hasText(config.apiKey())
                && StringUtils.hasText(config.defaultModel());
    }

    /**
     * 解析模型名称。
     */
    private String resolveModelName(String modelName) {
        if (StringUtils.hasText(modelName)) {
            return modelName.trim();
        }
        return aiConfigService.getResolvedConfig().defaultModel();
    }

    /**
     * 构造系统提示词。
     */
    private String buildSystemPrompt() {
        return """
                你是学习系统 Agent 的“pending 状态决策器”。你的任务不是回答用户，而是判断用户新消息是否还在回复上一轮待确认动作。
                只输出一个 JSON 对象，不要解释，不要输出 Markdown 代码块。

                decision 只能是：
                - CONTINUE：用户正在补充上一轮 pending 需要的信息。
                - INTERRUPT：用户开启了新任务、新问题、新查询或换了上下文，应打断旧 pending 并重新理解当前消息。
                - CANCEL：用户明确只是在取消/放弃上一轮 pending，且没有提出新的可执行请求。
                - CLARIFY：用户消息太模糊，需要继续追问。
                - UNKNOWN：无法判断。

                重要判断规则：
                1. 当前 pendingActionType=QUESTION_CONFIG 时，只有“题量/题型/难度/默认配置”这类回复才是 CONTINUE。
                   例：10道单选、单选5判断3简答2、默认、难度3。
                2. QUESTION_CONFIG 状态下，如果用户新消息里出现新的资料名、要求根据另一份资料出题/总结/讲解/查资料，应为 INTERRUPT。
                3. 当前 pendingActionType=MATERIAL_SELECTION 时，只有用户在选择候选资料、回复序号、资料ID、这份/那份，才是 CONTINUE。
                4. MATERIAL_SELECTION 状态下，如果用户改成问知识点、查任务、查资料列表、普通聊天或创建另一个任务，应为 INTERRUPT。
                5. 如果用户只说“算了/取消/不用了/先不做了”等，应为 CANCEL。
                6. 如果用户说“算了，帮我查一下资料”这种取消旧动作并提出新请求，应为 INTERRUPT，不是 CANCEL。
                7. interactionMode 仅允许：TASK_CREATE、TASK_CONFIG_REPLY、MATERIAL_SELECTION、MATERIAL_BROWSE、TASK_BROWSE、QUESTION_SET_BROWSE、CHAPTER_BROWSE、CONTEXT_CHALLENGE、STUDY_QA、CHAT、UNSUPPORTED、UNKNOWN。

                输出 JSON schema：
                {
                  "decision": "UNKNOWN",
                  "interactionMode": "UNKNOWN",
                  "reason": null,
                  "confidence": 0.0
                }
                """;
    }

    /**
     * 构造用户提示词。
     */
    private String buildUserPrompt(
            AssistantSession session,
            AssistantPendingActionPayload pendingPayload,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantToolPlan toolPlan
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前会话上下文：").append(System.lineSeparator())
                .append("contextType=").append(session == null ? null : session.getCurrentContextType()).append(System.lineSeparator())
                .append("contextId=").append(session == null ? null : session.getCurrentContextId()).append(System.lineSeparator())
                .append("materialId=").append(session == null ? null : session.getCurrentMaterialId()).append(System.lineSeparator())
                .append("questionSetId=").append(session == null ? null : session.getCurrentQuestionSetId()).append(System.lineSeparator())
                .append("practiceSessionId=").append(session == null ? null : session.getCurrentPracticeSessionId()).append(System.lineSeparator())
                .append("pendingActionType=").append(session == null ? null : session.getPendingActionType()).append(System.lineSeparator())
                .append("pendingPayload=").append(toJson(pendingPayload)).append(System.lineSeparator())
                .append("已有结构化意图=").append(toJson(structuredIntent)).append(System.lineSeparator())
                .append("已有工具计划=").append(toJson(toolPlan)).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("用户新消息：").append(System.lineSeparator())
                .append(userMessage == null ? "" : userMessage.trim());
        return builder.toString();
    }

    /**
     * 解析模型返回。
     */
    private AssistantPendingDecision parseResponse(String content) {
        if (!StringUtils.hasText(content)) {
            return AssistantPendingDecision.unknown();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            return AssistantPendingDecision.builder()
                    .decision(normalizeDecision(readNullableText(root, "decision")))
                    .interactionMode(normalizeUpper(readNullableText(root, "interactionMode")))
                    .reason(readNullableText(root, "reason"))
                    .confidence(readNullableDouble(root, "confidence"))
                    .build();
        } catch (Exception ignored) {
            return AssistantPendingDecision.unknown();
        }
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
     * 读取可空数字字段。
     */
    private Double readNullableDouble(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isTextual() && StringUtils.hasText(node.asText())) {
            try {
                return Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 规范化 pending 决策。
     */
    private String normalizeDecision(String value) {
        String normalized = normalizeUpper(value);
        if ("CONTINUE".equals(normalized)
                || "INTERRUPT".equals(normalized)
                || "CANCEL".equals(normalized)
                || "CLARIFY".equals(normalized)) {
            return normalized;
        }
        return "UNKNOWN";
    }

    /**
     * 转大写。
     */
    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    /**
     * 安全序列化对象。
     */
    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
