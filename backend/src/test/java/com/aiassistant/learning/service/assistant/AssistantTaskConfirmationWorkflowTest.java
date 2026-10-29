package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.assistant.tool.MaterialChapterOutlineAssistantTool;
import com.aiassistant.learning.service.assistant.tool.MaterialSearchAssistantTool;
import com.aiassistant.learning.service.assistant.tool.QuestionGenerateTaskAssistantTool;
import com.aiassistant.learning.service.assistant.tool.SummaryTaskAssistantTool;
import com.aiassistant.learning.vo.assistant.AssistantActionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantTaskConfirmationWorkflowTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AssistantTaskIntentParser parser = mock(AssistantTaskIntentParser.class);
    private final AssistantSessionStateSupport state = new AssistantSessionStateSupport(json, mock(QuestionSetService.class));
    private final MaterialSearchAssistantTool search = mock(MaterialSearchAssistantTool.class);
    private final AssistantActionService actions = mock(AssistantActionService.class);
    private final AssistantTaskWorkflowExecutor workflow = new AssistantTaskWorkflowExecutor(parser, state, search, actions);
    private final AssistantInteractionModeResolver modes = mock(AssistantInteractionModeResolver.class);
    private final AssistantPendingActionHandler pending = new AssistantPendingActionHandler(parser, state, modes,
            mock(AssistantToolExecutionSupport.class), workflow, mock(AssistantModelPolicy.class), search,
            mock(MaterialChapterOutlineAssistantTool.class));
    private final AssistantSession session = new AssistantSession();

    @BeforeEach
    void setup() {
        session.setId(42L);
        session.setUserId(7L);
        session.setCurrentMaterialId(9L);
        when(parser.buildQuestionConfigPrompt(any())).thenReturn("请补充出题题型与数量");
        when(actions.createPreview(eq(7L), same(session), eq(9L), anyList())).thenAnswer(call -> {
            List<AssistantPlannedTask> tasks = call.getArgument(3);
            state.savePendingAction(session, "ACTION_CONFIRMATION", AssistantPendingActionPayload.builder()
                    .actionId("new-preview").materialId(9L).tasks(tasks).build());
            return AssistantActionVO.builder().actionId("new-preview").sessionId(42L).revision(1L).status("PENDING")
                    .materialId(9L).tasks(tasks).build();
        });
    }

    @Test
    void completeMixedTasksCreateOnePreviewWithoutSubmittingEitherTask() {
        List<AssistantPlannedTask> tasks = List.of(summary(), question(false));
        AssistantWorkflowResolution result = workflow.executePlannedTasks(7L, session, 9L, tasks, new ArrayList<>(), new ArrayList<>());
        verify(actions).createPreview(7L, session, 9L, tasks);
        assertFalse(result.useModel());
        assertEquals(List.of("action.confirm"), result.executions().stream().map(AssistantTool.ToolExecutionResult::toolName).toList());
        assertEquals("WAITING", result.executions().get(0).status());
        assertTrue(result.replyText().contains("确认前不会创建任务"));
        assertEquals("ACTION_CONFIRMATION", session.getPendingActionType());
        assertEquals(2, state.readPendingActionPayload(session).getTasks().size());
    }

    @Test
    void missingQuestionConfigurationDefersSummaryAndPreservesAllTasksRegardlessOfOrder() {
        for (List<AssistantPlannedTask> tasks : List.of(List.of(summary(), question(true)), List.of(question(true), summary()))) {
            AssistantWorkflowResolution result = workflow.executePlannedTasks(7L, session, 9L, tasks, new ArrayList<>(), new ArrayList<>());
            assertFalse(result.useModel());
            assertEquals("QUESTION_CONFIG", session.getPendingActionType());
            assertEquals(tasks, state.readPendingActionPayload(session).getTasks());
            assertEquals(1, result.executions().size());
            assertEquals("WAITING", result.executions().get(0).status());
        }
        verifyNoInteractions(actions);
    }

    @Test
    void completingQuestionConfigurationRetainsTheSummaryBeforeCreatingPreview() {
        AssistantPlannedTask incomplete = question(true);
        state.savePendingAction(session, "QUESTION_CONFIG", AssistantPendingActionPayload.builder()
                .materialId(9L).tasks(List.of(summary(), incomplete)).build());
        AssistantPlannedTask completed = question(false);
        when(parser.resolveQuestionConfigReply(eq("五道多选题"), eq(incomplete), any()))
                .thenReturn(new AssistantTaskIntentParser.QuestionConfigResolution(true, completed, null));

        var result = resolve("五道多选题", AssistantInteractionMode.TASK_CONFIG_REPLY);

        assertEquals("action.confirm", result.executions().get(0).toolName());
        ArgumentCaptor<List<AssistantPlannedTask>> tasks = taskCaptor();
        verify(actions).createPreview(eq(7L), same(session), eq(9L), tasks.capture());
        assertEquals(List.of(summary(), completed), tasks.getValue());
    }

    @Test
    void incompleteEditRetainsOtherTasksAndInvalidatesTheOldPreviewPointer() {
        saveAction(List.of(summary(), question(false)));
        when(parser.resolveQuestionConfigReply(eq("调整一下题型"), any(), any()))
                .thenReturn(new AssistantTaskIntentParser.QuestionConfigResolution(false, question(true), "请补充题型"));

        var result = resolve("调整一下题型", AssistantInteractionMode.TASK_CONFIG_REPLY);

        assertEquals("QUESTION_CONFIG", session.getPendingActionType());
        var payload = state.readPendingActionPayload(session);
        assertNull(payload.getActionId());
        assertEquals(2, payload.getTasks().size());
        assertEquals("SUMMARY", payload.getTasks().get(0).getTaskType());
        assertTrue(payload.getTasks().get(1).getRequiresQuestionTypeConfirmation());
        assertFalse(result.useModel());
        verifyNoInteractions(actions);
    }

    @Test
    void completeEditCreatesANewPreviewWithAllTasks() {
        saveAction(List.of(summary(), question(false)));
        AssistantPlannedTask changed = question(false);
        changed.setQuestionCount(8);
        changed.setMultipleCount(8);
        when(parser.resolveQuestionConfigReply(eq("改为八道多选题"), any(), any()))
                .thenReturn(new AssistantTaskIntentParser.QuestionConfigResolution(true, changed, null));

        var result = resolve("改为八道多选题", AssistantInteractionMode.TASK_CREATE);

        assertEquals("action.confirm", result.executions().get(0).toolName());
        assertEquals("new-preview", state.readPendingActionPayload(session).getActionId());
        ArgumentCaptor<List<AssistantPlannedTask>> tasks = taskCaptor();
        verify(actions).createPreview(eq(7L), same(session), eq(9L), tasks.capture());
        assertEquals(List.of(summary(), changed), tasks.getValue());
    }

    @Test
    void textualConfirmOrCancelOnlyRemindsTheButtonsEvenWhenPlannerSaysTaskCreate() {
        for (String message : List.of("确认", "确认执行", "好的", "开始执行吧", "取消", "不用了", "取消执行")) {
            saveAction(List.of(summary()));
            var maliciousIntent = AssistantStructuredIntent.builder().interactionMode("TASK_CREATE")
                    .requestedTaskTypes(List.of("SUMMARY", "QUESTION_GENERATE")).materialQuery("另一份资料").build();
            var result = pending.resolve(7L, session, message, maliciousIntent, AssistantInteractionMode.TASK_CREATE,
                    AssistantPendingDecision.unknown());
            assertTrue(result.handled(), message);
            assertFalse(result.useModel(), message);
            assertTrue(result.replyText().contains("按钮"), message);
            assertEquals("ACTION_CONFIRMATION", session.getPendingActionType(), message);
            assertEquals("old-preview", state.readPendingActionPayload(session).getActionId(), message);
        }
        verifyNoInteractions(actions);
        verify(parser, never()).resolveQuestionConfigReply(anyString(), any(), any());
    }

    @Test
    void newTaskOrDifferentTopicClearsOldPreviewAndReturnsToTheMainRouter() {
        saveAction(List.of(summary(), question(false)));
        var newTask = resolve("另外创建一个总结任务", AssistantInteractionMode.TASK_CREATE);
        assertFalse(newTask.handled());
        assertNull(session.getPendingActionType());
        saveAction(List.of(summary()));
        var differentTopic = resolve("解释一下 TCP 三次握手", AssistantInteractionMode.STUDY_QA);
        assertFalse(differentTopic.handled());
        assertNull(session.getPendingActionPayloadJson());
        verifyNoInteractions(actions);
    }

    @Test
    void directToolExecutionCannotBypassTheActionConfirmationService() {
        AiTaskService taskService = mock(AiTaskService.class);
        var context = new AssistantTool.ToolContext(7L, session, "确认执行，生成总结并出题", "test", AssistantStructuredIntent.empty());
        assertEquals("FAILED", new SummaryTaskAssistantTool(taskService, parser, json).execute(context).status());
        assertEquals("FAILED", new QuestionGenerateTaskAssistantTool(taskService, parser, json).execute(context).status());
        verifyNoInteractions(taskService);
    }

    @Test
    void summaryOnlyEditChangesExplicitFieldsAndPreservesTheRest() {
        AssistantPlannedTask original = summary();
        original.setModelName("original-model");
        original.setTemperature(0.2);
        saveAction(List.of(original));

        var result = resolve("改成考试重点，不保存笔记", AssistantInteractionMode.TASK_CREATE);

        assertEquals("action.confirm", result.executions().get(0).toolName());
        ArgumentCaptor<List<AssistantPlannedTask>> tasks = taskCaptor();
        verify(actions).createPreview(eq(7L), same(session), eq(9L), tasks.capture());
        AssistantPlannedTask changed = tasks.getValue().get(0);
        assertEquals("EXAM", changed.getSummaryType());
        assertFalse(changed.getSaveAsNote());
        assertEquals("original-model", changed.getModelName());
        assertEquals(0.2, changed.getTemperature());
    }

    @Test
    void oneMessageCanChangeBothSummaryAndQuestionConfiguration() {
        saveAction(List.of(summary(), question(false)));
        var changedQuestion = question(false);
        changedQuestion.setQuestionCount(8);
        changedQuestion.setMultipleCount(8);
        String message = "总结改成考试重点，不保存笔记，题目改成八道多选";
        when(parser.resolveQuestionConfigReply(eq(message), any(), any()))
                .thenReturn(new AssistantTaskIntentParser.QuestionConfigResolution(true, changedQuestion, null));
        resolve(message, AssistantInteractionMode.TASK_CONFIG_REPLY);
        var tasks = taskCaptor();
        verify(actions).createPreview(eq(7L), same(session), eq(9L), tasks.capture());
        assertEquals("EXAM", tasks.getValue().get(0).getSummaryType());
        assertFalse(tasks.getValue().get(0).getSaveAsNote());
        assertEquals(8, tasks.getValue().get(1).getMultipleCount());
    }

    @Test
    void explicitSummaryModelAndTemperatureEditKeepsTypeAndSavePreference() {
        AssistantPlannedTask original = summary();
        original.setSummaryType("OUTLINE");
        original.setSaveAsNote(false);
        saveAction(List.of(original));

        resolve("模型改为gpt-test-v2，温度设为0.4", AssistantInteractionMode.TASK_CREATE);

        ArgumentCaptor<List<AssistantPlannedTask>> tasks = taskCaptor();
        verify(actions).createPreview(eq(7L), same(session), eq(9L), tasks.capture());
        AssistantPlannedTask changed = tasks.getValue().get(0);
        assertEquals("gpt-test-v2", changed.getModelName());
        assertEquals(0.4, changed.getTemperature());
        assertEquals("OUTLINE", changed.getSummaryType());
        assertFalse(changed.getSaveAsNote());
    }

    @Test
    void materialSelectionKeepsTheWholeMixedRequestUntilAConfirmationCardIsReady() {
        state.savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                .tasks(List.of(summary(), question(false)))
                .materialCandidates(List.of(AssistantMaterialCandidate.builder().id(9L).title("资料 A").build())).build());
        when(parser.resolveMaterialCandidateSelection(eq("第1份"), anyList(), any())).thenReturn(9L);
        var result = resolve("第1份", AssistantInteractionMode.MATERIAL_SELECTION);
        assertEquals(List.of("material.select", "action.confirm"), result.executions().stream()
                .map(AssistantTool.ToolExecutionResult::toolName).toList());
        assertEquals(2, state.readPendingActionPayload(session).getTasks().size());
    }

    private AssistantWorkflowResolution resolve(String message, AssistantInteractionMode mode) {
        return pending.resolve(7L, session, message, AssistantStructuredIntent.empty(), mode, AssistantPendingDecision.unknown());
    }

    private void saveAction(List<AssistantPlannedTask> tasks) {
        state.savePendingAction(session, "ACTION_CONFIRMATION", AssistantPendingActionPayload.builder()
                .actionId("old-preview").materialId(9L).tasks(tasks).build());
    }

    private AssistantPlannedTask summary() {
        return AssistantPlannedTask.builder().taskType("SUMMARY").summaryType("STANDARD").saveAsNote(true)
                .temperature(0.7).modelName("test").build();
    }

    private AssistantPlannedTask question(boolean incomplete) {
        return AssistantPlannedTask.builder().taskType("QUESTION_GENERATE").questionCount(5).singleCount(0)
                .multipleCount(5).judgeCount(0).shortAnswerCount(0).difficultyLevel(3).modelName("test")
                .requiresQuestionTypeConfirmation(incomplete).build();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<AssistantPlannedTask>> taskCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
