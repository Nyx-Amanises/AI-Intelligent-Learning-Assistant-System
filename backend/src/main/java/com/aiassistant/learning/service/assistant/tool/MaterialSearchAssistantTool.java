package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantMaterialCandidate;
import com.aiassistant.learning.service.assistant.AssistantMaterialSearchResult;
import com.aiassistant.learning.service.assistant.AssistantTaskIntentParser;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 资料搜索助手工具。
 */
@Component
public class MaterialSearchAssistantTool extends AbstractAssistantTool {

    /** 默认返回候选资料数量。 */
    private static final int DEFAULT_LIMIT = 5;

    /** 学习资料服务。 */
    private final StudyMaterialService studyMaterialService;
    /** 规则意图解析器。 */
    private final AssistantTaskIntentParser taskIntentParser;

    public MaterialSearchAssistantTool(
            StudyMaterialService studyMaterialService,
            AssistantTaskIntentParser taskIntentParser,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.studyMaterialService = studyMaterialService;
        this.taskIntentParser = taskIntentParser;
    }

    /**
     * 工具名称。
     */
    @Override
    public String name() {
        return "material.search";
    }

    /**
     * 用户消息中能提取出资料关键词时，支持执行搜索。
     */
    @Override
    public boolean supports(ToolContext context) {
        return StringUtils.hasText(taskIntentParser.extractMaterialQueryText(
                context.userMessage(),
                context.structuredIntent()
        ));
    }

    /**
     * 执行资料搜索。
     */
    @Override
    public ToolExecutionResult execute(ToolContext context) {
        String queryText = taskIntentParser.extractMaterialQueryText(
                context.userMessage(),
                context.structuredIntent()
        );
        return search(context.userId(), queryText);
    }

