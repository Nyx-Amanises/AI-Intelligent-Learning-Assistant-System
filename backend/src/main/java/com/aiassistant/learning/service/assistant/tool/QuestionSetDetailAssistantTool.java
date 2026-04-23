package com.aiassistant.learning.service.assistant.tool;

import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.assistant.AbstractAssistantTool;
import com.aiassistant.learning.service.assistant.AssistantToolSupport;
import com.aiassistant.learning.vo.question.QuestionItemVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 题集详情助手工具。
 */
@Component
public class QuestionSetDetailAssistantTool extends AbstractAssistantTool {

    /** 题集服务。 */
    private final QuestionSetService questionSetService;

    public QuestionSetDetailAssistantTool(QuestionSetService questionSetService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.questionSetService = questionSetService;
    }

    /**
     * 工具名称。
     */
    @Override
    public String name() {
        return "question_set.detail";
    }

    /**
     * 当前会话绑定题集时支持该工具。
     */
    @Override
    public boolean supports(ToolContext context) {
        return AssistantToolSupport.resolveQuestionSetId(context.session()) != null;
    }

    /**
     * 查询当前题集详情。
     */
    @Override
    public ToolExecutionResult execute(ToolContext context) {
        LocalDateTime startedAt = LocalDateTime.now();
        Long questionSetId = AssistantToolSupport.resolveQuestionSetId(context.session());
        Map<String, Object> args = Map.of("questionSetId", questionSetId);
        try {
            QuestionSetDetailVO detail = questionSetService.getQuestionSetDetail(context.userId(), questionSetId);
            List<String> stems = new ArrayList<>();
            if (detail.getQuestions() != null) {
                for (QuestionItemVO question : detail.getQuestions()) {
                    stems.add(AssistantToolSupport.abbreviate(question.getStemText(), 60));
                    if (stems.size() >= 3) {
                        break;
                    }
                }
            }
            String summary = """
                    当前题集：%s
                    题目数：%s，总分：%s，难度：%s。
                    示例题目：%s
                    """.formatted(
                    detail.getTitle(),
                    detail.getQuestionCount(),
                    detail.getTotalScore(),
                    detail.getDifficultyLevel(),
                    stems.isEmpty() ? "暂无" : String.join("；", stems)
            ).trim();
            return success(name(), args, detail, summary, startedAt);
        } catch (Exception exception) {
            return failure(name(), args, exception.getMessage(), startedAt);
        }
    }
}
