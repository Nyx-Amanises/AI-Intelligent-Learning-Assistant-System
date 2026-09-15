package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.dto.ai.EmbeddingTaskPayload;
import com.aiassistant.learning.dto.ai.EmbeddingTaskRequest;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.AiTaskMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.cache.AiTaskRedisCacheService;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AiTaskServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long MATERIAL_ID = 42L;

    private final AiTaskMapper tasks = mock(AiTaskMapper.class);
    private final AiTaskRedisCacheService cache = mock(AiTaskRedisCacheService.class);
    private final AiTaskService executor = mock(AiTaskService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiTaskServiceImpl service = new AiTaskServiceImpl(
            tasks,
            mock(StudyMaterialMapper.class),
            mock(PracticeSessionMapper.class),
            mock(QuestionSetMapper.class),
            List.of(),
            objectMapper,
            cache,
            executor
    );

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, AiTask.class);
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

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RUNNING"})
    @SuppressWarnings({"unchecked", "rawtypes"})
    void reusesAnActiveEmbeddingTaskWithoutCreatingOrSchedulingAnother(String status) {
        AiTask active = new AiTask();
        active.setId(11L);
        active.setUserId(USER_ID);
        active.setTaskType("EMBEDDING");
        active.setBizType("MATERIAL");
        active.setBizId(MATERIAL_ID);
        active.setStatus(status);
        active.setPayloadJson("{\"materialId\":42,\"forceRegenerate\":false}");
        when(tasks.selectOne(any())).thenReturn(active);
        EmbeddingTaskRequest request = new EmbeddingTaskRequest();
        request.setForceRegenerate(true);

        AiTaskDetailVO result = service.submitEmbeddingTask(USER_ID, MATERIAL_ID, request);

        assertEquals(11L, result.getId());
        assertEquals(status, result.getStatus());
        assertEquals(active.getPayloadJson(), result.getPayloadJson());
        verify(tasks, never()).insert(any(AiTask.class));
        verify(tasks, never()).updateById(any(AiTask.class));
        verify(cache).cacheTaskDetail(result);
        verifyNoInteractions(executor);
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());

        ArgumentCaptor<LambdaQueryWrapper<AiTask>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(tasks).selectOne(query.capture());
        query.getValue().getSqlSegment();
        assertEquals(
                Set.of(USER_ID, "EMBEDDING", "MATERIAL", MATERIAL_ID, "PENDING", "RUNNING"),
                Set.copyOf(query.getValue().getParamNameValuePairs().values())
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createsANewTaskWithTheRequestedRebuildFlagAndDispatchesAfterCommit(boolean forceRegenerate) throws Exception {
        when(tasks.insert(any(AiTask.class))).thenAnswer(invocation -> {
            AiTask task = invocation.getArgument(0);
            task.setId(22L);
            return 1;
        });
        EmbeddingTaskRequest request = new EmbeddingTaskRequest();
        request.setModelName("test-embedding-model");
        request.setForceRegenerate(forceRegenerate);

        AiTaskDetailVO result = service.submitEmbeddingTask(USER_ID, MATERIAL_ID, request);

        ArgumentCaptor<AiTask> inserted = ArgumentCaptor.forClass(AiTask.class);
        verify(tasks).insert(inserted.capture());
        AiTask task = inserted.getValue();
        EmbeddingTaskPayload payload = objectMapper.readValue(task.getPayloadJson(), EmbeddingTaskPayload.class);
        assertEquals(USER_ID, task.getUserId());
        assertEquals("EMBEDDING", task.getTaskType());
        assertEquals("MATERIAL", task.getBizType());
        assertEquals(MATERIAL_ID, task.getBizId());
        assertEquals("PENDING", task.getStatus());
        assertEquals(4, task.getPriority());
        assertEquals("test-embedding-model", task.getModelName());
        assertEquals(MATERIAL_ID, payload.getMaterialId());
        assertEquals("test-embedding-model", payload.getModelName());
        assertEquals(forceRegenerate, payload.getForceRegenerate());
        assertEquals(22L, result.getId());
        verify(cache).cacheTaskDetail(result);
        verifyNoInteractions(executor);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());

        synchronizations.get(0).afterCommit();

        verify(executor).executeTask(22L);
    }
}
