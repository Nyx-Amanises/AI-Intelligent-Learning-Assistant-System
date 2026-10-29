package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.assistant.tool.MaterialSearchAssistantTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 任务创建工作流：把“帮我总结 / 出 10 道题”这类请求变成待确认的 AI 任务预览。
 *
 * <p>负责解析任务参数、定位目标资料（必要时挂起等待用户选择）、以及最终生成总结 / 出题任务确认卡片。
 * 出题任务在需要用户确认题型时，会把任务挂到 QUESTION_CONFIG 待确认动作上。</p>
 */
@Component
public class AssistantTaskWorkflowExecutor {

    private final AssistantTaskIntentParser taskIntentParser;
    private final AssistantSessionStateSupport sessionState;
    private final MaterialSearchAssistantTool materialSearchAssistantTool;
    private final AssistantActionService actionService;

    public AssistantTaskWorkflowExecutor(
            AssistantTaskIntentParser taskIntentParser,
            AssistantSessionStateSupport sessionState,
            MaterialSearchAssistantTool materialSearchAssistantTool,
            AssistantActionService actionService
    ) {
        this.taskIntentParser = taskIntentParser;
        this.sessionState = sessionState;
        this.materialSearchAssistantTool = materialSearchAssistantTool;
        this.actionService = actionService;
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
        if (materialId == null && !StringUtils.hasText(explicitMaterialQuery)) {
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
     * 为已经规划好的任务列表补齐配置并生成确认预览。
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
            if (task != null && "QUESTION_GENERATE".equalsIgnoreCase(task.getTaskType())
                    && Boolean.TRUE.equals(task.getRequiresQuestionTypeConfirmation())) {
                String promptText = taskIntentParser.buildQuestionConfigPrompt(task);
                AssistantPendingActionPayload payload = AssistantPendingActionPayload.builder()
                        .promptText(promptText).materialId(materialId).tasks(new ArrayList<>(tasks)).build();
                sessionState.savePendingAction(session, "QUESTION_CONFIG", payload);
                planSnapshot.add(Map.of("toolName", "task.submit_question_generate", "reason", "先补齐题型数量，所有任务均未提交"));
                executions.add(sessionState.waitingExecution("task.submit_question_generate",
                        Map.of("materialId", materialId), payload, promptText));
                return new AssistantWorkflowResolution(true, false, executions, promptText, planSnapshot);
            }
        }
        var action = actionService.createPreview(userId, session, materialId, tasks);
        String promptText = "任务配置已整理，请核对动作卡片并点击“确认执行”或“取消”。确认前不会创建任务。";
        planSnapshot.add(Map.of("toolName", "action.confirm", "reason", "等待用户确认固定的任务配置"));
        executions.add(sessionState.waitingExecution("action.confirm", Map.of("actionId", action.getActionId()), action, promptText));
        return new AssistantWorkflowResolution(true, false, executions, promptText, planSnapshot);
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
                        .multipleCount(options.multipleCount())
                        .judgeCount(options.judgeCount())
                        .shortAnswerCount(options.shortAnswerCount())
                        .difficultyLevel(options.difficultyLevel())
                        .knowledgePoints(options.knowledgePoints())
                        .adjustmentNote(options.adjustmentNote())
                        .requiresQuestionTypeConfirmation(options.requiresQuestionTypeConfirmation())
                        .build());
            }
        }
        return tasks;
    }
}
