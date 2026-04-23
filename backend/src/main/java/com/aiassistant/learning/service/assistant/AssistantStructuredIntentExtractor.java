package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 结构化意图提取器。
 *
 * <p>它使用大模型把用户自然语言转成 {@link AssistantStructuredIntent}，
 * 供后续规则解析和工具规划使用。</p>
 */
@Component
public class AssistantStructuredIntentExtractor {

    /** AI 聊天服务。 */
    private final AiChatService aiChatService;
    /** AI 配置服务。 */
    private final AiConfigService aiConfigService;
    /** JSON 解析工具。 */
    private final ObjectMapper objectMapper;

    public AssistantStructuredIntentExtractor(
            AiChatService aiChatService,
            AiConfigService aiConfigService,
            ObjectMapper objectMapper
    ) {
        this.aiChatService = aiChatService;
        this.aiConfigService = aiConfigService;
        this.objectMapper = objectMapper;
    }

    /**
     * 提取用户消息中的结构化意图。
     */
    public AssistantStructuredIntent extract(String userMessage, String modelName) {
        if (!shouldUseExtraction(userMessage)) {
            return AssistantStructuredIntent.empty();
        }
        try {
            String content = aiChatService.chat(
                    buildSystemPrompt(),
                    buildUserPrompt(userMessage),
                    resolveModelName(modelName),
                    0.1
            );
            return parseResponse(content);
        } catch (Exception ignored) {
            return AssistantStructuredIntent.empty();
        }
    }

