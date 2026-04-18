package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantTaskIntentParser;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.question.QuestionSetPageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class QuestionSetListAssistantTool extends AbstractAssistantTool {

    private static final int DEFAULT_LIMIT = 6;

    private final QuestionSetService questionSetService;
    private final StudyMaterialService studyMaterialService;
    private final AssistantTaskIntentParser taskIntentParser;

    public QuestionSetListAssistantTool(
            QuestionSetService questionSetService,
            StudyMaterialService studyMaterialService,
            AssistantTaskIntentParser taskIntentParser,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.questionSetService = questionSetService;
        this.studyMaterialService = studyMaterialService;
        this.taskIntentParser = taskIntentParser;
    }

    @Override
    public String name() {
        return "question_set.list";
    }

    @Override
    public boolean supports(ToolContext context) {
        return taskIntentParser.looksLikeQuestionSetListRequest(context.userMessage(), context.structuredIntent());
    }

    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        AssistantTaskIntentParser.QuestionSetBrowseOptions options =
                taskIntentParser.parseQuestionSetBrowseRequest(context.userMessage(), context.structuredIntent());
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keyword", options.keyword());
        args.put("status", options.status());
        args.put("difficultyLevel", options.difficultyLevel());
        args.put("currentMaterialOnly", options.currentMaterialOnly());
        args.put("limit", DEFAULT_LIMIT);

        MaterialBinding materialBinding = resolveMaterialBinding(context.userId(), context.session(), options.currentMaterialOnly());
        if (options.currentMaterialOnly() && materialBinding.materialId() == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reason", "material_context_missing");
            return waiting(
                    name(),
                    args,
                    result,
                    "你这次像是在查某份资料对应的题集，但我当前还没绑定资料。你可以先告诉我资料标题，或者从某份资料页进入后再查。",
                    startedAt
            );
        }

        if (materialBinding.materialId() != null) {
            args.put("materialId", materialBinding.materialId());
        }
        try {
            PageVO<QuestionSetPageVO> page = questionSetService.browseAssistantQuestionSets(
                    context.userId(),
                    options.keyword(),
                    options.status(),
                    options.difficultyLevel(),
                    materialBinding.materialId(),
                    DEFAULT_LIMIT
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("keyword", options.keyword());
            result.put("status", options.status());
            result.put("difficultyLevel", options.difficultyLevel());
            result.put("currentMaterialOnly", options.currentMaterialOnly());
            result.put("materialId", materialBinding.materialId());
            result.put("materialTitle", materialBinding.materialTitle());
            result.put("autoResolvedFromRecent", materialBinding.autoResolvedFromRecent());
            result.put("total", page.getTotal());
            result.put("records", page.getRecords());
            return success(name(), args, result, buildSummary(options, materialBinding, page), startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }

    private MaterialBinding resolveMaterialBinding(Long userId, AssistantSession session, boolean currentMaterialOnly) {
        if (!currentMaterialOnly) {
            return new MaterialBinding(null, null, false);
        }
        Long materialId = AssistantToolSupport.resolveMaterialId(session);
        if (materialId != null) {
            return new MaterialBinding(materialId, resolveMaterialTitle(userId, materialId), false);
        }

        List<MaterialPageVO> recentMaterials = studyMaterialService.searchAssistantMaterials(userId, null, 1);
        if (recentMaterials.isEmpty()) {
            return new MaterialBinding(null, null, false);
        }

        MaterialPageVO material = recentMaterials.get(0);
        bindMaterialContext(session, material.getId());
        return new MaterialBinding(material.getId(), material.getTitle(), true);
    }

    private String resolveMaterialTitle(Long userId, Long materialId) {
        if (materialId == null) {
            return null;
        }
        try {
            MaterialDetailVO detail = studyMaterialService.getMaterialDetail(userId, materialId);
            return detail.getTitle();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void bindMaterialContext(AssistantSession session, Long materialId) {
        if (session == null || materialId == null) {
            return;
        }
        session.setCurrentMaterialId(materialId);
        session.setCurrentContextType("MATERIAL");
        session.setCurrentContextId(materialId);
    }

    private String buildSummary(
            AssistantTaskIntentParser.QuestionSetBrowseOptions options,
            MaterialBinding materialBinding,
            PageVO<QuestionSetPageVO> page
    ) {
        List<QuestionSetPageVO> records = page == null ? List.of() : page.getRecords();
        long total = page == null || page.getTotal() == null ? 0L : page.getTotal();

        if (records == null || records.isEmpty()) {
            if (materialBinding.materialId() != null) {
                return StringUtils.hasText(materialBinding.materialTitle())
                        ? "《%s》当前还没有符合条件的题集。".formatted(materialBinding.materialTitle())
                        : "当前资料下还没有符合条件的题集。";
            }
            return "当前还没有符合条件的题集。";
        }

        StringBuilder builder = new StringBuilder();
        if (materialBinding.autoResolvedFromRecent() && StringUtils.hasText(materialBinding.materialTitle())) {
            builder.append("当前没有固定资料，我先按你最近的资料《")
                    .append(materialBinding.materialTitle())
                    .append("》来查题集。")
                    .append(System.lineSeparator());
        }
        if (materialBinding.materialId() != null && StringUtils.hasText(materialBinding.materialTitle())) {
            builder.append("我先帮你列出《").append(materialBinding.materialTitle()).append("》关联的题集");
        } else {
            builder.append("我先帮你列出题集");
        }
        if (StringUtils.hasText(options.status())) {
            builder.append("（状态：").append(formatStatus(options.status())).append("）");
        }
        builder.append("，共 ").append(total).append(" 条，这里先显示 ").append(records.size()).append(" 条：");

        for (int index = 0; index < records.size(); index++) {
            QuestionSetPageVO item = records.get(index);
            builder.append(System.lineSeparator())
                    .append(index + 1)
                    .append(". 《")
                    .append(item.getTitle())
                    .append("》 · #")
                    .append(item.getId())
                    .append(" · ")
                    .append(item.getQuestionCount())
                    .append(" 题 · 难度 ")
                    .append(item.getDifficultyLevel() == null ? 3 : item.getDifficultyLevel())
                    .append(" · ")
                    .append(formatStatus(item.getStatus()));
        }
        if (total > records.size()) {
            builder.append(System.lineSeparator())
                    .append("如果你愿意，我可以继续按难度、状态或资料范围帮你缩小结果。");
        }
        return builder.toString();
    }

    private String formatStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "未知状态";
        }
        return switch (status.trim().toUpperCase()) {
            case "PUBLISHED" -> "已发布";
            case "DRAFT" -> "草稿";
            case "ARCHIVED" -> "已归档";
            default -> status.trim();
        };
    }

    private record MaterialBinding(Long materialId, String materialTitle, boolean autoResolvedFromRecent) {
    }
}
