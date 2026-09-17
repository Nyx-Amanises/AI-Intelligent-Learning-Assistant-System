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
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryTaskPayload;
import com.aiassistant.learning.entity.AiGenerationRecord;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.AiGenerationRecordMapper;
import com.aiassistant.learning.mapper.AiTaskMapper;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SummaryGenerationLifecycleTest {

    private final AiTaskMapper tasks = mock(AiTaskMapper.class);
    private final AiGenerationRecordMapper records = mock(AiGenerationRecordMapper.class);
    private final AiSummaryService summaries = mock(AiSummaryService.class);
    private final AiTaskProcessor processor = mock(AiTaskProcessor.class);
    private final AiTaskService executor = mock(AiTaskService.class);
    private final AiTaskRedisCacheService cache = mock(AiTaskRedisCacheService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AiTaskServiceImpl service = new AiTaskServiceImpl(
            tasks, mock(StudyMaterialMapper.class), mock(PracticeSessionMapper.class), mock(QuestionSetMapper.class),
            mock(AiQuestionService.class), records, summaries, List.of(processor), json, cache, executor);

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "summary-task-test");
        TableInfoHelper.initTableInfo(assistant, AiTask.class);
        TableInfoHelper.initTableInfo(assistant, AiGenerationRecord.class);
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
    void summaryRecordExistsAndIsLinkedBeforeAiWorkCanBeDispatched() throws Exception {
        SummaryGenerateRequest request = new SummaryGenerateRequest();
        when(summaries.createGeneratingSummaryRecord(7L, 42L, request)).thenReturn(record("GENERATING"));
        when(tasks.insert(any(AiTask.class))).thenAnswer(invocation -> {
            AiTask task = invocation.getArgument(0);
            task.setId(77L);
            return 1;
        });

        AiTaskDetailVO result = service.submitSummaryTask(7L, 42L, request);

        SummaryTaskPayload payload = json.readValue(result.getPayloadJson(), SummaryTaskPayload.class);
        assertEquals(201L, payload.getRecordId());
        assertEquals(42L, payload.getMaterialId());
        assertEquals("test-model", payload.getModelName());
        assertEquals("PENDING", result.getStatus());
        assertEquals("MATERIAL", result.getBizType());
        assertEquals(42L, result.getBizId());
        InOrder order = inOrder(summaries, tasks);
        order.verify(summaries).createGeneratingSummaryRecord(7L, 42L, request);
        order.verify(tasks).insert(any(AiTask.class));
        verifyNoInteractions(executor, processor);

        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();

        verify(executor).executeTask(77L);
    }

    @Test
    void failurePersistsAnErrorOnTheExistingSummaryRecord() {
        AiTask task = task("PENDING");
        when(tasks.selectById(77L)).thenReturn(task);
        when(tasks.update(any(), any())).thenReturn(1);
        when(processor.supports("SUMMARY")).thenReturn(true);
        when(processor.process(task)).thenThrow(new BusinessException("模型暂不可用"));

        service.executeTask(77L);

        assertEquals("FAILED", task.getStatus());
        assertEquals("模型暂不可用", task.getErrorMessage());
        LambdaUpdateWrapper<AiGenerationRecord> update = assertRecordTransition("GENERATING", "FAILED");
        assertTrue(update.getParamNameValuePairs().containsValue("模型暂不可用"));
        verify(records, never()).insert(any(AiGenerationRecord.class));
    }

    @Test
    void retryKeepsTheSameRecordAndClearsItsFailureStatus() throws Exception {
        when(tasks.selectOne(any())).thenReturn(task("FAILED"));
        when(records.selectOne(any())).thenReturn(record("FAILED"));

        AiTaskDetailVO result = service.retryTask(7L, 77L);

        assertEquals("PENDING", result.getStatus());
        assertEquals(1, result.getRetryCount());
        assertEquals(201L, json.readValue(result.getPayloadJson(), SummaryTaskPayload.class).getRecordId());
        LambdaUpdateWrapper<AiGenerationRecord> update = assertRecordTransition("FAILED", "GENERATING");
        assertTrue(update.getSqlSet().contains("error_message="));
        verifyNoInteractions(summaries, executor);
        verify(records, never()).insert(any(AiGenerationRecord.class));
    }

    @Test
    void retryOfAnAlreadyCompletedSummaryDoesNotResetItsSavedContent() {
        when(tasks.selectOne(any())).thenReturn(task("FAILED"));
        when(records.selectOne(any())).thenReturn(record("SUCCESS"));

        service.retryTask(7L, 77L);

        verify(records, never()).update(any(), any());
        verifyNoInteractions(summaries);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RUNNING"})
    void deletingATaskCancelsItsSummaryAndInvalidatesCacheAfterCommit(String status) {
        when(tasks.selectOne(any())).thenReturn(task(status));

        service.deleteTask(7L, 77L);

        assertRecordTransition("GENERATING", "CANCELLED");
        verify(tasks).deleteById(77L);
        verifyNoInteractions(cache);
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(cache).evictTaskDetail(7L, 77L);
    }

    @Test
    void newGenericTasksCannotBindToAnExistingSummaryRecord() {
        AiTaskCreateRequest request = new AiTaskCreateRequest();
        request.setTaskType("SUMMARY");
        request.setBizType("MATERIAL");
        request.setBizId(42L);
        request.setPayloadJson("{\"materialId\":42,\"recordId\":201}");

        assertThrows(BusinessException.class, () -> service.createTask(7L, request));

        verifyNoInteractions(summaries, records, executor);
    }

    @Test
    void dispatchOfALegacyTaskCreatesItsStableSummaryRecord() throws Exception {
        AiTask task = task("PENDING");
        task.setPayloadJson("{\"materialId\":42}");
        when(tasks.selectOne(any())).thenReturn(task);
        when(summaries.createGeneratingSummaryRecord(any(), any(), any())).thenReturn(record("GENERATING"));

        AiTaskDetailVO result = service.dispatchTask(7L, 77L);

        assertEquals(201L, json.readValue(result.getPayloadJson(), SummaryTaskPayload.class).getRecordId());
        verify(tasks).update(any(), any());
        verifyNoInteractions(executor);
    }

    @Test
    void aLateFailureCannotRestoreACancelledTasksCache() {
        AiTask task = task("PENDING");
        when(tasks.selectById(77L)).thenReturn(task);
        when(tasks.update(any(), any())).thenReturn(1, 0);
        when(records.update(any(), any())).thenReturn(0);
        when(processor.supports("SUMMARY")).thenReturn(true);
        when(processor.process(task)).thenThrow(new BusinessException("总结状态已变化"));

        service.executeTask(77L);

        assertRecordTransition("GENERATING", "FAILED");
        verify(cache, never()).cacheTaskDetail(any());
        verify(cache, times(2)).evictTaskDetail(7L, 77L);
    }

    @Test
    void retryRejectsMissingOrUnownedRecordsWithoutCreatingAnother() {
        when(tasks.selectOne(any())).thenReturn(task("FAILED"));

        assertThrows(BusinessException.class, () -> service.retryTask(7L, 77L));

        verifyNoInteractions(summaries, executor);
        verify(tasks, never()).update(any(), any());
        verify(records, never()).update(any(), any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaUpdateWrapper<AiGenerationRecord> assertRecordTransition(String from, String to) {
        ArgumentCaptor<LambdaUpdateWrapper<AiGenerationRecord>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(records).update(any(), update.capture());
        LambdaUpdateWrapper<AiGenerationRecord> wrapper = update.getValue();
        String condition = wrapper.getSqlSegment();
        assertTrue(condition.contains("status ="));
        assertTrue(condition.contains("user_id ="));
        assertTrue(condition.contains("task_type ="));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(201L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(7L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(from));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(to));
        return wrapper;
    }

    private AiTask task(String status) {
        AiTask task = new AiTask();
        task.setId(77L);
        task.setUserId(7L);
        task.setTaskType("SUMMARY");
        task.setBizType("MATERIAL");
        task.setBizId(42L);
        task.setStatus(status);
        task.setPayloadJson("{\"materialId\":42,\"recordId\":201}");
        return task;
    }

    private AiGenerationRecord record(String status) {
        AiGenerationRecord record = new AiGenerationRecord();
        record.setId(201L);
        record.setUserId(7L);
        record.setMaterialId(42L);
        record.setTaskType("SUMMARY");
        record.setSummaryType("STANDARD");
        record.setModelName("test-model");
        record.setStatus(status);
        return record;
    }
}
