package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.assistant.tool.MaterialChapterOutlineAssistantTool;
import com.aiassistant.learning.service.assistant.tool.MaterialSearchAssistantTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantStudyQaPendingQueryTest {
    private static final String ORIGINAL_QUESTION = "那它在《微积分》里如何推导？";
    private static final String RESOLVED_QUESTION = "微积分中乘积求导法则如何从导数定义推导？";
    private final AssistantTaskIntentParser parser = mock(AssistantTaskIntentParser.class);
    private final AssistantSessionStateSupport state =
            new AssistantSessionStateSupport(new ObjectMapper(), mock(QuestionSetService.class));
    private final AssistantInteractionModeResolver modes = mock(AssistantInteractionModeResolver.class);
    private final AssistantToolExecutionSupport execution = mock(AssistantToolExecutionSupport.class);
    private final AssistantModelPolicy modelPolicy = mock(AssistantModelPolicy.class);
    private final MaterialSearchAssistantTool search = mock(MaterialSearchAssistantTool.class);
    private final AssistantStudyQaWorkflowHandler study =
            new AssistantStudyQaWorkflowHandler(parser, state, modes, execution, modelPolicy, search);
    private final AssistantPendingActionHandler pending = new AssistantPendingActionHandler(
            parser, state, modes, execution, mock(AssistantTaskWorkflowExecutor.class), modelPolicy,
            search, mock(MaterialChapterOutlineAssistantTool.class));
    private final AssistantSession session = new AssistantSession();

    @BeforeEach
    void setup() {
        session.setId(42L);
        session.setUserId(7L);
        when(modelPolicy.shouldUseAiModel()).thenReturn(true);
        // Mockito 对 Long 的默认返回值是 0L；未选中必须显式返回 null，才能进入继续检索分支。
        when(parser.resolveMaterialCandidateSelection(anyString(), anyList(), any())).thenReturn(null);
        when(execution.executeToolByName(anyString(), eq(7L), eq(session), anyString(), isNull(), any()))
                .thenAnswer(invocation -> state.infoExecution(invocation.getArgument(0), Map.of(), Map.of(), "检索完成"));
    }

    @Test
    void resolvingMaterialPreservesTheHistoryResolvedQuestionAcrossPendingSerialization() {
        startAmbiguousStudyQuestion();

        AssistantPendingActionPayload saved = state.readPendingActionPayload(session);
        assertNotNull(saved);
        assertEquals(ORIGINAL_QUESTION, saved.getFollowUpUserMessage());
        assertEquals(RESOLVED_QUESTION, saved.getStudyQuery());

        AssistantWorkflowResolution result = selectFirstMaterial();

        assertTrue(result.handled());
        assertTrue(result.useModel());
        assertEquals(101L, session.getCurrentMaterialId());
        assertNull(session.getPendingActionType());
        assertEquals(List.of("material.select", "rag.retrieve"),
                result.executions().stream().map(AssistantTool.ToolExecutionResult::toolName).toList());
        ArgumentCaptor<AssistantStructuredIntent> intent = ArgumentCaptor.forClass(AssistantStructuredIntent.class);
        verify(execution).executeToolByName(eq("rag.retrieve"), eq(7L), eq(session), eq(RESOLVED_QUESTION),
                isNull(), intent.capture());
        assertEquals(RESOLVED_QUESTION, intent.getValue().getStudyQuery());
        verify(modes, never()).resolveStudyQaQuery(anyString(), anyString());
        verify(modes).isMaterialDetailQuestion(ORIGINAL_QUESTION);
    }

    @Test
    void anotherAmbiguousMaterialSearchRetainsBothTheOriginalAndResolvedQuestions() {
        startAmbiguousStudyQuestion();
        AssistantStructuredIntent refinement = AssistantStructuredIntent.builder().materialQuery("大学微积分").build();
        when(parser.extractMaterialQueryText("查找大学微积分", refinement)).thenReturn("大学微积分");
        when(search.search(7L, "大学微积分")).thenReturn(ambiguousSearch("大学微积分"));

        AssistantWorkflowResolution result = pending.resolve(7L, session, "查找大学微积分", refinement,
                AssistantInteractionMode.MATERIAL_SELECTION, AssistantPendingDecision.unknown());

        assertFalse(result.useModel());
        verify(search).search(7L, "大学微积分");
        AssistantPendingActionPayload saved = state.readPendingActionPayload(session);
        assertNotNull(saved);
        assertEquals("大学微积分", saved.getMaterialQuery());
        assertEquals(ORIGINAL_QUESTION, saved.getFollowUpUserMessage());
        assertEquals(RESOLVED_QUESTION, saved.getStudyQuery());
        selectFirstMaterial();
        verify(execution).executeToolByName(eq("rag.retrieve"), eq(7L), eq(session), eq(RESOLVED_QUESTION),
                isNull(), any());
    }

    @Test
    void oldPendingPayloadWithoutStudyQueryStillUsesTheOriginalQuestionFallback() {
        session.setPendingActionType("MATERIAL_SELECTION");
        session.setPendingActionPayloadJson("""
                {"followUpActionType":"STUDY_QA","followUpUserMessage":"请根据《微积分》解释导数定义",
                 "materialQuery":"微积分","materialCandidates":[{"id":101,"title":"微积分 A"}]}
                """);
        when(modes.resolveStudyQaQuery("请根据《微积分》解释导数定义", "微积分")).thenReturn("解释导数定义");

        selectFirstMaterial();

        verify(execution).executeToolByName(eq("rag.retrieve"), eq(7L), eq(session), eq("解释导数定义"),
                isNull(), any());
    }

    @Test
    void resolvedQueryDoesNotReplaceOriginalWordingWhenChoosingTheMaterialDetailTool() {
        state.savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                .followUpActionType("STUDY_QA")
                .followUpUserMessage("它有多少页？")
                .studyQuery("  微积分第二版的总页数  ")
                .materialCandidates(candidates())
                .build());
        when(modes.isMaterialDetailQuestion("它有多少页？")).thenReturn(true);

        selectFirstMaterial();

        verify(execution).executeToolByName(eq("material.detail"), eq(7L), eq(session), eq("微积分第二版的总页数"),
                isNull(), any());
        verify(modes).isMaterialDetailQuestion("它有多少页？");
    }

    private void startAmbiguousStudyQuestion() {
        AssistantStructuredIntent intent = AssistantStructuredIntent.builder()
                .interactionMode("STUDY_QA").materialQuery("微积分").studyQuery(RESOLVED_QUESTION).build();
        when(parser.extractMaterialQueryText(ORIGINAL_QUESTION, intent)).thenReturn("微积分");
        when(search.search(7L, "微积分")).thenReturn(ambiguousSearch("微积分"));
        AssistantWorkflowResolution result = study.handleStudyQa(7L, session, ORIGINAL_QUESTION, "model", intent);
        assertTrue(result.handled());
        assertFalse(result.useModel());
        assertEquals("MATERIAL_SELECTION", session.getPendingActionType());
    }

    private AssistantWorkflowResolution selectFirstMaterial() {
        when(parser.resolveMaterialCandidateSelection(eq("第1份"), anyList(), any())).thenReturn(101L);
        return pending.resolve(7L, session, "第1份", AssistantStructuredIntent.empty(),
                AssistantInteractionMode.MATERIAL_SELECTION, AssistantPendingDecision.unknown());
    }

    private AssistantTool.ToolExecutionResult ambiguousSearch(String query) {
        return state.infoExecution("material.search", Map.of("queryText", query),
                AssistantMaterialSearchResult.builder().queryText(query).needsClarification(true)
                        .candidates(candidates()).build(), "请选择一份资料");
    }

    private List<AssistantMaterialCandidate> candidates() {
        return List.of(AssistantMaterialCandidate.builder().id(101L).title("微积分 A").build(),
                AssistantMaterialCandidate.builder().id(102L).title("微积分 B").build());
    }
}
