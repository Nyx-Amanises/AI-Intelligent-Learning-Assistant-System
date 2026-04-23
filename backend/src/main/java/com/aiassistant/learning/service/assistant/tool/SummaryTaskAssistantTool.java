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

/**
 * AI 总结任务助手工具。
 */
@Component
public class SummaryTaskAssistantTool extends AbstractAssistantTool {

    /** AI 任务服务。 */
    private final AiTaskService aiTaskService;
    /** 规则意图解析器。 */
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

    /**
     * 工具名称。
     */
    @Override
    public String name() {
        return "task.submit_summary";
    }

    /**
     * 当前会话绑定资料时才支持创建总结任务。
     */
    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveMaterialId(context.session()) != null;
    }

    /**
     * 从用户消息中解析总结参数并提交任务。
     */
    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long materialId = AssistantToolSupport.resolveMaterialId(context.session());
        AssistantTaskIntentParser.SummaryTaskOptions options = taskIntentParser.parseSummaryRequest(
                context.userMessage(),
                context.modelName(),
                context.structuredIntent()
        );
        SummaryGenerateRequest request = new SummaryGenerateRequest();
        request.setModelName(options.modelName());
        request.setSummaryType(options.summaryType());
        request.setSaveAsNote(options.saveAsNote());
        request.setTemperature(options.temperature());
        return executeRequest(context.userId(), materialId, request, startedAt);
    }

    /**
     * 直接提交总结任务，供编排器在已解析好参数时调用。
     */
    public ToolExecutionResult executeRequest(Long userId, Long materialId, SummaryGenerateRequest request) {
        return executeRequest(userId, materialId, request, LocalDateTime.now());
    }

    /**
     * 提交总结任务的内部实现。
     */
    private ToolExecutionResult executeRequest(
            Long userId,
            Long materialId,
            SummaryGenerateRequest request,
            LocalDateTime startedAt
    ) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("materialId", materialId);
        args.put("summaryType", request.getSummaryType());
        args.put("saveAsNote", request.getSaveAsNote());
        if (request.getModelName() != null) {
            args.put("modelName", request.getModelName());
        }
        try {
            AiTaskDetailVO detail = aiTaskService.submitSummaryTask(userId, materialId, request);
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

    /**
     * 将总结类型转换成中文说明。
     */
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
