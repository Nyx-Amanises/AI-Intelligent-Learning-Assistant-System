package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.practice.PracticeSessionPageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 查询当前用户已提交的练习，包含仍在等待评分的记录。 */
@Component
public class PracticeListAssistantTool extends AbstractAssistantTool {
    private final PracticeService practiceService;

    public PracticeListAssistantTool(PracticeService practiceService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.practiceService = practiceService;
    }

    @Override
    public String name() { return "practice.list"; }

    @Override
    public boolean supports(ToolContext context) { return true; }

    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime started = LocalDateTime.now();
        Integer requestedPage = context.structuredIntent() == null ? null : context.structuredIntent().getPracticePage();
        java.util.regex.Matcher pageMention = java.util.regex.Pattern.compile("第\\s*([+-]?\\d+)\\s*页")
                .matcher(context.userMessage() == null ? "" : context.userMessage());
        if (pageMention.find()) {
            Long value = com.aiassistant.learning.service.assistant.AssistantToolSupport.readPracticeId(pageMention.group(1));
            requestedPage = value != null && value <= 100000 ? value.intValue() : 0;
        }
        long current = requestedPage == null ? 1 : requestedPage;
        Map<String, Object> args = Map.of("current", current, "size", 10);
        if (current < 1 || current > 100000) return failure(name(), args, "练习记录页码必须在 1 至 100000 之间。", started);
        try {
            PageVO<PracticeSessionPageVO> page = practiceService.pagePracticeSessions(context.userId(), current, 10L);
            List<PracticeSessionPageVO> records = page.getRecords() == null ? List.of() : page.getRecords();
            StringBuilder summary = new StringBuilder("已提交练习记录，第 ").append(page.getCurrent()).append(" 页，共 ")
                    .append(page.getTotal()).append(" 条。列表包含等待 AI 评分的练习，未评分完成的得分可能更新。\n");
            if (records.isEmpty()) summary.append("本页没有练习记录。尚未提交的练习不会出现在历史列表中。\n");
            for (PracticeSessionPageVO item : records) {
                summary.append("- 练习 #").append(item.getId()).append("：")
                        .append(com.aiassistant.learning.service.assistant.AssistantToolSupport.abbreviate(item.getSessionName(), 100))
                        .append("；题数 ").append(item.getTotalQuestions()).append("；当前得分 ").append(item.getObtainedScore())
                        .append("；当前正确率 ").append(item.getAccuracyRate()).append("%；提交时间 ").append(item.getSubmitTime()).append('\n');
            }
            summary.append("可指定练习记录 ID 查看答案、评分状态和错题原因。");
            return success(name(), args, page, summary.toString(), started);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), started);
        }
    }
}
