package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 资料详情助手工具。
 */
@Component
public class MaterialDetailAssistantTool extends AbstractAssistantTool {

    /** 学习资料服务。 */
    private final StudyMaterialService studyMaterialService;

    public MaterialDetailAssistantTool(StudyMaterialService studyMaterialService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.studyMaterialService = studyMaterialService;
    }

    /**
     * 工具名称。
     */
    @Override
    public String name() {
        return "material.detail";
    }

    /**
     * 当前会话绑定了资料时支持查看详情。
     */
    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveMaterialId(context.session()) != null;
    }

    /**
     * 查询当前资料详情。
     */
    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long materialId = AssistantToolSupport.resolveMaterialId(context.session());
        Map<String, Object> args = Map.of("materialId", materialId);
        try {
            MaterialDetailVO detail = studyMaterialService.getMaterialDetail(context.userId(), materialId);
            String summary = """
                    当前资料：%s
                    类型：%s，解析状态：%s，总页数：%s，总字符数：%s。
                    Embedding：%s。
                    """.formatted(
                    detail.getTitle(),
                    detail.getMaterialType(),
                    detail.getParseStatus(),
                    detail.getTotalPages() == null ? "--" : detail.getTotalPages(),
                    detail.getTotalCharacters() == null ? "--" : detail.getTotalCharacters(),
                    formatEmbeddingSummary(detail)
            ).trim();
            return success(name(), args, detail, summary, startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }

    /**
     * 格式化资料 Embedding 状态。
     */
    private String formatEmbeddingSummary(MaterialDetailVO detail) {
        if (detail == null) {
            return "未知";
        }
        String statusText = switch ((detail.getEmbeddingStatus() == null ? "" : detail.getEmbeddingStatus().trim().toUpperCase())) {
            case "SUCCESS" -> "已完成";
            case "PARTIAL" -> "部分完成";
            case "PARTIAL_FAILED" -> "部分失败";
            case "RUNNING" -> "生成中";
            case "FAILED" -> "失败";
            case "PENDING" -> "未生成";
            case "PARSING" -> "资料解析中";
            case "PARSE_FAILED" -> "资料解析失败";
            case "NOT_READY" -> "资料未就绪";
            default -> "待处理";
        };
        int embedded = detail.getEmbeddedSegmentCount() == null ? 0 : detail.getEmbeddedSegmentCount();
        int total = detail.getTotalSegmentCount() == null ? 0 : detail.getTotalSegmentCount();
        return total > 0 ? statusText + "（" + embedded + "/" + total + "）" : statusText;
    }
}
