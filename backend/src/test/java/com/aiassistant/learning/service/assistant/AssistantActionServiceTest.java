package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.dto.assistant.AssistantActionDecisionRequest;
import com.aiassistant.learning.entity.*;
import com.aiassistant.learning.mapper.*;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantActionServiceTest {
    final AssistantActionMapper actions = mock(AssistantActionMapper.class);
    final AssistantSessionMapper sessions = mock(AssistantSessionMapper.class);
    final AssistantMessageMapper messages = mock(AssistantMessageMapper.class);
    final StudyMaterialMapper materials = mock(StudyMaterialMapper.class);
    final AiTaskService tasks = mock(AiTaskService.class);
    final AiConfigService configs = mock(AiConfigService.class);
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final AssistantActionService service = new AssistantActionService(actions, sessions, messages, materials, tasks, configs, json);
    final AssistantSession session = new AssistantSession();
    AssistantAction stored;

    @BeforeAll static void metadata() {
        var builder = new MapperBuilderAssistant(new MybatisConfiguration(), "action-service-test");
        for (Class<?> type : List.of(AssistantSession.class, AssistantAction.class, StudyMaterial.class))
            TableInfoHelper.initTableInfo(builder, type);
    }
    @BeforeEach void setup() {
        session.setId(42L); session.setUserId(7L);
        when(sessions.selectOne(any())).thenReturn(session);
        StudyMaterial material = new StudyMaterial(); material.setId(11L); material.setUserId(7L); material.setTitle("网络原理");
        when(materials.selectOne(any())).thenReturn(material);
        doAnswer(inv -> { stored = inv.getArgument(0); stored.setId(1L); return 1; }).when(actions).insert(any(AssistantAction.class));
        when(actions.selectOne(any())).thenAnswer(inv -> stored);
        when(tasks.submitSummaryTask(eq(7L), eq(11L), any())).thenReturn(AiTaskDetailVO.builder().id(901L).build());
        when(tasks.submitQuestionGenerateTask(eq(7L), eq(11L), any())).thenReturn(AiTaskDetailVO.builder().id(902L).build());
    }
    @Test void previewCopiesConfigurationWithoutCreatingTasks() {
        AssistantPlannedTask task = summary();
        var result = service.createPreview(7L, session, 11L, List.of(task));
        task.setSummaryType("EXAM");
        assertEquals("STANDARD", service.pending(7L, 42L).getTasks().get(0).getSummaryType());
        assertEquals("PENDING", result.getStatus());
        assertEquals("网络原理", result.getMaterialTitle());
        assertTrue(result.getExpiresAt().isAfter(LocalDateTime.now()));
        assertTrue(session.getPendingActionPayloadJson().contains(result.getActionId()));
        verifyNoInteractions(tasks, messages);
    }
    @Test void confirmUsesSnapshotAndRepeatedConfirmationReturnsTheSameTasks() {
        var preview = service.createPreview(7L, session, 11L, List.of(summary(), questions()));
        var result = service.decide(7L, 42L, preview.getActionId(), decision("CONFIRM", 1L));
        var repeated = service.decide(7L, 42L, preview.getActionId(), decision("CONFIRM", 1L));
        assertEquals(List.of(901L, 902L), result.getTaskIds());
        assertEquals(result, repeated);
        assertEquals("CONFIRMED", stored.getStatus());
        assertNull(session.getPendingActionType());
        ArgumentCaptor<SummaryGenerateRequest> request = ArgumentCaptor.forClass(SummaryGenerateRequest.class);
        verify(tasks).submitSummaryTask(eq(7L), eq(11L), request.capture());
        assertEquals("STANDARD", request.getValue().getSummaryType());
        assertFalse(request.getValue().getSaveAsNote());
        verify(tasks).submitQuestionGenerateTask(eq(7L), eq(11L), any());
        verify(messages).insert(any(AssistantMessage.class));
        assertEquals(result, service.pending(7L, 42L));
    }
    @Test void cancellationAndDuplicateCancellationNeverCreateTasks() {
        var preview = service.createPreview(7L, session, 11L, List.of(summary()));
        var result = service.decide(7L, 42L, preview.getActionId(), decision("CANCEL", 1L));
        assertEquals("CANCELLED", result.getStatus());
        assertEquals(result, service.decide(7L, 42L, preview.getActionId(), decision("CANCEL", 1L)));
        assertConflict(() -> service.decide(7L, 42L, preview.getActionId(), decision("CONFIRM", 1L)));
        verifyNoInteractions(tasks);
    }
    @Test void outdatedRevisionExpiryAndReplacedPreviewCannotExecute() {
        var preview = service.createPreview(7L, session, 11L, List.of(summary()));
        assertConflict(() -> service.decide(7L, 42L, preview.getActionId(), decision("CONFIRM", 2L)));
        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertConflict(() -> service.decide(7L, 42L, preview.getActionId(), decision("CONFIRM", 1L)));
        assertEquals("EXPIRED", service.pending(7L, 42L).getStatus());
        assertNull(session.getPendingActionType());
        stored.setStatus("PENDING"); stored.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        assertEquals("SUPERSEDED", service.pending(7L, 42L).getStatus());
        assertConflict(() -> service.decide(7L, 42L, preview.getActionId(), decision("CONFIRM", 1L)));
        verifyNoInteractions(tasks);
    }
    @Test void ownershipIsCheckedBeforePreviewAndAgainAtConfirmation() {
        when(sessions.selectOne(any())).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class, () -> service.pending(8L, 42L)).getCode());
        verifyNoInteractions(actions, materials, tasks);
        when(sessions.selectOne(any())).thenReturn(session);
        var preview = service.createPreview(7L, session, 11L, List.of(summary()));
        when(materials.selectOne(any())).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.decide(7L, 42L, preview.getActionId(), decision("CONFIRM", 1L))).getCode());
        assertEquals("PENDING", stored.getStatus());
        verifyNoInteractions(tasks);
    }
    @Test void queriesLockSessionBeforeActionAndConstrainOwnership() {
        var preview = service.createPreview(7L, session, 11L, List.of(summary()));
        clearInvocations(sessions, actions);
        service.decide(7L, 42L, preview.getActionId(), decision("CANCEL", 1L));
        var order = inOrder(sessions, actions);
        ArgumentCaptor<LambdaQueryWrapper<AssistantSession>> sessionQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<AssistantAction>> actionQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        order.verify(sessions).selectOne(sessionQuery.capture());
        order.verify(actions).selectOne(actionQuery.capture());
        assertTrue(sessionQuery.getValue().getSqlSegment().contains("for update"));
        assertTrue(sessionQuery.getValue().getSqlSegment().contains("user_id"));
        assertTrue(actionQuery.getValue().getSqlSegment().contains("for update"));
        assertTrue(actionQuery.getValue().getSqlSegment().contains("session_id"));
        assertTrue(actionQuery.getValue().getSqlSegment().contains("user_id"));
        assertTrue(actionQuery.getValue().getParamNameValuePairs().containsValue(preview.getActionId()));
    }
    @Test void taskCreationFailureLeavesNoConfirmedResultAndPropagatesForTransactionRollback() {
        var preview = service.createPreview(7L, session, 11L, List.of(summary(), questions()));
        when(tasks.submitQuestionGenerateTask(anyLong(), anyLong(), any())).thenThrow(new BusinessException("失败"));
        assertThrows(BusinessException.class, () -> service.decide(7L, 42L, preview.getActionId(), decision("CONFIRM", 1L)));
        assertEquals("PENDING", stored.getStatus());
        assertNull(stored.getResultJson());
        verify(messages, never()).insert(any(AssistantMessage.class));
    }
    @Test void incompleteOrInvalidTasksCannotBePreviewed() {
        var question = questions(); question.setRequiresQuestionTypeConfirmation(true);
        assertThrows(BusinessException.class, () -> service.createPreview(7L, session, 11L, List.of(summary(), question)));
        question.setRequiresQuestionTypeConfirmation(false); question.setSingleCount(99);
        assertThrows(BusinessException.class, () -> service.createPreview(7L, session, 11L, List.of(question)));
        assertThrows(BusinessException.class, () -> service.createPreview(7L, session, 11L, new ArrayList<>()));
        verify(actions, never()).insert(any(AssistantAction.class));
        verifyNoInteractions(tasks);
    }
    private AssistantPlannedTask summary() {
        return AssistantPlannedTask.builder().taskType("SUMMARY").modelName("test-model").summaryType("STANDARD")
                .saveAsNote(false).temperature(0.7).build();
    }
    private AssistantPlannedTask questions() {
        return AssistantPlannedTask.builder().taskType("QUESTION_GENERATE").modelName("test-model").questionCount(3)
                .singleCount(0).multipleCount(3).judgeCount(0).shortAnswerCount(0).difficultyLevel(3)
                .knowledgePoints(List.of("TCP")).build();
    }
    private AssistantActionDecisionRequest decision(String decision, Long revision) {
        var request = new AssistantActionDecisionRequest(); request.setRevision(revision); request.setDecision(decision); return request;
    }
    private void assertConflict(org.junit.jupiter.api.function.Executable code) {
        assertEquals(409, assertThrows(BusinessException.class, code).getCode());
    }
}
