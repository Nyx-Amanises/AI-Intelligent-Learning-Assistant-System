package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SummaryTaskAssistantTool extends AbstractAssistantTool {

    private final AiTaskService aiTaskService;

    public SummaryTaskAssistantTool(AiTaskService aiTaskService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.aiTaskService = aiTaskService;
    }

    @Override
    public String name() {
        return "task.submit_summary";
    }

    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveMaterialId(context.session()) != null;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long materialId = AssistantToolSupport.resolveMaterialId(context.session());
        SummaryGenerateRequest request = new SummaryGenerateRequest();
        request.setModelName(context.modelName());
        request.setSummaryType("STANDARD");
        request.setSaveAsNote(true);
        request.setTemperature(0.7);
        Map<String, Object> args = Map.of(
                "materialId", materialId,
                "summaryType", request.getSummaryType(),
                "saveAsNote", request.getSaveAsNote()
        );
        try {
            AiTaskDetailVO detail = aiTaskService.submitSummaryTask(context.userId(), materialId, request);
            String summary = "已为当前资料创建 AI 总结任务，任务号 #%s，当前状态 %s。".formatted(
                    detail.getId(),
                    detail.getStatus()
            );
            return success(name(), args, detail, summary, startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }
}
