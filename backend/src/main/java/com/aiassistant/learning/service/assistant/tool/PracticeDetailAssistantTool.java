package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.practice.PracticeAnswerVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 练习详情助手工具。
 */
@Component
public class PracticeDetailAssistantTool extends AbstractAssistantTool {

    /** 练习服务。 */
    private final PracticeService practiceService;

    public PracticeDetailAssistantTool(PracticeService practiceService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.practiceService = practiceService;
    }

    /**
     * 工具名称。
     */
    @Override
    public String name() {
        return "practice.detail";
    }

    /**
     * 当前会话绑定练习会话时支持该工具。
     */
    @Override
    public boolean supports(ToolContext context) {
        return true;
    }

    /**
     * 查询当前练习记录详情。
     */
    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long sessionId = AssistantToolSupport.resolvePracticeSessionId(context.session(), context.userMessage(), context.structuredIntent());
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("practiceSessionId", sessionId);
        if (sessionId == null) {
            return failure(name(), args, "请先查看练习记录列表，或指定练习记录 ID。", startedAt);
        }
        if (sessionId <= 0) {
            return failure(name(), args, "练习记录 ID 必须为正整数。", startedAt);
        }
        try {
            PracticeDetailVO detail = practiceService.getPracticeDetail(context.userId(), sessionId);
            // 只有拥有校验成功后才切换当前练习，让下一轮“这题为什么错”延续刚查到的记录。
            if (context.session() != null) {
                context.session().setCurrentContextType("PRACTICE_SESSION");
                context.session().setCurrentContextId(detail.getSessionId());
                context.session().setCurrentPracticeSessionId(detail.getSessionId());
                context.session().setCurrentQuestionSetId(detail.getQuestionSetId());
                context.session().setCurrentMaterialId(detail.getMaterialId());
            }
            return success(name(), args, detail, buildSummary(detail, context.userMessage()), startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }

    /** 给模型有限但可核对的作答证据；完整结构化详情仍供界面使用。 */
    static String buildSummary(PracticeDetailVO detail, String userMessage) {
        List<PracticeAnswerVO> answers = detail.getAnswers() == null ? List.of() : detail.getAnswers().stream()
                .filter(java.util.Objects::nonNull).toList();
        long pending = answers.stream().filter(PracticeDetailAssistantTool::isPending).count();
        boolean submitted = "SUBMITTED".equalsIgnoreCase(detail.getSessionStatus());
        StringBuilder text = new StringBuilder("练习记录 #").append(detail.getSessionId()).append("：")
                .append(bounded(detail.getSessionName(), 100)).append('\n');
        if (!submitted) {
            text.append("状态：尚未提交，没有最终得分或正确率；页面上的未提交答案可能尚未保存。\n");
        } else {
            text.append("状态：已提交").append(pending > 0 ? "，还有 " + pending + " 道题等待 AI 评分，当前分数和正确率为暂时结果。" : "，评分已完成。")
                    .append("\n得分：").append(detail.getObtainedScore()).append('/').append(detail.getTotalScore())
                    .append("，正确题数：").append(detail.getCorrectCount()).append('/').append(detail.getTotalQuestions())
                    .append("，正确率：").append(detail.getAccuracyRate()).append("%。\n");
        }
        text.append("以下是本次作答证据。只根据已列出的题目解释判分；待评分题不能认定为错题。\n");
        Set<Integer> selected = new LinkedHashSet<>();
        Matcher requested = Pattern.compile("第\\s*(\\d+)\\s*题").matcher(userMessage == null ? "" : userMessage);
        if (requested.find()) {
            try {
                int index = Integer.parseInt(requested.group(1)) - 1;
                if (index >= 0 && index < answers.size()) selected.add(index);
            } catch (NumberFormatException ignored) { }
        }
        for (int index = 0; index < answers.size() && selected.size() < 8; index++) {
            PracticeAnswerVO answer = answers.get(index);
            if (submitted && !isPending(answer) && Integer.valueOf(0).equals(answer.getIsCorrect())) selected.add(index);
        }
        for (int index = 0; index < answers.size() && selected.size() < 8; index++) selected.add(index);
        for (int index : selected) {
            PracticeAnswerVO answer = answers.get(index);
            String state = !submitted ? "未提交" : isPending(answer) ? "等待 AI 评分（非错题）"
                    : answer.getIsCorrect() == null ? "未评分" : answer.getIsCorrect() == 1 ? "正确" : "错误";
            text.append("\n第 ").append(index + 1).append(" 题（题目 ID ").append(answer.getQuestionId())
                    .append("，").append(answer.getQuestionType()).append("）：").append(bounded(answer.getStemText(), 240)).append('\n');
            appendOption(text, "A", answer.getOptionA());
            appendOption(text, "B", answer.getOptionB());
            appendOption(text, "C", answer.getOptionC());
            appendOption(text, "D", answer.getOptionD());
            text.append("用户答案：").append(bounded(answer.getUserAnswer(), 140)).append('\n')
                    .append("参考答案：").append(bounded(StringUtils.hasText(answer.getReferenceAnswer())
                            ? answer.getReferenceAnswer() : answer.getCorrectAnswer(), 140)).append('\n')
                    .append("判分状态：").append(state).append("；得分：")
                    .append(!submitted || isPending(answer) ? "待定" : answer.getObtainedScore()).append('\n')
                    .append("判分说明：").append(bounded(answer.getReviewComment(), 120)).append('\n')
                    .append("答案解析：").append(bounded(answer.getAnswerAnalysis(), 230)).append('\n');
        }
        if (answers.size() > selected.size()) text.append("\n其余 ").append(answers.size() - selected.size())
                .append(" 道题未展开，可指定第几题继续查看；不要推测未列出题目的答案。\n");
        return bounded(text.toString(), 8000);
    }

    private static boolean isPending(PracticeAnswerVO answer) {
        return "AI_PENDING".equalsIgnoreCase(answer.getReviewMode());
    }

    private static void appendOption(StringBuilder text, String label, String option) {
        if (StringUtils.hasText(option)) text.append(label).append("：").append(bounded(option, 70)).append('\n');
    }

    private static String bounded(String value, int limit) {
        if (!StringUtils.hasText(value)) return "未提供";
        String text = value.trim();
        if (text.length() <= limit) return text;
        String marker = "…[已截断]";
        int end = limit - marker.length();
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + marker;
    }
}
