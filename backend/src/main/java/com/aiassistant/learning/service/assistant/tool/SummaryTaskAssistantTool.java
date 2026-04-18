package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantTaskIntentParser;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SummaryTaskAssistantTool extends AbstractAssistantTool {

    private final AiTaskService aiTaskService;
    private final AssistantTaskIntentParser taskIntentParser;

    public SummaryTaskAssistantTool(
            AiTaskService aiTaskService,
            AssistantTaskIntentParser taskIntentParser,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.aiTaskService = aiTaskService;
        this.taskIntentParser = taskIntentParser;
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
        AssistantTaskIntentParser.SummaryTaskOptions options = taskIntentParser.parseSummaryRequest(
                context.userMessage(),
                context.modelName()
        );
        SummaryGenerateRequest request = new SummaryGenerateRequest();
        request.setModelName(options.modelName());
        request.setSummaryType(options.summaryType());
        request.setSaveAsNote(options.saveAsNote());
        request.setTemperature(options.temperature());
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("materialId", materialId);
        args.put("summaryType", request.getSummaryType());
        args.put("saveAsNote", request.getSaveAsNote());
        if (request.getModelName() != null) {
            args.put("modelName", request.getModelName());
        }
        try {
            AiTaskDetailVO detail = aiTaskService.submitSummaryTask(context.userId(), materialId, request);
            String summary = "已为当前资料创建 AI 总结任务（%s%s），任务号 #%s，当前状态 %s。".formatted(
                    formatSummaryType(request.getSummaryType()),
                    Boolean.TRUE.equals(request.getSaveAsNote()) ? "，保存到笔记" : "，仅生成不保存",
                    detail.getId(),
                    detail.getStatus()
            );
            return success(name(), args, detail, summary, startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }

    private String formatSummaryType(String summaryType) {
        if ("EXAM".equalsIgnoreCase(summaryType)) {
            return "考试重点";
        }
        if ("OUTLINE".equalsIgnoreCase(summaryType)) {
            return "结构提纲";
        }
        return "标准总结";
    }
}
