package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * AI 任务状态助手工具。
 */
@Component
public class TaskStatusAssistantTool extends AbstractAssistantTool {

    /** AI 任务服务。 */
    private final AiTaskService aiTaskService;

    public TaskStatusAssistantTool(AiTaskService aiTaskService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.aiTaskService = aiTaskService;
    }

    /**
     * 工具名称。
     */
    @Override
    public String name() {
        return "task.get_status";
    }

    /**
     * 能从上下文或用户消息解析出任务 ID 时支持该工具。
     */
    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveTaskId(context.session(), context.userMessage()) != null;
    }

    /**
     * 查询任务详情和当前进度。
     */
    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long taskId = AssistantToolSupport.resolveTaskId(context.session(), context.userMessage());
        Map<String, Object> args = Map.of("taskId", taskId);
        try {
            AiTaskDetailVO detail = aiTaskService.getTaskDetail(context.userId(), taskId);
            String summary = """
                    任务 #%s
                    类型：%s，状态：%s，进度：%s%%。
                    %s
                    """.formatted(
                    detail.getId(),
                    detail.getTaskType(),
                    detail.getStatus(),
                    detail.getProgressRate(),
                    detail.getErrorMessage() == null ? "" : "错误信息：" + detail.getErrorMessage()
            ).trim();
            return success(name(), args, detail, summary, startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }
}
