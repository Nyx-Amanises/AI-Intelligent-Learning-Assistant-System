package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.assistant.tool.MaterialChapterOutlineAssistantTool;
import com.aiassistant.learning.service.assistant.tool.MaterialSearchAssistantTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 待确认动作（pending action）状态机。
 *
 * <p>会话上可能挂着两类等待用户回复的动作：</p>
 * <ul>
 *   <li>MATERIAL_SELECTION：搜索到多份候选资料，等用户选一份；</li>
 *   <li>QUESTION_CONFIG：出题前等用户确认题型和数量。</li>
 * </ul>
 * 本类负责根据用户的新消息推进、改写或结束这些动作，并在确认完成后接着执行原本要做的事。
 */
@Component
public class AssistantPendingActionHandler {

    private final AssistantTaskIntentParser taskIntentParser;
    private final AssistantSessionStateSupport sessionState;
    private final AssistantInteractionModeResolver modeResolver;
    private final AssistantToolExecutionSupport toolExecution;
    private final AssistantTaskWorkflowExecutor taskWorkflowExecutor;
    private final AssistantModelPolicy modelPolicy;
    private final MaterialSearchAssistantTool materialSearchAssistantTool;
    private final MaterialChapterOutlineAssistantTool materialChapterOutlineAssistantTool;

    public AssistantPendingActionHandler(
            AssistantTaskIntentParser taskIntentParser,
            AssistantSessionStateSupport sessionState,
            AssistantInteractionModeResolver modeResolver,
            AssistantToolExecutionSupport toolExecution,
            AssistantTaskWorkflowExecutor taskWorkflowExecutor,
            AssistantModelPolicy modelPolicy,
            MaterialSearchAssistantTool materialSearchAssistantTool,
            MaterialChapterOutlineAssistantTool materialChapterOutlineAssistantTool
    ) {
        this.taskIntentParser = taskIntentParser;
        this.sessionState = sessionState;
        this.modeResolver = modeResolver;
        this.toolExecution = toolExecution;
        this.taskWorkflowExecutor = taskWorkflowExecutor;
        this.modelPolicy = modelPolicy;
        this.materialSearchAssistantTool = materialSearchAssistantTool;
        this.materialChapterOutlineAssistantTool = materialChapterOutlineAssistantTool;
    }

    /**
     * 处理会话中等待用户确认的动作；没有 pending 或用户已经换话题时返回 notHandled。
     */
    public AssistantWorkflowResolution resolve(
            Long userId,
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantInteractionMode interactionMode,
            AssistantPendingDecision pendingDecision
    ) {
        if (session == null || !StringUtils.hasText(session.getPendingActionType())) {
            return AssistantWorkflowResolution.notHandled();
        }
        AssistantPendingActionPayload payload = sessionState.readPendingActionPayload(session);
        if (payload == null) {
            sessionState.clearPendingAction(session);
            return AssistantWorkflowResolution.notHandled();
        }

        String pendingActionType = session.getPendingActionType().trim().toUpperCase();
        if (modeResolver.shouldInterruptPendingAction(
                pendingActionType,
                userMessage,
                structuredIntent,
                null,
                interactionMode,
                pendingDecision
        )) {
            sessionState.clearPendingAction(session);
            return AssistantWorkflowResolution.notHandled();
        }

        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        List<Map<String, Object>> planSnapshot = new ArrayList<>();
        if ("MATERIAL_SELECTION".equals(pendingActionType)) {
            return resolveMaterialSelection(
                    userId, session, userMessage, structuredIntent, interactionMode, payload, executions, planSnapshot);
        }
        if ("QUESTION_CONFIG".equals(pendingActionType)) {
            return resolveQuestionConfig(
                    userId, session, userMessage, structuredIntent, interactionMode, payload, executions, planSnapshot);
        }

        sessionState.clearPendingAction(session);
        return AssistantWorkflowResolution.notHandled();
    }