    /**
     * 判断当前是否可以使用大模型做意图提取。
     */
    private boolean shouldUseExtraction(String userMessage) {
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
     * 解析用于意图提取的模型名称。
     */
    private String resolveModelName(String modelName) {
        if (StringUtils.hasText(modelName)) {
            return modelName.trim();
        }
        return aiConfigService.getResolvedConfig().defaultModel();
    }

    /**
     * 构造意图提取器提示词。
     */
    private String buildSystemPrompt() {
        return """
                你是学习助手系统的“结构化意图提取器”。
                你的职责是把用户消息提取为 JSON，不要回答问题，不要解释，不要输出 Markdown 代码块。
                只输出一个 JSON 对象。

                规则：
                1. interactionMode 仅允许：TASK_CREATE、TASK_CONFIG_REPLY、MATERIAL_SELECTION、MATERIAL_BROWSE、TASK_BROWSE、QUESTION_SET_BROWSE、CHAPTER_BROWSE、CONTEXT_CHALLENGE、STUDY_QA、CHAT、UNSUPPORTED、UNKNOWN。
                2. 如果用户是在正常聊天、问候、感谢、寒暄，interactionMode=CHAT。
                3. 如果用户是在提问资料内容、让你讲解知识点、带学、解释概念、根据资料回答问题，interactionMode=STUDY_QA。
                4. 如果用户是在明确要求“生成总结/出题”，interactionMode=TASK_CREATE。
                5. 如果用户是在补充出题题型、题量、默认配置，interactionMode=TASK_CONFIG_REPLY。
                6. 如果用户是在从候选资料中回复序号、资料ID、确认“这份/那份”，interactionMode=MATERIAL_SELECTION。
                7. 如果用户是在查看资料列表，则 interactionMode=MATERIAL_BROWSE，materialBrowse=true。
                8. 如果用户是在查看任务列表或任务进度，则 interactionMode=TASK_BROWSE，taskList=true。
                9. 如果用户是在查看题集列表，则 interactionMode=QUESTION_SET_BROWSE，questionSetList=true。
                10. 如果用户是在查看章节/目录/大纲，则 interactionMode=CHAPTER_BROWSE，chapterBrowse=true。
                11. 如果用户是在质疑、追问、澄清上一条系统定位/选择/判断，则 interactionMode=CONTEXT_CHALLENGE。
                12. 如果用户要求的是当前系统明显不支持的能力，则 interactionMode=UNSUPPORTED，并尽量填 unsupportedFeature。
                13. requestedTaskTypes 仅允许：SUMMARY、QUESTION_GENERATE。
                14. taskTypeFilter 仅允许：SUMMARY、QUESTION_GENERATE、PRACTICE_REVIEW、EMBEDDING。
                15. taskStatusFilter 仅允许：PENDING、RUNNING、SUCCESS、FAILED、CANCELLED。
                16. exclusiveQuestionType 仅允许：SINGLE、JUDGE、SHORT_ANSWER。
                17. 如果用户明确要看“已生成 Embedding / 已向量化”的资料，则 embeddingReadyOnly=true。
                18. 如果用户是在澄清“是哪一份资料 / id 6 还是 id 7 / 当前定位不明确”，则 materialDisambiguation=true。
                19. 如果用户是在质疑、追问、澄清上一条系统定位/选择/判断，则 contextChallenge=true。
                20. 如果某字段无法确定，请返回 null；布尔字段默认 false；数组默认 []。

                输出 JSON schema：
                {
                  "interactionMode": "UNKNOWN",
                  "unsupportedFeature": null,
                  "requestedTaskTypes": [],
                  "materialQuery": null,
                  "materialBrowse": false,
                  "embeddingReadyOnly": false,
                  "taskList": false,
                  "taskTypeFilter": null,
                  "taskStatusFilter": null,
                  "questionSetList": false,
                  "questionSetKeyword": null,
                  "questionSetStatus": null,
                  "questionSetDifficultyLevel": null,
                  "chapterBrowse": false,
                  "chapterKeyword": null,
                  "questionCount": null,
                  "singleCount": null,
                  "judgeCount": null,
                  "shortAnswerCount": null,
                  "difficultyLevel": null,
                  "exclusiveQuestionType": null,
                  "defaultChoice": false,
                  "questionConfigReply": false,
                  "contextChallenge": false,
                  "materialDisambiguation": false
                }
                """;
    }

    /**
     * 构造用户提示词。
     */
    private String buildUserPrompt(String userMessage) {
        return "用户消息：\n" + userMessage.trim();
    }

    /**
     * 解析模型返回的 JSON。
     */
    private AssistantStructuredIntent parseResponse(String content) {
        if (!StringUtils.hasText(content)) {
            return AssistantStructuredIntent.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            return AssistantStructuredIntent.builder()
                    .interactionMode(normalizeUpper(readNullableText(root, "interactionMode")))
                    .unsupportedFeature(readNullableText(root, "unsupportedFeature"))
                    .requestedTaskTypes(readStringList(root, "requestedTaskTypes"))
                    .materialQuery(readNullableText(root, "materialQuery"))
                    .materialBrowse(readNullableBoolean(root, "materialBrowse"))
                    .embeddingReadyOnly(readNullableBoolean(root, "embeddingReadyOnly"))
                    .taskList(readNullableBoolean(root, "taskList"))
                    .taskTypeFilter(normalizeUpper(readNullableText(root, "taskTypeFilter")))
                    .taskStatusFilter(normalizeUpper(readNullableText(root, "taskStatusFilter")))
                    .questionSetList(readNullableBoolean(root, "questionSetList"))
                    .questionSetKeyword(readNullableText(root, "questionSetKeyword"))
                    .questionSetStatus(normalizeUpper(readNullableText(root, "questionSetStatus")))
                    .questionSetDifficultyLevel(readNullableInt(root, "questionSetDifficultyLevel"))
                    .chapterBrowse(readNullableBoolean(root, "chapterBrowse"))
                    .chapterKeyword(readNullableText(root, "chapterKeyword"))
                    .questionCount(readNullableInt(root, "questionCount"))
                    .singleCount(readNullableInt(root, "singleCount"))
                    .judgeCount(readNullableInt(root, "judgeCount"))
                    .shortAnswerCount(readNullableInt(root, "shortAnswerCount"))
                    .difficultyLevel(readNullableInt(root, "difficultyLevel"))
                    .exclusiveQuestionType(normalizeUpper(readNullableText(root, "exclusiveQuestionType")))
                    .defaultChoice(readNullableBoolean(root, "defaultChoice"))
                    .questionConfigReply(readNullableBoolean(root, "questionConfigReply"))
                    .contextChallenge(readNullableBoolean(root, "contextChallenge"))
                    .materialDisambiguation(readNullableBoolean(root, "materialDisambiguation"))
                    .build();
        } catch (Exception ignored) {
            return AssistantStructuredIntent.empty();
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
                values.add(item.asText().trim().toUpperCase());
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
     * 读取可空布尔字段。
     */
    private Boolean readNullableBoolean(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /**
     * 读取可空整数字段。
     */
    private Integer readNullableInt(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.intValue();
        }
        if (node.isTextual() && StringUtils.hasText(node.asText())) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 将字符串转成大写。
     */
    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
