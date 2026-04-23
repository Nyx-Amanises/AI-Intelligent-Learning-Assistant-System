package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
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
 * AI 出题任务助手工具。
 */
@Component
public class QuestionGenerateTaskAssistantTool extends AbstractAssistantTool {

    /** AI 任务服务。 */
    private final AiTaskService aiTaskService;
    /** 规则意图解析器。 */
    private final AssistantTaskIntentParser taskIntentParser;

    public QuestionGenerateTaskAssistantTool(
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
        return "task.submit_question_generate";
    }

    /**
     * 当前会话绑定资料时支持创建出题任务。
     */
    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveMaterialId(context.session()) != null;
    }

    /**
     * 从用户消息中解析出题参数并提交任务。
     */
    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long materialId = AssistantToolSupport.resolveMaterialId(context.session());
        AssistantTaskIntentParser.QuestionTaskOptions options = taskIntentParser.parseQuestionRequest(
                context.userMessage(),
                context.modelName(),
                context.structuredIntent()
        );
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setModelName(options.modelName());
        request.setQuestionCount(options.questionCount());
        request.setSingleCount(options.singleCount());
        request.setJudgeCount(options.judgeCount());
        request.setShortAnswerCount(options.shortAnswerCount());
        request.setDifficultyLevel(options.difficultyLevel());
        return executeRequest(context.userId(), materialId, request, options.adjustmentNote(), startedAt);
    }

    /**
     * 直接提交出题任务，供编排器在已解析好参数时调用。
     */
    public ToolExecutionResult executeRequest(Long userId, Long materialId, QuestionGenerateRequest request, String adjustmentNote) {
        return executeRequest(userId, materialId, request, adjustmentNote, LocalDateTime.now());
    }

    /**
     * 提交出题任务的内部实现。
     */
    private ToolExecutionResult executeRequest(
            Long userId,
            Long materialId,
            QuestionGenerateRequest request,
            String adjustmentNote,
            LocalDateTime startedAt
    ) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("materialId", materialId);
        args.put("questionCount", request.getQuestionCount());
        args.put("singleCount", request.getSingleCount());
        args.put("judgeCount", request.getJudgeCount());
        args.put("shortAnswerCount", request.getShortAnswerCount());
        args.put("difficultyLevel", request.getDifficultyLevel());
        if (request.getModelName() != null) {
            args.put("modelName", request.getModelName());
        }
        if (adjustmentNote != null) {
            args.put("adjustmentNote", adjustmentNote);
        }
        try {
            AiTaskDetailVO detail = aiTaskService.submitQuestionGenerateTask(userId, materialId, request);
            String summary = "已为当前资料创建 AI 出题任务（单选 %s、判断 %s、简答 %s，难度 %s），任务号 #%s，当前状态 %s。%s".formatted(
                    request.getSingleCount(),
                    request.getJudgeCount(),
                    request.getShortAnswerCount(),
                    request.getDifficultyLevel(),
                    detail.getId(),
                    detail.getStatus(),
                    adjustmentNote == null ? "" : adjustmentNote
            );
            return success(name(), args, detail, summary.trim(), startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }
}