    /**
     * 等待用户选择资料。
     */
    private AssistantWorkflowResolution resolveMaterialSelection(
            Long userId,
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantInteractionMode interactionMode,
            AssistantPendingActionPayload payload,
            List<AssistantTool.ToolExecutionResult> executions,
            List<Map<String, Object>> planSnapshot
    ) {
        Long selectedMaterialId = taskIntentParser.resolveMaterialCandidateSelection(
                userMessage,
                payload.getMaterialCandidates(),
                structuredIntent
        );
        if (selectedMaterialId != null) {
            return confirmMaterialAndContinue(
                    userId, session, payload, selectedMaterialId,
                    resolveMaterialTitle(payload, selectedMaterialId), executions, planSnapshot);
        }

        String materialQuery = taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent);
        if (StringUtils.hasText(materialQuery)) {
            AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, materialQuery);
            executions.add(searchExecution);
            planSnapshot.add(Map.of("toolName", "material.search", "reason", "根据用户补充的资料标题继续定位"));
            if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
                return new AssistantWorkflowResolution(true, false, executions, searchExecution.errorMessage(), planSnapshot);
            }

            AssistantMaterialSearchResult searchResult = sessionState.readMaterialSearchResult(searchExecution.toolResultJson());
            if (searchResult != null && searchResult.getSelectedMaterialId() != null) {
                return confirmMaterialAndContinue(
                        userId, session, payload, searchResult.getSelectedMaterialId(),
                        searchResult.getSelectedMaterialTitle(), executions, planSnapshot);
            }

            AssistantPendingActionPayload updatedPayload = AssistantPendingActionPayload.builder()
                    .promptText(searchExecution.summaryText())
                    .materialQuery(materialQuery)
                    .materialCandidates(searchResult == null ? List.of() : searchResult.getCandidates())
                    .followUpActionType(payload.getFollowUpActionType())
                    .followUpUserMessage(payload.getFollowUpUserMessage())
                    .chapterKeyword(payload.getChapterKeyword())
                    .tasks(payload.getTasks())
                    .build();
            sessionState.savePendingAction(session, "MATERIAL_SELECTION", updatedPayload);
            return new AssistantWorkflowResolution(true, false, executions, searchExecution.summaryText(), planSnapshot);
        }

        if (modeResolver.shouldBypassPendingAction("MATERIAL_SELECTION", interactionMode)) {
            sessionState.clearPendingAction(session);
            return AssistantWorkflowResolution.notHandled();
        }
        String promptText = StringUtils.hasText(payload.getPromptText())
                ? payload.getPromptText()
                : "我还没确认你指的是哪份资料。你可以直接回复序号、资料ID（例如 #7），或者把资料标题说完整一点。";
        executions.add(sessionState.waitingExecution(
                "material.search",
                Map.of("pending", true),
                payload,
                promptText
        ));
        planSnapshot.add(Map.of("toolName", "material.search", "reason", "等待用户确认具体资料"));
        return new AssistantWorkflowResolution(true, false, executions, promptText, planSnapshot);
    }

    /**
     * 资料已经确定：绑定上下文、记录一条 material.select 结果，然后执行后续动作或挂起的任务。
     */
    private AssistantWorkflowResolution confirmMaterialAndContinue(
            Long userId,
            AssistantSession session,
            AssistantPendingActionPayload payload,
            Long materialId,
            String materialTitle,
            List<AssistantTool.ToolExecutionResult> executions,
            List<Map<String, Object>> planSnapshot
    ) {
        sessionState.bindMaterialContext(session, materialId);
        String selectionSummary = StringUtils.hasText(materialTitle)
                ? "已确认资料《%s》，后续我会按这份资料继续。".formatted(materialTitle)
                : "已确认当前资料，后续我会按这份资料继续。";
        executions.add(sessionState.infoExecution(
                "material.select",
                Map.of("materialId", materialId),
                buildMaterialSelectionResult(materialId, materialTitle),
                selectionSummary
        ));
        sessionState.clearPendingAction(session);
        AssistantWorkflowResolution followUpResolution = executePendingFollowUp(userId, session, payload, executions, planSnapshot);
        if (followUpResolution.handled()) {
            return followUpResolution;
        }
        if (payload.getTasks() == null || payload.getTasks().isEmpty()) {
            return new AssistantWorkflowResolution(true, false, executions, selectionSummary, planSnapshot);
        }
        return taskWorkflowExecutor.executePlannedTasks(userId, session, materialId, payload.getTasks(), executions, planSnapshot);
    }

    /**
     * 等待用户确认出题配置。
     */
    private AssistantWorkflowResolution resolveQuestionConfig(
            Long userId,
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantInteractionMode interactionMode,
            AssistantPendingActionPayload payload,
            List<AssistantTool.ToolExecutionResult> executions,
            List<Map<String, Object>> planSnapshot
    ) {
        AssistantPlannedTask pendingQuestionTask = payload.getTasks().isEmpty() ? null : payload.getTasks().get(0);
        AssistantWorkflowResolution materialDisambiguationResolution = resolveMaterialDisambiguationDuringQuestionConfig(
                userId, session, userMessage, structuredIntent, payload, executions, planSnapshot);
        if (materialDisambiguationResolution.handled()) {
            return materialDisambiguationResolution;
        }
        AssistantTaskIntentParser.QuestionConfigResolution resolution =
                taskIntentParser.resolveQuestionConfigReply(userMessage, pendingQuestionTask, structuredIntent);
        if (!resolution.resolved()) {
            if (modeResolver.shouldBypassPendingAction("QUESTION_CONFIG", interactionMode)) {
                sessionState.clearPendingAction(session);
                return AssistantWorkflowResolution.notHandled();
            }
            String promptText = StringUtils.hasText(resolution.promptText())
                    ? resolution.promptText()
                    : taskIntentParser.buildQuestionConfigPrompt(pendingQuestionTask);
            payload.setPromptText(promptText);
            sessionState.savePendingAction(session, "QUESTION_CONFIG", payload);
            executions.add(sessionState.waitingExecution(
                    "task.submit_question_generate",
                    Map.of("pending", true),
                    payload,
                    promptText
            ));
            planSnapshot.add(Map.of("toolName", "task.submit_question_generate", "reason", "等待用户补充题型配置"));
            return new AssistantWorkflowResolution(true, false, executions, promptText, planSnapshot);
        }

        sessionState.clearPendingAction(session);
        Long materialId = payload.getMaterialId() != null ? payload.getMaterialId() : AssistantToolSupport.resolveMaterialId(session);
        if (materialId != null) {
            sessionState.bindMaterialContext(session, materialId);
        }
        return taskWorkflowExecutor.executePlannedTasks(userId, session, materialId, List.of(resolution.task()), executions, planSnapshot);
    }

    /**
     * 在等待出题配置时，如果用户转而质疑资料选择，则优先处理资料歧义。
     */
    private AssistantWorkflowResolution resolveMaterialDisambiguationDuringQuestionConfig(
            Long userId,
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantPendingActionPayload payload,
            List<AssistantTool.ToolExecutionResult> executions,
            List<Map<String, Object>> planSnapshot
    ) {
        boolean materialDisambiguation = Boolean.TRUE.equals(structuredIntent == null ? null : structuredIntent.getMaterialDisambiguation())
                || taskIntentParser.looksLikeMaterialAmbiguityChallenge(userMessage);
        String materialQuery = taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent);
        if (payload.getMaterialId() == null && StringUtils.hasText(materialQuery)) {
            materialDisambiguation = true;
        }
        if (!materialDisambiguation || !StringUtils.hasText(materialQuery)) {
            return AssistantWorkflowResolution.notHandled();
        }

        AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, materialQuery);
        executions.add(searchExecution);
        planSnapshot.add(Map.of("toolName", "material.search", "reason", "用户在补题型阶段转而澄清资料定位"));
        if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
            return new AssistantWorkflowResolution(true, false, executions, searchExecution.errorMessage(), planSnapshot);
        }

        AssistantMaterialSearchResult searchResult = sessionState.readMaterialSearchResult(searchExecution.toolResultJson());
        if (searchResult != null && searchResult.getSelectedMaterialId() != null) {
            sessionState.bindMaterialContext(session, searchResult.getSelectedMaterialId());
            payload.setMaterialId(searchResult.getSelectedMaterialId());
            String promptText = taskIntentParser.buildQuestionConfigPrompt(payload.getTasks().isEmpty() ? null : payload.getTasks().get(0));
            payload.setPromptText(promptText);
            sessionState.savePendingAction(session, "QUESTION_CONFIG", payload);
            return new AssistantWorkflowResolution(
                    true,
                    false,
                    executions,
                    searchExecution.summaryText() + System.lineSeparator() + promptText,
                    planSnapshot
            );
        }

        sessionState.clearMaterialContext(session);
        sessionState.savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                .promptText(searchExecution.summaryText())
                .materialQuery(materialQuery)
                .materialCandidates(searchResult == null ? List.of() : searchResult.getCandidates())
                .tasks(payload.getTasks())
                .build());
        return new AssistantWorkflowResolution(true, false, executions, searchExecution.summaryText(), planSnapshot);
    }

    /**
     * 用户完成资料确认后，继续执行之前被打断的动作（查看章节目录 / 回答学习问题）。
     */
    private AssistantWorkflowResolution executePendingFollowUp(
            Long userId,
            AssistantSession session,
            AssistantPendingActionPayload payload,
            List<AssistantTool.ToolExecutionResult> executions,
            List<Map<String, Object>> planSnapshot
    ) {
        if (payload == null || !StringUtils.hasText(payload.getFollowUpActionType())) {
            return AssistantWorkflowResolution.notHandled();
        }
        String followUpActionType = payload.getFollowUpActionType().trim().toUpperCase();
        if ("CHAPTER_BROWSE".equals(followUpActionType)) {
            planSnapshot.add(Map.of("toolName", "material.chapter_outline", "reason", "已确认资料，继续展开章节目录"));
            AssistantStructuredIntent structuredIntent = AssistantStructuredIntent.builder()
                    .chapterBrowse(true)
                    .chapterKeyword(payload.getChapterKeyword())
                    .build();
            AssistantTool.ToolExecutionResult execution = materialChapterOutlineAssistantTool.execute(
                    new AssistantTool.ToolContext(
                            userId,
                            session,
                            StringUtils.hasText(payload.getChapterKeyword()) ? payload.getChapterKeyword() : "查看章节目录",
                            null,
                            structuredIntent
                    )
            );
            executions.add(execution);
            return new AssistantWorkflowResolution(true, false, executions, execution.summaryText(), planSnapshot);
        }
        if ("STUDY_QA".equals(followUpActionType)) {
            String followUpMessage = StringUtils.hasText(payload.getFollowUpUserMessage())
                    ? payload.getFollowUpUserMessage()
                    : "继续回答当前资料问题";
            String effectiveQuery = modeResolver.resolveStudyQaQuery(followUpMessage, payload.getMaterialQuery());
            String toolMessage = StringUtils.hasText(effectiveQuery) ? effectiveQuery : followUpMessage;
            String toolName = modeResolver.isMaterialDetailQuestion(followUpMessage) ? "material.detail" : "rag.retrieve";
            String reason = "已确认资料，继续回答用户刚才的问题";
            AssistantTool.ToolExecutionResult execution = toolExecution.executeToolByName(
                    toolName,
                    userId,
                    session,
                    toolMessage,
                    null,
                    AssistantStructuredIntent.builder().build()
            );
            if (execution == null) {
                return new AssistantWorkflowResolution(true, false, executions, "资料已确认，但我这边暂时还没继续执行后续回答。你可以再问我一次。", planSnapshot);
            }
            planSnapshot.add(Map.of("toolName", toolName, "reason", reason));
            executions.add(execution);
            return new AssistantWorkflowResolution(true, modelPolicy.shouldUseAiModel(), executions, null, planSnapshot);
        }
        return AssistantWorkflowResolution.notHandled();
    }

    private String resolveMaterialTitle(AssistantPendingActionPayload payload, Long materialId) {
        if (payload == null || materialId == null || payload.getMaterialCandidates() == null) {
            return null;
        }
        return payload.getMaterialCandidates().stream()
                .filter(candidate -> materialId.equals(candidate.getId()))
                .map(AssistantMaterialCandidate::getTitle)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildMaterialSelectionResult(Long materialId, String materialTitle) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("materialId", materialId);
        result.put("materialTitle", materialTitle);
        return result;
    }
}
