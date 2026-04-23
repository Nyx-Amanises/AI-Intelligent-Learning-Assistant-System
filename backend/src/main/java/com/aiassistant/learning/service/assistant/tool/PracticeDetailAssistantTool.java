package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.practice.PracticeAnswerVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

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
        return AssistantToolSupport.resolvePracticeSessionId(context.session()) != null;
    }

    /**
     * 查询当前练习记录详情。
     */
    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long sessionId = AssistantToolSupport.resolvePracticeSessionId(context.session());
        Map<String, Object> args = Map.of("practiceSessionId", sessionId);
        try {
            PracticeDetailVO detail = practiceService.getPracticeDetail(context.userId(), sessionId);
            List<String> wrongSamples = new ArrayList<>();
            if (detail.getAnswers() != null) {
                for (PracticeAnswerVO answer : detail.getAnswers()) {
                    if (answer.getIsCorrect() != null && answer.getIsCorrect() == 0) {
                        wrongSamples.add(AssistantToolSupport.abbreviate(answer.getStemText(), 60));
                    }
                    if (wrongSamples.size() >= 3) {
                        break;
                    }
                }
            }
            String summary = """
                    练习记录：%s
                    得分：%s/%s，正确题数：%s/%s，正确率：%s%%。
                    最近错题样例：%s
                    """.formatted(
                    detail.getSessionName(),
                    detail.getObtainedScore(),
                    detail.getTotalScore(),
                    detail.getCorrectCount(),
                    detail.getTotalQuestions(),
                    detail.getAccuracyRate(),
                    wrongSamples.isEmpty() ? "暂无" : String.join("；", wrongSamples)
            ).trim();
            return success(name(), args, detail, summary, startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }
}
