package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.AiTaskCreateRequest;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.QuestionGenerateTaskPayload;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.AiTaskMapper;
import com.aiassistant.learning.mapper.AiGenerationRecordMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.service.AiQuestionService;
import com.aiassistant.learning.service.AiSummaryService;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.cache.AiTaskRedisCacheService;
import com.aiassistant.learning.service.task.AiTaskProcessor;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class QuestionGenerationLifecycleTest {

    private static final long USER_ID = 7L;
    private static final long MATERIAL_ID = 42L;
    private static final long QUESTION_SET_ID = 91L;
    private static final long TASK_ID = 77L;

    private final AiTaskMapper tasks = mock(AiTaskMapper.class);
    private final QuestionSetMapper questionSets = mock(QuestionSetMapper.class);
    private final AiQuestionService generator = mock(AiQuestionService.class);
    private final AiTaskProcessor processor = mock(AiTaskProcessor.class);
    private final AiTaskService executor = mock(AiTaskService.class);
    private final AiTaskRedisCacheService cache = mock(AiTaskRedisCacheService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AiTaskServiceImpl service = new AiTaskServiceImpl(
            tasks, mock(StudyMaterialMapper.class), mock(PracticeSessionMapper.class), questionSets,
            generator, mock(AiGenerationRecordMapper.class), mock(AiSummaryService.class),
            List.of(processor), json, cache, executor);

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "question-task-test");
        TableInfoHelper.initTableInfo(assistant, AiTask.class);
        TableInfoHelper.initTableInfo(assistant, QuestionSet.class);
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
    }

    @BeforeEach
    void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void createsAndLinksTheQuestionSetBeforeSchedulingAiWork() throws Exception {
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        when(generator.createGeneratingQuestionSet(USER_ID, MATERIAL_ID, request)).thenReturn(questionSet("GENERATING"));
        when(tasks.insert(any(AiTask.class))).thenAnswer(invocation -> {
            AiTask task = invocation.getArgument(0);
            task.setId(TASK_ID);
            return 1;
        });

        AiTaskDetailVO result = service.submitQuestionGenerateTask(USER_ID, MATERIAL_ID, request);

        QuestionGenerateTaskPayload payload = json.readValue(result.getPayloadJson(), QuestionGenerateTaskPayload.class);
        assertEquals(QUESTION_SET_ID, payload.getQuestionSetId());
        assertEquals(MATERIAL_ID, payload.getMaterialId());
        assertEquals("PENDING", result.getStatus());
        assertEquals("MATERIAL", result.getBizType());
        assertEquals(MATERIAL_ID, result.getBizId());
        InOrder order = inOrder(generator, tasks);
        order.verify(generator).createGeneratingQuestionSet(USER_ID, MATERIAL_ID, request);
        order.verify(tasks).insert(any(AiTask.class));
        verifyNoInteractions(executor, processor);
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());

        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();

        verify(executor).executeTask(TASK_ID);
    }

    @Test
    void failedGenerationEndsThePlaceholderStatus() {
        AiTask task = task("PENDING");
        when(tasks.selectById(TASK_ID)).thenReturn(task);
        when(tasks.update(any(), any())).thenReturn(1);
        when(processor.supports("QUESTION_GENERATE")).thenReturn(true);
        when(processor.process(task)).thenThrow(new BusinessException("模型暂不可用"));

        service.executeTask(TASK_ID);

        assertEquals("FAILED", task.getStatus());
        assertEquals("模型暂不可用", task.getErrorMessage());
        assertQuestionSetTransition("GENERATING", "FAILED");
        verify(tasks, times(2)).update(any(), any());
    }

    @Test
    void retryReusesTheSameFailedQuestionSetAndResetsItsStatus() throws Exception {
        AiTask task = task("FAILED");
        when(tasks.selectOne(any())).thenReturn(task);
        when(questionSets.selectOne(any())).thenReturn(questionSet("FAILED"));

        AiTaskDetailVO result = service.retryTask(USER_ID, TASK_ID);

        assertEquals("PENDING", result.getStatus());
        assertEquals(1, result.getRetryCount());
        assertEquals(QUESTION_SET_ID, json.readValue(result.getPayloadJson(), QuestionGenerateTaskPayload.class).getQuestionSetId());
        assertQuestionSetTransition("FAILED", "GENERATING");
        verifyNoInteractions(generator, executor);
        verify(questionSets, never()).insert(any(QuestionSet.class));
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    }

    @Test
    void retryDoesNotResetAnAlreadyPublishedQuestionSet() {
        when(tasks.selectOne(any())).thenReturn(task("FAILED"));
        when(questionSets.selectOne(any())).thenReturn(questionSet("PUBLISHED"));

        AiTaskDetailVO result = service.retryTask(USER_ID, TASK_ID);

        assertEquals("PENDING", result.getStatus());
        verify(questionSets, never()).update(any(), any());
        verifyNoInteractions(generator);
    }

    @Test
    void retryRejectsADeletedQuestionSetWithoutCreatingAnother() {
        when(tasks.selectOne(any())).thenReturn(task("FAILED"));

        assertThrows(BusinessException.class, () -> service.retryTask(USER_ID, TASK_ID));

        verify(tasks, never()).update(any(), any());
        verifyNoInteractions(generator, executor);
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void dispatchOfALegacyTaskCreatesAndSavesItsPlaceholderLink() throws Exception {
        AiTask task = task("PENDING");
        task.setPayloadJson("{\"materialId\":42,\"questionCount\":5}");
        when(tasks.selectOne(any())).thenReturn(task);
        when(generator.createGeneratingQuestionSet(any(), any(), any())).thenReturn(questionSet("GENERATING"));

        AiTaskDetailVO result = service.dispatchTask(USER_ID, TASK_ID);

        assertEquals(QUESTION_SET_ID, json.readValue(result.getPayloadJson(), QuestionGenerateTaskPayload.class).getQuestionSetId());
        verify(tasks).update(any(), any());
        verifyNoInteractions(executor);
    }

    @Test
    void deletingAQueuedTaskCancelsItsPlaceholder() {
        when(tasks.selectOne(any())).thenReturn(task("PENDING"));

        service.deleteTask(USER_ID, TASK_ID);

        assertQuestionSetTransition("GENERATING", "CANCELLED");
        verify(tasks).deleteById(TASK_ID);
        verifyNoInteractions(cache);
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(cache).evictTaskDetail(USER_ID, TASK_ID);
    }

    @Test
    void deletingARunningTaskCancelsItsPlaceholderIncludingAfterARestart() {
        when(tasks.selectOne(any())).thenReturn(task("RUNNING"));

        service.deleteTask(USER_ID, TASK_ID);

        assertQuestionSetTransition("GENERATING", "CANCELLED");
        verify(tasks).deleteById(TASK_ID);
    }

    @Test
    void newGenericTasksCannotReuseAnotherTasksPlaceholder() {
        AiTaskCreateRequest request = new AiTaskCreateRequest();
        request.setTaskType("QUESTION_GENERATE");
        request.setBizType("MATERIAL");
        request.setBizId(MATERIAL_ID);
        request.setPayloadJson("{\"materialId\":42,\"questionSetId\":91}");

        assertThrows(BusinessException.class, () -> service.createTask(USER_ID, request));

        verifyNoInteractions(questionSets, generator, executor);
    }

    @Test
    void completionAfterTaskDeletionCannotRecacheAGhostTask() {
        AiTask task = task("PENDING");
        when(tasks.selectById(TASK_ID)).thenReturn(task);
        when(tasks.update(any(), any())).thenReturn(1, 0);
        when(processor.supports("QUESTION_GENERATE")).thenReturn(true);
        when(processor.process(task)).thenReturn(new AiTaskProcessor.TaskExecutionResult("{\"id\":91}", 100));

        service.executeTask(TASK_ID);

        verify(cache, never()).cacheTaskDetail(any());
        verify(cache, times(2)).evictTaskDetail(USER_ID, TASK_ID);
    }

    @Test
    void failureAfterTaskDeletionCannotRecacheAGhostTask() {
        AiTask task = task("PENDING");
        when(tasks.selectById(TASK_ID)).thenReturn(task);
        when(tasks.update(any(), any())).thenReturn(1, 0);
        when(processor.supports("QUESTION_GENERATE")).thenReturn(true);
        when(processor.process(task)).thenThrow(new BusinessException("生成已取消"));

        service.executeTask(TASK_ID);

        verify(cache, never()).cacheTaskDetail(any());
        verify(cache, times(2)).evictTaskDetail(USER_ID, TASK_ID);
    }

    @Test
    void onlyTheWorkerThatClaimsThePendingTaskCallsAi() {
        when(tasks.selectById(TASK_ID)).thenReturn(task("PENDING"));
        when(tasks.update(any(), any())).thenReturn(0);

        service.executeTask(TASK_ID);

        verifyNoInteractions(processor, questionSets, generator);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void assertQuestionSetTransition(String from, String to) {
        ArgumentCaptor<LambdaUpdateWrapper<QuestionSet>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(questionSets).update(any(), update.capture());
        LambdaUpdateWrapper<QuestionSet> wrapper = update.getValue();
        assertTrue(wrapper.getSqlSet().contains("status="));
        String condition = wrapper.getSqlSegment();
        assertTrue(condition.contains("id ="));
        assertTrue(condition.contains("user_id ="));
        assertTrue(condition.contains("status ="));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(QUESTION_SET_ID));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(USER_ID));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(from));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(to));
    }

    private AiTask task(String status) {
        AiTask task = new AiTask();
        task.setId(TASK_ID);
        task.setUserId(USER_ID);
        task.setTaskType("QUESTION_GENERATE");
        task.setBizType("MATERIAL");
        task.setBizId(MATERIAL_ID);
        task.setStatus(status);
        task.setPayloadJson("{\"materialId\":42,\"questionSetId\":91,\"questionCount\":5}");
        return task;
    }

    private QuestionSet questionSet(String status) {
        QuestionSet questionSet = new QuestionSet();
        questionSet.setId(QUESTION_SET_ID);
        questionSet.setUserId(USER_ID);
        questionSet.setMaterialId(MATERIAL_ID);
        questionSet.setStatus(status);
        return questionSet;
    }
}
