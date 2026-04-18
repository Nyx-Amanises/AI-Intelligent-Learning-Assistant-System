package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantTaskIntentParser;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MaterialListAssistantTool extends AbstractAssistantTool {

    private static final int DEFAULT_LIMIT = 6;

    private final StudyMaterialService studyMaterialService;
    private final AssistantTaskIntentParser taskIntentParser;

    public MaterialListAssistantTool(
            StudyMaterialService studyMaterialService,
            AssistantTaskIntentParser taskIntentParser,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.studyMaterialService = studyMaterialService;
        this.taskIntentParser = taskIntentParser;
    }

    @Override
    public String name() {
        return "material.list";
    }

    @Override
    public boolean supports(ToolContext context) {
        return taskIntentParser.looksLikeMaterialBrowseRequest(context.userMessage(), context.structuredIntent());
    }

    @Override
    public ToolExecutionResult execute(ToolContext context) {
        AssistantTaskIntentParser.MaterialBrowseOptions options =
                taskIntentParser.parseMaterialBrowseRequest(context.userMessage(), context.structuredIntent());
        return browse(context.userId(), options.keyword(), options.embeddingReadyOnly());
    }

    public ToolExecutionResult browse(Long userId, String keyword) {
        return browse(userId, keyword, false);
    }

    public ToolExecutionResult browse(Long userId, String keyword, boolean embeddingReadyOnly) {
        LocalDateTime startedAt = LocalDateTime.now();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keyword", keyword);
        args.put("limit", DEFAULT_LIMIT);
        args.put("embeddingReadyOnly", embeddingReadyOnly);
        try {
            PageVO<MaterialPageVO> page = studyMaterialService.browseAssistantMaterials(
                    userId,
                    keyword,
                    DEFAULT_LIMIT,
                    embeddingReadyOnly
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("keyword", keyword);
            result.put("embeddingReadyOnly", embeddingReadyOnly);
            result.put("total", page.getTotal());
            result.put("size", page.getSize());
            result.put("records", page.getRecords());

            String summary = buildSummary(keyword, embeddingReadyOnly, page.getRecords(), page.getTotal());
            return success(name(), args, result, summary, startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }

    private String buildSummary(String keyword, boolean embeddingReadyOnly, List<MaterialPageVO> records, long total) {
        if (records == null || records.isEmpty()) {
            if (embeddingReadyOnly) {
                return StringUtils.hasText(keyword)
                        ? "我暂时没找到和“%s”相关、且已经生成 Embedding 的资料。".formatted(keyword)
                        : "当前还没有已经生成 Embedding 的资料。";
            }
            if (StringUtils.hasText(keyword)) {
                return "我暂时没找到和“%s”相关的资料。你可以换个关键词试试。".formatted(keyword);
            }
            return "你当前还没有资料，或者资料列表暂时为空。";
        }

        StringBuilder builder = new StringBuilder();
        if (embeddingReadyOnly && StringUtils.hasText(keyword)) {
            builder.append("我先帮你找到了和“").append(keyword).append("”相关、且已经生成 Embedding 的资料");
        } else if (embeddingReadyOnly) {
            builder.append("我先帮你列出当前已经生成 Embedding 的资料");
        } else if (StringUtils.hasText(keyword)) {
            builder.append("我先帮你找到了和“").append(keyword).append("”相关的资料");
        } else {
            builder.append("我先帮你列出当前资料");
        }
        builder.append("（共 ").append(total).append(" 条，这里先显示 ").append(records.size()).append(" 条）：");

        for (int index = 0; index < records.size(); index++) {
            MaterialPageVO item = records.get(index);
            builder.append(System.lineSeparator())
                    .append(index + 1)
                    .append(". 《")
                    .append(item.getTitle())
                    .append("》 · #")
                    .append(item.getId())
                    .append(" · ")
                    .append(item.getMaterialType())
                    .append(" · ")
                    .append(item.getParseStatus())
                    .append(" · ")
                    .append(formatEmbeddingSummary(item));
        }

        if (total > records.size()) {
            builder.append(System.lineSeparator())
                    .append("如果你愿意，我可以继续按关键词、类型、解析状态或 Embedding 状态帮你缩小范围。");
        }
        return builder.toString();
    }

    private String formatEmbeddingSummary(MaterialPageVO item) {
        if (item == null) {
            return "Embedding 未知";
        }
        String statusText = switch ((item.getEmbeddingStatus() == null ? "" : item.getEmbeddingStatus().trim().toUpperCase())) {
            case "SUCCESS" -> "Embedding 已完成";
            case "PARTIAL" -> "Embedding 部分完成";
            case "PARTIAL_FAILED" -> "Embedding 部分失败";
            case "RUNNING" -> "Embedding 生成中";
            case "FAILED" -> "Embedding 失败";
            case "PENDING" -> "Embedding 未生成";
            case "PARSING" -> "资料解析中";
            case "PARSE_FAILED" -> "资料解析失败";
            default -> "Embedding 待处理";
        };
        int embedded = item.getEmbeddedSegmentCount() == null ? 0 : item.getEmbeddedSegmentCount();
        int total = item.getTotalSegmentCount() == null ? 0 : item.getTotalSegmentCount();
        return total > 0 ? statusText + "（" + embedded + "/" + total + "）" : statusText;
    }
}
