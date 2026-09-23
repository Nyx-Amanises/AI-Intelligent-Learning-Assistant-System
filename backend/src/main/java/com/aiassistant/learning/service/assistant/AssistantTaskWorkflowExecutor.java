package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.assistant.tool.MaterialSearchAssistantTool;
import com.aiassistant.learning.service.assistant.tool.QuestionGenerateTaskAssistantTool;
import com.aiassistant.learning.service.assistant.tool.SummaryTaskAssistantTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 任务创建工作流：把“帮我总结 / 出 10 道题”这类请求变成已提交的 AI 任务。
 *
 * <p>负责解析任务参数、定位目标资料（必要时挂起等待用户选择）、以及最终提交总结 / 出题任务。
 * 出题任务在需要用户确认题型时，会把任务挂到 QUESTION_CONFIG 待确认动作上。</p>
 */
@Component
public class AssistantTaskWorkflowExecutor {

    private final AssistantTaskIntentParser taskIntentParser;
    private final AssistantSessionStateSupport sessionState;
    private final MaterialSearchAssistantTool materialSearchAssistantTool;
    private final SummaryTaskAssistantTool summaryTaskAssistantTool;
    private final QuestionGenerateTaskAssistantTool questionGenerateTaskAssistantTool;

    public AssistantTaskWorkflowExecutor(
            AssistantTaskIntentParser taskIntentParser,
            AssistantSessionStateSupport sessionState,
            MaterialSearchAssistantTool materialSearchAssistantTool,
            SummaryTaskAssistantTool summaryTaskAssistantTool,
            QuestionGenerateTaskAssistantTool questionGenerateTaskAssistantTool
    ) {
        this.taskIntentParser = taskIntentParser;
        this.sessionState = sessionState;
        this.materialSearchAssistantTool = materialSearchAssistantTool;
        this.summaryTaskAssistantTool = summaryTaskAssistantTool;
        this.questionGenerateTaskAssistantTool = questionGenerateTaskAssistantTool;
    }

    /**
     * 处理总结或出题任务创建工作流。
     */
    public AssistantWorkflowResolution handleTaskCreation(
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent
    ) {
        List<String> requestedTaskTypes = taskIntentParser.resolveRequestedTaskTypes(userMessage, structuredIntent);
        if (requestedTaskTypes.isEmpty()) {
            return AssistantWorkflowResolution.notHandled();
        }

        List<AssistantPlannedTask> plannedTasks = buildPlannedTasks(userMessage, modelName, requestedTaskTypes, structuredIntent);
        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        List<Map<String, Object>> planSnapshot = new ArrayList<>();

        String explicitMaterialQuery = taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent);
        Long materialId = null;
        if (!StringUtils.hasText(explicitMaterialQuery)) {
            materialId = AssistantToolSupport.resolveMaterialId(session);
        }
        if (materialId == null) {
            materialId = sessionState.resolveMaterialIdFromQuestionSetContext(userId, session);
        }
        if (materialId == null) {
            if (!StringUtils.hasText(explicitMaterialQuery)) {
                String promptText = "请先告诉我资料标题、资料 ID，或者一个更明确的关键词，我再帮你继续处理。";
                sessionState.savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                        .promptText(promptText)
                        .tasks(plannedTasks)
                        .build());
                executions.add(sessionState.waitingExecution(
                        "material.search",
                        Map.of(),
                        Map.of("reason", "material_query_missing"),
                        promptText
                ));
                planSnapshot.add(Map.of("toolName", "material.search", "reason", "需要先定位资料"));
                return new AssistantWorkflowResolution(true, false, executions, null, planSnapshot);
            }

            AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, explicitMaterialQuery);
            executions.add(searchExecution);
            planSnapshot.add(Map.of("toolName", "material.search", "reason", "先根据标题关键词定位资料"));
            if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
                return new AssistantWorkflowResolution(true, false, executions, searchExecution.errorMessage(), planSnapshot);
            }

            AssistantMaterialSearchResult searchResult = sessionState.readMaterialSearchResult(searchExecution.toolResultJson());
            if (searchResult != null && searchResult.getSelectedMaterialId() != null) {
                materialId = searchResult.getSelectedMaterialId();
                sessionState.bindMaterialContext(session, materialId);
                sessionState.clearPendingAction(session);
            } else {
                sessionState.savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                        .promptText(searchExecution.summaryText())
                        .materialQuery(explicitMaterialQuery)
                        .materialCandidates(searchResult == null ? List.of() : searchResult.getCandidates())
                        .tasks(plannedTasks)
                        .build());
                return new AssistantWorkflowResolution(true, false, executions, searchExecution.summaryText(), planSnapshot);
            }
        }

        AssistantWorkflowResolution executionResolution = executePlannedTasks(userId, session, materialId, plannedTasks, executions, planSnapshot);
        return executionResolution.handled()
                ? executionResolution
                : new AssistantWorkflowResolution(true, false, executions, null, planSnapshot);
    }

    /**
     * 执行已经规划好的任务列表。
     *
     * <p>{@code executions} 和 {@code planSnapshot} 是调用方传入的可变列表，本方法会往里追加内容。</p>
     */
    public AssistantWorkflowResolution executePlannedTasks(
            Long userId,
            AssistantSession session,
            Long materialId,
            List<AssistantPlannedTask> tasks,
            List<AssistantTool.ToolExecutionResult> executions,
            List<Map<String, Object>> planSnapshot
    ) {
        if (materialId == null || tasks == null || tasks.isEmpty()) {
            return new AssistantWorkflowResolution(true, false, executions, null, planSnapshot);
        }

        for (AssistantPlannedTask task : tasks) {
            if (task == null || !StringUtils.hasText(task.getTaskType())) {
                continue;
            }
            String taskType = task.getTaskType().trim().toUpperCase();
            if ("SUMMARY".equals(taskType)) {
                planSnapshot.add(Map.of("toolName", "task.submit_summary", "reason", "按当前资料提交总结任务"));
                SummaryGenerateRequest request = new SummaryGenerateRequest();
                request.setModelName(task.getModelName());
                request.setSummaryType(task.getSummaryType());
                request.setSaveAsNote(task.getSaveAsNote());
                request.setTemperature(task.getTemperature());
                executions.add(summaryTaskAssistantTool.executeRequest(userId, materialId, request));
                continue;
            }
            if ("QUESTION_GENERATE".equals(taskType)) {
                if (Boolean.TRUE.equals(task.getRequiresQuestionTypeConfirmation())) {
                    String promptText = taskIntentParser.buildQuestionConfigPrompt(task);
                    AssistantPendingActionPayload payload = AssistantPendingActionPayload.builder()
                            .promptText(promptText)
                            .materialId(materialId)
                            .tasks(List.of(task))
                            .build();
                    sessionState.savePendingAction(session, "QUESTION_CONFIG", payload);
                    planSnapshot.add(Map.of("toolName", "task.submit_question_generate", "reason", "等待用户确认题型数量"));
                    executions.add(sessionState.waitingExecution(
                            "task.submit_question_generate",
                            Map.of("materialId", materialId, "questionCount", task.getQuestionCount()),
                            payload,
                            promptText
                    ));
                    return new AssistantWorkflowResolution(true, false, executions, null, planSnapshot);
                }

                planSnapshot.add(Map.of("toolName", "task.submit_question_generate", "reason", "按当前资料提交出题任务"));
                QuestionGenerateRequest request = new QuestionGenerateRequest();
                request.setModelName(task.getModelName());
                request.setQuestionCount(task.getQuestionCount());
                request.setSingleCount(task.getSingleCount());
                request.setJudgeCount(task.getJudgeCount());
                request.setShortAnswerCount(task.getShortAnswerCount());
                request.setDifficultyLevel(task.getDifficultyLevel());
                executions.add(questionGenerateTaskAssistantTool.executeRequest(
                        userId,
                        materialId,
                        request,
                        task.getAdjustmentNote()
                ));
            }
        }
        sessionState.clearPendingAction(session);
        return new AssistantWorkflowResolution(true, false, executions, null, planSnapshot);
    }

    /**
     * 把用户消息解析成待执行的任务列表（总结 / 出题及各自参数）。
     */
    private List<AssistantPlannedTask> buildPlannedTasks(
            String userMessage,
            String modelName,
            List<String> requestedTaskTypes,
            AssistantStructuredIntent structuredIntent
    ) {
        List<AssistantPlannedTask> tasks = new ArrayList<>();
        for (String taskType : requestedTaskTypes) {
            if ("SUMMARY".equalsIgnoreCase(taskType)) {
                AssistantTaskIntentParser.SummaryTaskOptions options =
                        taskIntentParser.parseSummaryRequest(userMessage, modelName, structuredIntent);
                tasks.add(AssistantPlannedTask.builder()
                        .taskType("SUMMARY")
                        .modelName(options.modelName())
                        .summaryType(options.summaryType())
                        .saveAsNote(options.saveAsNote())
                        .temperature(options.temperature())
                        .build());
                continue;
            }
            if ("QUESTION_GENERATE".equalsIgnoreCase(taskType)) {
                AssistantTaskIntentParser.QuestionTaskOptions options =
                        taskIntentParser.parseQuestionRequest(userMessage, modelName, structuredIntent);
                tasks.add(AssistantPlannedTask.builder()
                        .taskType("QUESTION_GENERATE")
                        .modelName(options.modelName())
                        .questionCount(options.questionCount())
                        .singleCount(options.singleCount())
                        .judgeCount(options.judgeCount())
                        .shortAnswerCount(options.shortAnswerCount())
                        .difficultyLevel(options.difficultyLevel())
                        .adjustmentNote(options.adjustmentNote())
                        .requiresQuestionTypeConfirmation(options.requiresQuestionTypeConfirmation())
                        .build());
            }
        }
        return tasks;
    }
}
