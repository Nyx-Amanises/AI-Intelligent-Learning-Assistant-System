package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantTaskIntentParser;
import com.aiassistant.learning.vo.ai.AiTaskPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TaskListAssistantTool extends AbstractAssistantTool {

    private static final int DEFAULT_LIMIT = 6;
    private static final int LATEST_LIMIT = 3;

    private final AiTaskService aiTaskService;
    private final AssistantTaskIntentParser taskIntentParser;

    public TaskListAssistantTool(
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
        return "task.list";
    }

    @Override
    public boolean supports(ToolContext context) {
        return taskIntentParser.looksLikeTaskListRequest(context.userMessage(), context.structuredIntent());
    }

    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        AssistantTaskIntentParser.TaskBrowseOptions options =
                taskIntentParser.parseTaskBrowseRequest(context.userMessage(), context.structuredIntent());
        int limit = options.latestOnly() ? LATEST_LIMIT : DEFAULT_LIMIT;
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("taskTypeFilter", options.taskTypeFilter());
        args.put("taskStatusFilter", options.taskStatusFilter());
        args.put("latestOnly", options.latestOnly());
        args.put("limit", limit);
        try {
            PageVO<AiTaskPageVO> page = aiTaskService.pageTasks(
                    context.userId(),
                    1L,
                    (long) limit,
                    options.taskTypeFilter(),
                    options.taskStatusFilter()
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskTypeFilter", options.taskTypeFilter());
            result.put("taskStatusFilter", options.taskStatusFilter());
            result.put("latestOnly", options.latestOnly());
            result.put("total", page.getTotal());
            result.put("records", page.getRecords());
            return success(name(), args, result, buildSummary(options, page), startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }

    private String buildSummary(AssistantTaskIntentParser.TaskBrowseOptions options, PageVO<AiTaskPageVO> page) {
        List<AiTaskPageVO> records = page == null ? List.of() : page.getRecords();
        long total = page == null || page.getTotal() == null ? 0L : page.getTotal();
        if (records == null || records.isEmpty()) {
            String taskTypeLabel = StringUtils.hasText(options.taskTypeFilter())
                    ? formatTaskTypeLabel(options.taskTypeFilter())
                    : "任务";
            if (StringUtils.hasText(options.taskStatusFilter())) {
                return "当前还没有%s里状态为%s的记录。".formatted(
                        taskTypeLabel,
                        formatTaskStatusLabel(options.taskStatusFilter())
                );
            }
            return "当前还没有%s记录。".formatted(taskTypeLabel);
        }

        StringBuilder builder = new StringBuilder();
        if (options.latestOnly()) {
            builder.append("我先帮你看最近的");
        } else {
            builder.append("我先帮你列出");
        }
        if (StringUtils.hasText(options.taskTypeFilter())) {
            builder.append(formatTaskTypeLabel(options.taskTypeFilter()));
        } else {
            builder.append("任务");
        }
        if (StringUtils.hasText(options.taskStatusFilter())) {
            builder.append("里状态为").append(formatTaskStatusLabel(options.taskStatusFilter())).append("的部分");
        }
        builder.append("（共 ").append(total).append(" 条，这里先显示 ").append(records.size()).append(" 条）：");

        for (int index = 0; index < records.size(); index++) {
            AiTaskPageVO item = records.get(index);
            builder.append(System.lineSeparator())
                    .append(index + 1)
                    .append(". 任务 #")
                    .append(item.getId())
                    .append(" · ")
                    .append(formatTaskTypeLabel(item.getTaskType()))
                    .append(" · ")
                    .append(formatTaskStatusLabel(item.getStatus()))
                    .append(" · ")
                    .append(item.getProgressRate() == null ? 0 : item.getProgressRate())
                    .append("%");
        }
        if (total > records.size()) {
            builder.append(System.lineSeparator())
                    .append("如果你愿意，我可以继续按任务类型或状态帮你缩小范围。");
        }
        return builder.toString();
    }

    private String formatTaskTypeLabel(String taskType) {
        if (!StringUtils.hasText(taskType)) {
            return "任务";
        }
        return switch (taskType.trim().toUpperCase()) {
            case "SUMMARY" -> "AI 总结";
            case "QUESTION_GENERATE" -> "AI 出题";
            case "PRACTICE_REVIEW" -> "简答题评分";
            case "EMBEDDING" -> "Embedding";
            default -> taskType.trim();
        };
    }

    private String formatTaskStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "未知状态";
        }
        return switch (status.trim().toUpperCase()) {
            case "PENDING" -> "等待中";
            case "RUNNING" -> "执行中";
            case "SUCCESS" -> "已完成";
            case "FAILED" -> "失败";
            case "CANCELLED" -> "已取消";
            default -> status.trim();
        };
    }
}
