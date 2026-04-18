package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MaterialDetailAssistantTool extends AbstractAssistantTool {

    private final StudyMaterialService studyMaterialService;

    public MaterialDetailAssistantTool(StudyMaterialService studyMaterialService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.studyMaterialService = studyMaterialService;
    }

    @Override
    public String name() {
        return "material.detail";
    }

    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveMaterialId(context.session()) != null;
    }

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
                    """.formatted(
                    detail.getTitle(),
                    detail.getMaterialType(),
                    detail.getParseStatus(),
                    detail.getTotalPages() == null ? "--" : detail.getTotalPages(),
                    detail.getTotalCharacters() == null ? "--" : detail.getTotalCharacters()
            ).trim();
            return success(name(), args, detail, summary, startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }
}
