package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QuestionGenerateTaskAssistantTool extends AbstractAssistantTool {

    private final AiTaskService aiTaskService;

    public QuestionGenerateTaskAssistantTool(AiTaskService aiTaskService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.aiTaskService = aiTaskService;
    }

    @Override
    public String name() {
        return "task.submit_question_generate";
    }

    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveMaterialId(context.session()) != null;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long materialId = AssistantToolSupport.resolveMaterialId(context.session());
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setModelName(context.modelName());
        request.setQuestionCount(8);
        request.setSingleCount(5);
        request.setJudgeCount(2);
        request.setShortAnswerCount(1);
        request.setDifficultyLevel(3);
        Map<String, Object> args = Map.of(
                "materialId", materialId,
                "questionCount", request.getQuestionCount(),
                "singleCount", request.getSingleCount(),
                "judgeCount", request.getJudgeCount(),
                "shortAnswerCount", request.getShortAnswerCount(),
                "difficultyLevel", request.getDifficultyLevel()
        );
        try {
            AiTaskDetailVO detail = aiTaskService.submitQuestionGenerateTask(context.userId(), materialId, request);
            String summary = "已为当前资料创建 AI 出题任务，任务号 #%s，当前状态 %s。".formatted(
                    detail.getId(),
                    detail.getStatus()
            );
            return success(name(), args, detail, summary, startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }
}
