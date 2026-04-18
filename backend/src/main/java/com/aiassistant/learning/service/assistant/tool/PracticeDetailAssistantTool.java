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

@Component
public class PracticeDetailAssistantTool extends AbstractAssistantTool {

    private final PracticeService practiceService;

    public PracticeDetailAssistantTool(PracticeService practiceService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.practiceService = practiceService;
    }

    @Override
    public String name() {
        return "practice.detail";
    }

    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolvePracticeSessionId(context.session()) != null;
    }

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
