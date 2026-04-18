package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskStatusAssistantTool extends AbstractAssistantTool {

    private final AiTaskService aiTaskService;

    public TaskStatusAssistantTool(AiTaskService aiTaskService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.aiTaskService = aiTaskService;
    }

    @Override
    public String name() {
        return "task.get_status";
    }

    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveTaskId(context.session(), context.userMessage()) != null;
    }

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
