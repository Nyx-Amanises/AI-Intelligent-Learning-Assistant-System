package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AssistantStructuredIntentExtractor {

    private final AiChatService aiChatService;
    private final AiConfigService aiConfigService;
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

    private String resolveModelName(String modelName) {
        if (StringUtils.hasText(modelName)) {
            return modelName.trim();
        }
        return aiConfigService.getResolvedConfig().defaultModel();
    }

    private String buildSystemPrompt() {
        return """
                你是学习助手系统的“结构化意图提取器”。
                你的职责是把用户消息提取为 JSON，不要回答问题，不要解释，不要输出 Markdown 代码块。
                只输出一个 JSON 对象。

                规则：
                1. requestedTaskTypes 仅允许：SUMMARY、QUESTION_GENERATE。
                2. taskTypeFilter 仅允许：SUMMARY、QUESTION_GENERATE、PRACTICE_REVIEW、EMBEDDING。
                3. taskStatusFilter 仅允许：PENDING、RUNNING、SUCCESS、FAILED、CANCELLED。
                4. exclusiveQuestionType 仅允许：SINGLE、JUDGE、SHORT_ANSWER。
                5. 如果用户是在查看资料列表，则 materialBrowse=true。
                6. 如果用户明确要看“已生成 Embedding / 已向量化”的资料，则 embeddingReadyOnly=true。
                7. 如果用户是在查看任务列表，则 taskList=true。
                8. 如果用户是在查看题集列表，则 questionSetList=true。
                9. 如果用户是在查看章节/目录/大纲，则 chapterBrowse=true。
                10. 如果某字段无法确定，请返回 null；布尔字段默认 false；数组默认 []。

                输出 JSON schema：
                {
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
                  "defaultChoice": false
                }
                """;
    }

    private String buildUserPrompt(String userMessage) {
        return "用户消息：\n" + userMessage.trim();
    }

    private AssistantStructuredIntent parseResponse(String content) {
        if (!StringUtils.hasText(content)) {
            return AssistantStructuredIntent.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            return AssistantStructuredIntent.builder()
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
                    .build();
        } catch (Exception ignored) {
            return AssistantStructuredIntent.empty();
        }
    }

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

    private String readNullableText(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        if (node == null || node.isNull() || !StringUtils.hasText(node.asText())) {
            return null;
        }
        return node.asText().trim();
    }

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

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
