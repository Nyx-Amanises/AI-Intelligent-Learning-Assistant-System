package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantMaterialCandidate;
import com.aiassistant.learning.service.assistant.AssistantPendingActionPayload;
import com.aiassistant.learning.service.assistant.AssistantTaskIntentParser;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.aiassistant.learning.vo.material.MaterialSegmentVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 资料章节线索助手工具。
 *
 * <p>资料没有显式目录时，会从资料分段标题和内容里提取可展示的章节线索。</p>
 */
@Component
public class MaterialChapterOutlineAssistantTool extends AbstractAssistantTool {

    /** 最多展示的章节线索数量。 */
    private static final int MAX_ENTRY_COUNT = 12;

    /** 学习资料服务。 */
    private final StudyMaterialService studyMaterialService;
    /** 规则意图解析器。 */
    private final AssistantTaskIntentParser taskIntentParser;

    public MaterialChapterOutlineAssistantTool(
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
        return "material.chapter_outline";
    }

    /**
     * 用户想看章节、目录或大纲时支持该工具。
     */
    @Override
    public boolean supports(ToolContext context) {
        return taskIntentParser.looksLikeChapterBrowseRequest(context.userMessage(), context.structuredIntent());
    }

    /**
     * 查询资料章节线索。
     */
    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        AssistantTaskIntentParser.ChapterBrowseOptions options =
                taskIntentParser.parseChapterBrowseRequest(context.userMessage(), context.structuredIntent());
        String materialQuery = taskIntentParser.extractMaterialQueryText(
                context.userMessage(),
                context.structuredIntent()
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keyword", options.keyword());
        args.put("outlineOnly", options.outlineOnly());
        args.put("materialQuery", materialQuery);

        MaterialResolution materialResolution = resolveMaterial(context.userId(), context.session(), materialQuery);
        if (materialResolution.waitingSummary() != null) {
            savePendingSelectionIfNeeded(context.session(), materialQuery, options.keyword(), materialResolution);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("materialQuery", materialQuery);
            result.put("candidates", materialResolution.candidates());
            return waiting(name(), args, result, materialResolution.waitingSummary(), startedAt);
        }
        if (materialResolution.materialId() == null) {
            return waiting(
                    name(),
                    args,
                    Map.of("reason", "material_context_missing"),
                    "要看章节目录，我还需要先知道是哪份资料。你可以直接告诉我资料标题，或者从某份资料页进入后再问。",
                    startedAt
            );
        }

        try {
            MaterialDetailVO detail = studyMaterialService.getMaterialDetail(context.userId(), materialResolution.materialId());
            bindMaterialContext(context.session(), detail.getId());

            List<OutlineEntry> entries = buildOutlineEntries(detail.getSegments(), options.keyword());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("materialId", detail.getId());
            result.put("materialTitle", detail.getTitle());
            result.put("materialQuery", materialQuery);
            result.put("chapterKeyword", options.keyword());
            result.put("autoResolvedFromRecent", materialResolution.autoResolvedFromRecent());
            result.put("entryCount", entries.size());
            result.put("entries", entries);
            return success(name(), args, result, buildSummary(detail, options.keyword(), entries, materialResolution), startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }

    /**
     * 解析要查看章节的资料。
     */
    private MaterialResolution resolveMaterial(Long userId, AssistantSession session, String materialQuery) {
        Long sessionMaterialId = AssistantToolSupport.resolveMaterialId(session);
        if (sessionMaterialId != null && !StringUtils.hasText(materialQuery)) {
            return new MaterialResolution(sessionMaterialId, null, false, List.of());
        }

        if (StringUtils.hasText(materialQuery)) {
            List<MaterialPageVO> candidates = studyMaterialService.searchAssistantMaterials(userId, materialQuery, 5).stream()
                    .sorted((left, right) -> Integer.compare(
                            computeMatchScore(right, materialQuery),
                            computeMatchScore(left, materialQuery)
                    ))
                    .toList();
            if (candidates.isEmpty()) {
                return new MaterialResolution(
                        null,
                        "我暂时没找到标题里和“%s”匹配的资料，所以还没法展开章节目录。你可以换个关键词，或者把资料全名再说完整一点。".formatted(materialQuery),
                        false,
                        List.of()
                );
            }
            if (candidates.size() == 1 || isClearSelection(candidates, materialQuery)) {
                MaterialPageVO material = candidates.get(0);
                bindMaterialContext(session, material.getId());
                return new MaterialResolution(material.getId(), null, false, candidates);
            }
            return new MaterialResolution(
                    null,
                    buildMaterialClarificationSummary(candidates),
                    false,
                    candidates
            );
        }

        List<MaterialPageVO> recentMaterials = studyMaterialService.searchAssistantMaterials(userId, null, 1);
        if (recentMaterials.isEmpty()) {
            return new MaterialResolution(null, null, false, List.of());
        }
        MaterialPageVO material = recentMaterials.get(0);
        bindMaterialContext(session, material.getId());
        return new MaterialResolution(material.getId(), null, true, List.of(material));
    }

    /**
     * 判断候选资料第一项是否足够明确。
     */
    private boolean isClearSelection(List<MaterialPageVO> candidates, String materialQuery) {
        if (candidates == null || candidates.size() <= 1 || !StringUtils.hasText(materialQuery)) {
            return false;
        }
        int bestScore = computeMatchScore(candidates.get(0), materialQuery);
        int secondScore = computeMatchScore(candidates.get(1), materialQuery);
        return (bestScore >= 100 && secondScore < 100) || bestScore - secondScore >= 18;
    }

    /**
     * 构造资料歧义时的澄清提示。
     */
    private String buildMaterialClarificationSummary(List<MaterialPageVO> candidates) {
        StringBuilder builder = new StringBuilder("我找到了几份可能相关的资料，我先把候选项列给你：");
        for (int index = 0; index < candidates.size(); index++) {
            MaterialPageVO item = candidates.get(index);
            builder.append(System.lineSeparator())
                    .append(index + 1)
                    .append(". 《")
                    .append(item.getTitle())
                    .append("》 · #")
                    .append(item.getId());
        }
        builder.append(System.lineSeparator()).append("你可以直接回复序号、资料ID，或者把资料标题说完整一点也可以，我会继续把目录展开。");
        return builder.toString();
    }

    /**
     * 从资料分段中构造章节线索。
     */
    private List<OutlineEntry> buildOutlineEntries(List<MaterialSegmentVO> segments, String keyword) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        String normalizedKeyword = normalize(keyword);
        Set<String> dedupe = new LinkedHashSet<>();
        List<OutlineEntry> entries = new ArrayList<>();
        for (MaterialSegmentVO segment : segments) {
            if (!matchesKeyword(segment, normalizedKeyword)) {
                continue;
            }
            String locationText = buildLocationText(segment);
            String heading = resolveHeading(segment);
            String dedupeKey = normalize(locationText + "|" + heading);
            if (!dedupe.add(dedupeKey)) {
                continue;
            }
            entries.add(new OutlineEntry(
                    segment.getPageNo(),
                    segment.getSegmentNo(),
                    locationText,
                    heading,
                    AssistantToolSupport.abbreviate(segment.getContentText(), 90)
            ));
            if (entries.size() >= MAX_ENTRY_COUNT) {
                break;
            }
        }
        return entries;
    }

    /**
     * 判断分段是否匹配章节关键词。
     */
    private boolean matchesKeyword(MaterialSegmentVO segment, String normalizedKeyword) {
        if (!StringUtils.hasText(normalizedKeyword)) {
            return true;
        }
        return normalize(segment.getSectionTitle()).contains(normalizedKeyword)
                || normalize(segment.getContentText()).contains(normalizedKeyword);
    }

    /**
     * 构造分段位置文本。
     */
    private String buildLocationText(MaterialSegmentVO segment) {
        if (segment == null) {
            return "位置未知";
        }
        if (segment.getPageNo() != null && segment.getSegmentNo() != null) {
            return "第 " + segment.getPageNo() + " 页 · 第 " + segment.getSegmentNo() + " 段";
        }
        if (segment.getPageNo() != null) {
            return "第 " + segment.getPageNo() + " 页";
        }
        if (segment.getSegmentNo() != null) {
            return "第 " + segment.getSegmentNo() + " 段";
        }
        return "位置未知";
    }

    /**
     * 解析章节标题；没有标题时使用正文预览。
     */
    private String resolveHeading(MaterialSegmentVO segment) {
        if (segment == null) {
            return "未命名片段";
        }
        if (StringUtils.hasText(segment.getSectionTitle())) {
            String[] parts = segment.getSectionTitle().split("·");
            String candidate = parts.length == 0 ? segment.getSectionTitle() : parts[parts.length - 1];
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return AssistantToolSupport.abbreviate(segment.getContentText(), 28);
    }

    /**
     * 构造章节线索摘要。
     */
    private String buildSummary(
            MaterialDetailVO detail,
            String chapterKeyword,
            List<OutlineEntry> entries,
            MaterialResolution materialResolution
    ) {
        StringBuilder builder = new StringBuilder();
        if (materialResolution.autoResolvedFromRecent()) {
            builder.append("当前没有固定资料，我先按你最近的资料《")
                    .append(detail.getTitle())
                    .append("》来展开目录。")
                    .append(System.lineSeparator());
        }

        if (entries.isEmpty()) {
            if (StringUtils.hasText(chapterKeyword)) {
                builder.append("我在《")
                        .append(detail.getTitle())
                        .append("》里暂时没找到和“")
                        .append(chapterKeyword)
                        .append("”直接匹配的章节或片段。");
            } else {
                builder.append("《").append(detail.getTitle()).append("》当前还没有可展示的章节线索。");
            }
            return builder.toString();
        }

        if (StringUtils.hasText(chapterKeyword)) {
            builder.append("我在《")
                    .append(detail.getTitle())
                    .append("》里找到了和“")
                    .append(chapterKeyword)
                    .append("”相关的章节线索：");
        } else {
            builder.append("我先把《")
                    .append(detail.getTitle())
                    .append("》的章节线索整理给你：");
        }

        for (int index = 0; index < entries.size(); index++) {
            OutlineEntry entry = entries.get(index);
            builder.append(System.lineSeparator())
                    .append(index + 1)
                    .append(". ")
                    .append(entry.locationText())
                    .append(" · ")
                    .append(entry.heading());
        }
        if (entries.size() >= MAX_ENTRY_COUNT) {
            builder.append(System.lineSeparator())
                    .append("如果你愿意，我可以继续围绕其中某一章、某一页或某个关键词往下展开。");
        }
        return builder.toString();
    }

    /**
     * 绑定资料上下文到会话。
     */
    private void bindMaterialContext(AssistantSession session, Long materialId) {
        if (session == null || materialId == null) {
            return;
        }
        session.setCurrentMaterialId(materialId);
        session.setCurrentContextType("MATERIAL");
        session.setCurrentContextId(materialId);
    }

    /**
     * 资料候选不明确时，把待选择动作存入会话。
     */
    private void savePendingSelectionIfNeeded(
            AssistantSession session,
            String materialQuery,
            String chapterKeyword,
            MaterialResolution materialResolution
    ) {
        if (session == null
                || materialResolution == null
                || materialResolution.candidates() == null
                || materialResolution.candidates().isEmpty()) {
            return;
        }
        AssistantPendingActionPayload payload = AssistantPendingActionPayload.builder()
                .promptText(materialResolution.waitingSummary())
                .materialQuery(materialQuery)
                .followUpActionType("CHAPTER_BROWSE")
                .chapterKeyword(chapterKeyword)
                .materialCandidates(materialResolution.candidates().stream().map(this::toCandidate).toList())
                .build();
        session.setPendingActionType("MATERIAL_SELECTION");
        session.setPendingActionPayloadJson(toJson(payload));
    }

    /**
     * 资料列表项转候选资料。
     */
    private AssistantMaterialCandidate toCandidate(MaterialPageVO material) {
        return AssistantMaterialCandidate.builder()
                .id(material.getId())
                .title(material.getTitle())
                .materialType(material.getMaterialType())
                .parseStatus(material.getParseStatus())
                .difficultyLevel(material.getDifficultyLevel())
                .totalCharacters(material.getTotalCharacters())
                .tags(material.getTags())
                .build();
    }

    /**
     * 归一化文本。
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("\\s+", " ").trim().toLowerCase() : "";
    }

    /**
     * 计算资料和查询词的匹配分数。
     */
    private int computeMatchScore(MaterialPageVO material, String queryText) {
        if (material == null || !StringUtils.hasText(queryText)) {
            return 0;
        }
        String normalizedQuery = normalize(queryText);
        String normalizedTitle = normalize(material.getTitle());
        String normalizedTags = normalize(material.getTags());
        if (normalizedTitle.equals(normalizedQuery)) {
            return 100;
        }
        if (normalizedTitle.startsWith(normalizedQuery)) {
            return 96;
        }
        if (normalizedTitle.contains(normalizedQuery)) {
            return 84;
        }
        if (normalizedTags.contains(normalizedQuery)) {
            return 72;
        }
        return 50;
    }

    /**
     * 资料解析结果。
     */
    private record MaterialResolution(
            Long materialId,
            String waitingSummary,
            boolean autoResolvedFromRecent,
            List<MaterialPageVO> candidates
    ) {
    }

    /**
     * 章节线索条目。
     */
    private record OutlineEntry(
            Integer pageNo,
            Integer segmentNo,
            String locationText,
            String heading,
            String previewText
    ) {
    }
}