    /**
     * 按关键词搜索资料，并判断是否能自动选中唯一资料。
     */
    public ToolExecutionResult search(Long userId, String queryText) {
        LocalDateTime startedAt = LocalDateTime.now();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("queryText", queryText);
        args.put("limit", DEFAULT_LIMIT);
        try {
            List<AssistantMaterialCandidate> candidates = studyMaterialService.searchAssistantMaterials(
                            userId,
                            queryText,
                            DEFAULT_LIMIT
                    ).stream()
                    .map(item -> toCandidate(item, queryText))
                    .sorted((left, right) -> Integer.compare(
                            right.getMatchScore() == null ? 0 : right.getMatchScore(),
                            left.getMatchScore() == null ? 0 : left.getMatchScore()
                    ))
                    .toList();

            AssistantMaterialSearchResult result = resolveSearchResult(queryText, candidates);
            if (!candidates.isEmpty() && result.getSelectedMaterialId() != null) {
                String summary = "已定位到资料《%s》，后续会按这份资料继续处理。".formatted(result.getSelectedMaterialTitle());
                return success(name(), args, result, summary, startedAt);
            }
            if (!candidates.isEmpty()) {
                return waiting(name(), args, result, buildClarificationSummary(candidates), startedAt);
            }
            String summary = StringUtils.hasText(queryText)
                    ? "我暂时没找到标题里和“%s”匹配的资料，你可以换个关键词，或者直接告诉我资料全名。".formatted(queryText)
                    : "我还没定位到你说的是哪份资料。你可以直接说资料标题，或者告诉我一个更明显的关键词。";
            return waiting(name(), args, result, summary, startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }

    /**
     * 将资料列表项转换为助手候选资料。
     */
    private AssistantMaterialCandidate toCandidate(MaterialPageVO item, String queryText) {
        return AssistantMaterialCandidate.builder()
                .id(item.getId())
                .title(item.getTitle())
                .materialType(item.getMaterialType())
                .parseStatus(item.getParseStatus())
                .difficultyLevel(item.getDifficultyLevel())
                .totalCharacters(item.getTotalCharacters())
                .tags(item.getTags())
                .matchScore(computeMatchScore(item, queryText))
                .build();
    }

    /**
     * 根据匹配分判断是否需要用户确认。
     */
    private AssistantMaterialSearchResult resolveSearchResult(String queryText, List<AssistantMaterialCandidate> candidates) {
        if (candidates.isEmpty()) {
            return AssistantMaterialSearchResult.builder()
                    .queryText(queryText)
                    .needsClarification(true)
                    .candidates(candidates)
                    .build();
        }
        AssistantMaterialCandidate bestCandidate = candidates.get(0);
        AssistantMaterialCandidate secondCandidate = candidates.size() > 1 ? candidates.get(1) : null;
        int bestScore = bestCandidate.getMatchScore() == null ? 0 : bestCandidate.getMatchScore();
        int secondScore = secondCandidate == null || secondCandidate.getMatchScore() == null ? 0 : secondCandidate.getMatchScore();
        boolean clearSelection = candidates.size() == 1
                || (bestScore >= 100 && secondScore < 100)
                || (secondCandidate != null && bestScore - secondScore >= 18);
        return AssistantMaterialSearchResult.builder()
                .queryText(queryText)
                .selectedMaterialId(clearSelection ? bestCandidate.getId() : null)
                .selectedMaterialTitle(clearSelection ? bestCandidate.getTitle() : null)
                .needsClarification(!clearSelection)
                .candidates(candidates)
                .build();
    }

    /**
     * 给资料标题和标签的匹配程度打分。
     */
    private int computeMatchScore(MaterialPageVO material, String queryText) {
        if (!StringUtils.hasText(queryText)) {
            return 0;
        }
        String normalizedQuery = queryText.trim().toLowerCase();
        String normalizedTitle = StringUtils.hasText(material.getTitle()) ? material.getTitle().trim().toLowerCase() : "";
        String normalizedTags = StringUtils.hasText(material.getTags()) ? material.getTags().trim().toLowerCase() : "";
        String compactQuery = compactSearchText(queryText);
        String compactTitle = compactSearchText(material.getTitle());
        String compactTags = compactSearchText(material.getTags());
        if (normalizedTitle.equals(normalizedQuery)) {
            return 100;
        }
        if (StringUtils.hasText(compactQuery) && compactTitle.equals(compactQuery)) {
            return 98;
        }
        if (normalizedTitle.startsWith(normalizedQuery)) {
            return 96;
        }
        if (normalizedTitle.contains(normalizedQuery)) {
            return 84;
        }
        if (StringUtils.hasText(compactQuery) && compactTitle.contains(compactQuery)) {
            return 82;
        }
        if (StringUtils.hasText(compactQuery) && compactQuery.contains(compactTitle)) {
            return 80;
        }
        if (normalizedTags.contains(normalizedQuery)) {
            return 72;
        }
        if (StringUtils.hasText(compactQuery) && compactTags.contains(compactQuery)) {
            return 70;
        }
        return 50;
    }

    /**
     * 规范化资料标题搜索文本，忽略空格和中英文标点。
     */
    private String compactSearchText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        text.trim().toLowerCase().codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    /**
     * 构造候选资料澄清提示。
     */
    private String buildClarificationSummary(List<AssistantMaterialCandidate> candidates) {
        StringBuilder builder = new StringBuilder("我找到了几份可能相关的资料，你告诉我是下面哪一份：");
        for (int index = 0; index < candidates.size(); index++) {
            AssistantMaterialCandidate candidate = candidates.get(index);
            builder.append(System.lineSeparator())
                    .append(index + 1)
                    .append(". 《")
                    .append(candidate.getTitle())
                    .append("》")
                    .append(" · #")
                    .append(candidate.getId());
        }
        builder.append(System.lineSeparator()).append("你可以直接回复序号、资料ID（例如 #")
                .append(candidates.get(0).getId())
                .append("），或者把资料标题再说完整一点也可以。");
        return builder.toString();
    }
}
