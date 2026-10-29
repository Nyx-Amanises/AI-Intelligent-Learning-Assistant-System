package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.assistant.tool.MaterialSearchAssistantTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 资料问答类工作流：围绕某份资料回答学习问题，以及处理用户对“当前是哪份资料”的质疑。
 */
@Component
public class AssistantStudyQaWorkflowHandler {

    private final AssistantTaskIntentParser taskIntentParser;
    private final AssistantSessionStateSupport sessionState;
    private final AssistantInteractionModeResolver modeResolver;
    private final AssistantToolExecutionSupport toolExecution;
    private final AssistantModelPolicy modelPolicy;
    private final MaterialSearchAssistantTool materialSearchAssistantTool;

    public AssistantStudyQaWorkflowHandler(
            AssistantTaskIntentParser taskIntentParser,
            AssistantSessionStateSupport sessionState,
            AssistantInteractionModeResolver modeResolver,
            AssistantToolExecutionSupport toolExecution,
            AssistantModelPolicy modelPolicy,
            MaterialSearchAssistantTool materialSearchAssistantTool
    ) {
        this.taskIntentParser = taskIntentParser;
        this.sessionState = sessionState;
        this.modeResolver = modeResolver;
        this.toolExecution = toolExecution;
        this.modelPolicy = modelPolicy;
        this.materialSearchAssistantTool = materialSearchAssistantTool;
    }

    /**
     * 处理学习问答工作流：先定位资料（必要时挂起等待选择），再用 RAG 或资料详情工具回答。
     */
    public AssistantWorkflowResolution handleStudyQa(
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent
    ) {
        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        List<Map<String, Object>> planSnapshot = new ArrayList<>();

        Long materialId = AssistantToolSupport.resolveMaterialId(session);
        if (materialId == null) {
            materialId = sessionState.resolveMaterialIdFromQuestionSetContext(userId, session);
        }

        String materialQuery = taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent);
        if (StringUtils.hasText(materialQuery)) {
            AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, materialQuery);
            executions.add(searchExecution);
            planSnapshot.add(Map.of("toolName", "material.search", "reason", "学习问答前先定位资料"));
            if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
                return new AssistantWorkflowResolution(true, false, executions, searchExecution.errorMessage(), planSnapshot);
            }

            AssistantMaterialSearchResult searchResult = sessionState.readMaterialSearchResult(searchExecution.toolResultJson());
            if (searchResult != null && searchResult.getSelectedMaterialId() != null) {
                materialId = searchResult.getSelectedMaterialId();
                sessionState.bindMaterialContext(session, materialId);
            } else {
                sessionState.savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                        .promptText(searchExecution.summaryText())
                        .materialQuery(materialQuery)
                        .followUpActionType("STUDY_QA")
                        .followUpUserMessage(userMessage)
                        .studyQuery(structuredIntent == null ? null : structuredIntent.getStudyQuery())
                        .materialCandidates(searchResult == null ? List.of() : searchResult.getCandidates())
                        .build());
                return new AssistantWorkflowResolution(true, false, executions, searchExecution.summaryText(), planSnapshot);
            }
        }

        if (materialId == null) {
            String replyText = "我可以先带你学，不过还需要先知道你想基于哪份资料。你可以直接告诉我资料标题，或者从某份资料页进入后再继续问我。";
            executions.add(sessionState.waitingExecution(
                    "material.search",
                    Map.of("reason", "material_context_missing"),
                    Map.of(),
                    replyText
            ));
            planSnapshot.add(Map.of("toolName", "material.search", "reason", "学习问答需要先定位资料"));
            return new AssistantWorkflowResolution(true, false, executions, replyText, planSnapshot);
        }

        String effectiveQuery = structuredIntent != null && StringUtils.hasText(structuredIntent.getStudyQuery())
                ? structuredIntent.getStudyQuery().trim()
                : modeResolver.resolveStudyQaQuery(userMessage, materialQuery);
        String toolMessage = StringUtils.hasText(effectiveQuery) ? effectiveQuery : userMessage;
        String toolName = modeResolver.isMaterialDetailQuestion(userMessage) ? "material.detail" : "rag.retrieve";
        planSnapshot.add(Map.of("toolName", toolName, "reason", "围绕当前资料回答学习问题"));
        AssistantTool.ToolExecutionResult execution = toolExecution.executeToolByName(
                toolName,
                userId,
                session,
                toolMessage,
                modelName,
                structuredIntent
        );
        if (execution != null) {
            executions.add(execution);
        }
        return new AssistantWorkflowResolution(true, modelPolicy.shouldUseAiModel(), executions, null, planSnapshot);
    }

    /**
     * 处理用户质疑资料定位、上下文选择等情况。
     */
    public AssistantWorkflowResolution handleContextChallenge(
            Long userId,
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent
    ) {
        String materialQuery = taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent);
        if (!StringUtils.hasText(materialQuery)) {
            String replyText = "你说得对，当前还没有足够信息让我唯一定位到资料。你可以直接告诉我资料 ID、回复候选序号，或者把资料标题再说完整一点。";
            return new AssistantWorkflowResolution(true, false, List.of(), replyText, List.of());
        }

        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        List<Map<String, Object>> planSnapshot = new ArrayList<>();
        AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, materialQuery);
        executions.add(searchExecution);
        planSnapshot.add(Map.of("toolName", "material.search", "reason", "用户在质疑或澄清当前资料定位"));
        if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
            return new AssistantWorkflowResolution(true, false, executions, searchExecution.errorMessage(), planSnapshot);
        }

        AssistantMaterialSearchResult searchResult = sessionState.readMaterialSearchResult(searchExecution.toolResultJson());
        if (searchResult != null && searchResult.getSelectedMaterialId() != null) {
            sessionState.bindMaterialContext(session, searchResult.getSelectedMaterialId());
        } else {
            sessionState.savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                    .promptText(searchExecution.summaryText())
                    .materialQuery(materialQuery)
                    .materialCandidates(searchResult == null ? List.of() : searchResult.getCandidates())
                    .build());
        }
        return new AssistantWorkflowResolution(true, false, executions, searchExecution.summaryText(), planSnapshot);
    }

    /**
     * 构造“不支持该能力”的工作流结果。
     */
    public AssistantWorkflowResolution buildUnsupportedResolution(AssistantStructuredIntent structuredIntent) {
        String unsupportedFeature = structuredIntent == null ? null : structuredIntent.getUnsupportedFeature();
        return new AssistantWorkflowResolution(true, false, List.of(), buildUnsupportedReply(unsupportedFeature), List.of());
    }

    /**
     * “目前我还没有这个功能”的统一话术。
     */
    public static String buildUnsupportedReply(String unsupportedFeature) {
        return StringUtils.hasText(unsupportedFeature)
                ? "目前我还没有“%s”这个功能。你可以先让我做资料检索、知识讲解、AI 总结、AI 出题、任务查询这些系统内能力。".formatted(unsupportedFeature)
                : "目前我还没有这个功能。你可以先让我做资料检索、知识讲解、AI 总结、AI 出题、任务查询这些系统内能力。";
    }
}
