package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.config.QdrantProperties;
import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.TextEmbeddingService;
import com.aiassistant.learning.service.VectorStoreService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;

class AiEmbeddingServiceImplTest {
    private final StudyMaterialMapper materials = mock(StudyMaterialMapper.class);
    private final MaterialSegmentMapper segments = mock(MaterialSegmentMapper.class);
    private final AiConfigService config = mock(AiConfigService.class);
    private final TextEmbeddingService embeddings = mock(TextEmbeddingService.class);
    private final VectorStoreService vectors = mock(VectorStoreService.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final QdrantProperties properties = new QdrantProperties();
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transaction = mock(TransactionStatus.class);
    private final AiEmbeddingServiceImpl service = new AiEmbeddingServiceImpl(
            materials, segments, config, embeddings, vectors, properties, dataSource, transactionManager);
    private MaterialSegment segment;
    private final Map<Long, Map<String, Object>> savedIndexes = new LinkedHashMap<>();
    private final Map<Long, String> indexedPoints = new LinkedHashMap<>();
    private final Object savepoint = new Object();

    @BeforeAll
    static void metadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "embedding-test");
        TableInfoHelper.initTableInfo(assistant, MaterialSegment.class);
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        StudyMaterial material = new StudyMaterial();
        material.setId(42L);
        material.setParseStatus("SUCCESS");
        when(materials.selectOne(any())).thenReturn(material);
        segment = new MaterialSegment();
        segment.setId(10L);
        segment.setMaterialId(42L);
        segment.setContentText("资料正文");
        segment.setEmbeddingStatus("SUCCESS");
        segment.setEmbeddingModel("emb");
        segment.setEmbeddingMode("LIVE");
        segment.setVectorId("10");
        segment.setEmbeddedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        when(segments.selectList(any())).thenReturn(List.of(segment));
        when(config.getResolvedConfig()).thenReturn(new AiConfigService.ResolvedAiConfig(
                true, false, "OPENAI_COMPATIBLE", "https://example.test", "/chat", "OPENAI_COMPATIBLE",
                "https://example.test", "/embedding", "test-key", "test-key", "chat", "emb"));
        Connection connection = mock(Connection.class);
        DatabaseMetaData dbMetadata = mock(DatabaseMetaData.class);
        ResultSet columns = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(dbMetadata);
        when(dbMetadata.getColumns(any(), any(), any(), any())).thenReturn(columns);
        when(columns.next()).thenReturn(true);
        when(embeddings.embedTexts(anyList(), eq("emb")))
                .thenReturn(new TextEmbeddingService.EmbeddingBatchResult("emb", List.of(List.of(0.1D)), false));
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        when(transaction.createSavepoint()).thenReturn(savepoint);
        when(segments.update(isNull(), any())).thenAnswer(invocation -> {
            LambdaUpdateWrapper<MaterialSegment> update = invocation.getArgument(1);
            String condition = update.getSqlSegment();
            var id = Pattern.compile("\\bid\\s*=\\s*#\\{ew.paramNameValuePairs.(MPGENVAL\\d+)\\}").matcher(condition);
            assertTrue(id.find());
            Long segmentId = (Long) update.getParamNameValuePairs().get(id.group(1));
            Map<String, Object> values = new LinkedHashMap<>();
            var fields = Pattern.compile("(\\w+)\\s*=\\s*#\\{ew.paramNameValuePairs.(MPGENVAL\\d+)\\}").matcher(update.getSqlSet());
            while (fields.find()) values.put(fields.group(1), update.getParamNameValuePairs().get(fields.group(2)));
            savedIndexes.put(segmentId, values);
            return 1;
        });
        doAnswer(invocation -> {
            List<VectorStoreService.MaterialSegmentVector> batch = invocation.getArgument(0);
            for (var point : batch) indexedPoints.put(point.segmentId(), point.attemptId());
            return null;
        }).when(vectors).upsertMaterialSegments(anyList());
        doAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(2);
            String attempt = invocation.getArgument(3);
            for (Long id : ids) {
                if (attempt == null || attempt.equals(indexedPoints.get(id))) indexedPoints.remove(id);
            }
            return null;
        }).when(vectors).deleteSegmentVectors(eq(7L), eq(42L), anyList(), any());
    }

    @Test
    void knownMatchingIndexIsReusedWithItsPersistedProvenance() {
        var result = service.prepareMaterialEmbedding(7L, 42L, "emb", false, 100L);

        assertEquals(1, result.getSkippedSegments());
        assertEquals("LIVE", result.getEmbeddingMode());
        verifyNoInteractions(embeddings, vectors);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void embeddingLocksTheOwnedMaterialBeforeReadingOrWritingItsIndex() {
        service.prepareMaterialEmbedding(7L, 42L, "emb", false, 100L);

        ArgumentCaptor<LambdaQueryWrapper<StudyMaterial>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(materials).selectOne(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("id ="));
        assertTrue(sql.contains("user_id ="));
        assertTrue(sql.endsWith("limit 1 for update"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "MOCK", "OTHER_MODEL"})
    void indexWithoutMatchingProvenanceOrModelIsRebuilt(String issue) {
        if ("OTHER_MODEL".equals(issue)) segment.setEmbeddingModel("other");
        else segment.setEmbeddingMode(issue);

        var result = service.prepareMaterialEmbedding(7L, 42L, "emb", false, 100L);

        assertEquals(0, result.getSkippedSegments());
        assertEquals(1, result.getStoredSegments());
        assertEquals("LIVE", segment.getEmbeddingMode());
        assertEquals("emb", segment.getEmbeddingModel());
        verify(vectors).upsertMaterialSegments(anyList());
    }

    @Test
    void storedProvenanceComesFromActualEmbeddingBatchRatherThanTheEarlierConfigRead() {
        when(embeddings.embedTexts(anyList(), eq("emb")))
                .thenReturn(new TextEmbeddingService.EmbeddingBatchResult("actual-model", List.of(List.of(0.1D)), true));

        var result = service.prepareMaterialEmbedding(7L, 42L, "emb", true, 100L);

        assertEquals("MOCK", segment.getEmbeddingMode());
        assertEquals("actual-model", segment.getEmbeddingModel());
        assertEquals("MOCK", result.getEmbeddingMode());
    }

    @Test
    void secondBatchFailureCommitsAllPendingAsFailedAndCleansEveryPendingPointButKeepsSkippedIndex() {
        MaterialSegment skipped = segment;
        skipped.setEmbeddingAttemptId("old-success");
        MaterialSegment first = pending(11L);
        MaterialSegment second = pending(12L);
        when(segments.selectList(any())).thenReturn(List.of(skipped, first, second));
        indexedPoints.put(10L, "old-success");
        properties.setUpsertBatchSize(1);
        when(embeddings.embedTexts(anyList(), eq("emb")))
                .thenReturn(batch()).thenThrow(new BusinessException("第二批模型失败"));

        assertThrows(BusinessException.class, () -> service.prepareMaterialEmbedding(7L, 42L, "emb", false, 100L));

        verify(transaction).rollbackToSavepoint(savepoint);
        verify(transaction).releaseSavepoint(savepoint);
        verify(transactionManager).commit(transaction);
        verify(transactionManager, never()).rollback(any());
        assertFailedIndex(11L);
        assertFailedIndex(12L);
        assertEquals(Map.of(10L, "old-success"), indexedPoints);
        assertFalse(savedIndexes.containsKey(10L));
        verify(vectors).deleteSegmentVectors(7L, 42L, List.of(11L, 12L), null);
        verify(vectors, never()).deleteMaterialSegments(any(), any());
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getValue().getPropagationBehavior());
    }

    @Test
    void aTimedOutWriteIsCleanedEvenWhenTheVectorStoreAppliedItBeforeThrowing() {
        doAnswer(invocation -> {
            List<VectorStoreService.MaterialSegmentVector> batch = invocation.getArgument(0);
            batch.forEach(point -> indexedPoints.put(point.segmentId(), point.attemptId()));
            throw new BusinessException("写入响应超时");
        }).when(vectors).upsertMaterialSegments(anyList());

        assertThrows(BusinessException.class, () -> service.prepareMaterialEmbedding(7L, 42L, "emb", true, 100L));

        assertTrue(indexedPoints.isEmpty());
        assertFailedIndex(10L);
        verify(transactionManager).commit(transaction);
    }

    @Test
    void cleanupFailureStillCommitsFailedStateAndReportsResidualVectorsToTheTask() {
        when(embeddings.embedTexts(anyList(), eq("emb"))).thenThrow(new BusinessException("模型失败"));
        doThrow(new BusinessException("Qdrant 不可达")).when(vectors)
                .deleteSegmentVectors(7L, 42L, List.of(10L), null);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.prepareMaterialEmbedding(7L, 42L, "emb", true, 100L));

        assertTrue(failure.getMessage().contains("向量清理未完成"));
        assertTrue(failure.getMessage().contains("可能残留"));
        assertTrue(failure.getMessage().contains("重试"));
        assertFailedIndex(10L);
        verify(transactionManager).commit(transaction);
    }

    @Test
    void aSuccessfulDatabaseCommitFailureIsCompensatedInANewTransaction() {
        AtomicInteger commits = new AtomicInteger();
        doAnswer(invocation -> {
            if (commits.getAndIncrement() == 0) throw new TransactionSystemException("提交失败");
            return null;
        }).when(transactionManager).commit(transaction);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.prepareMaterialEmbedding(7L, 42L, "emb", true, 100L));

        assertTrue(failure.getMessage().contains("数据库提交未完成"));
        assertFailedIndex(10L);
        assertTrue(indexedPoints.isEmpty());
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(2)).commit(transaction);
        verify(materials, times(2)).selectOne(any());
    }

    @Test
    void commitRollbackCanRecoverTheOriginalIndexStateBeforeMarkingTheAttemptFailed() {
        MaterialSegment original = new MaterialSegment();
        original.setId(segment.getId());
        original.setMaterialId(segment.getMaterialId());
        original.setEmbeddingStatus(segment.getEmbeddingStatus());
        original.setEmbeddingModel(segment.getEmbeddingModel());
        original.setEmbeddingMode(segment.getEmbeddingMode());
        original.setVectorId(segment.getVectorId());
        original.setEmbeddedAt(segment.getEmbeddedAt());
        AtomicInteger commits = new AtomicInteger();
        doAnswer(invocation -> {
            if (commits.getAndIncrement() == 0) {
                when(segments.selectList(any())).thenReturn(List.of(original));
                throw new TransactionSystemException("事务已回滚");
            }
            return null;
        }).when(transactionManager).commit(transaction);

        assertThrows(BusinessException.class, () -> service.prepareMaterialEmbedding(7L, 42L, "emb", true, 100L));

        assertFailedIndex(10L);
        assertTrue(indexedPoints.isEmpty());
        assertEquals("FAILED", original.getEmbeddingStatus());
    }

    @Test
    void databaseWriteFailureRollsBackPartialIndexStateAndPersistsFailedBeforeThrowing() {
        AtomicInteger writes = new AtomicInteger();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 2) throw new BusinessException("成功状态保存失败");
            return 1;
        }).when(segments).update(isNull(), any());

        assertThrows(BusinessException.class, () -> service.prepareMaterialEmbedding(7L, 42L, "emb", true, 100L));

        assertEquals("FAILED", segment.getEmbeddingStatus());
        assertNull(segment.getVectorId());
        assertNull(segment.getEmbeddedAt());
        assertTrue(indexedPoints.isEmpty());
        verify(transaction).rollbackToSavepoint(savepoint);
        verify(transactionManager).commit(transaction);
    }

    @Test
    void commitCompensationDoesNotInvalidateANewerAttemptForTheSameTaskId() {
        AtomicInteger commits = new AtomicInteger();
        doAnswer(invocation -> {
            if (commits.getAndIncrement() == 0) {
                segment.setEmbeddingAttemptId("newer-attempt");
                segment.setEmbeddingStatus("SUCCESS");
                segment.setEmbeddingTaskId(100L);
                indexedPoints.put(10L, "newer-attempt");
                throw new TransactionSystemException("提交失败");
            }
            return null;
        }).when(transactionManager).commit(transaction);

        assertThrows(BusinessException.class, () -> service.prepareMaterialEmbedding(7L, 42L, "emb", true, 100L));

        assertEquals("newer-attempt", segment.getEmbeddingAttemptId());
        assertEquals("SUCCESS", segment.getEmbeddingStatus());
        assertEquals(Map.of(10L, "newer-attempt"), indexedPoints);
        verify(vectors, never()).deleteSegmentVectors(eq(7L), eq(42L), anyList(), isNull());
        assertEquals("SUCCESS", savedIndexes.get(10L).get("embedding_status"));
    }

    @Test
    void compensationFailureIsReportedWithoutClaimingIndexOrStatusRecoverySucceeded() {
        doThrow(new TransactionSystemException("提交失败")).when(transactionManager).commit(transaction);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.prepareMaterialEmbedding(7L, 42L, "emb", true, 100L));

        assertTrue(failure.getMessage().contains("失败状态保存未完成"));
        assertTrue(failure.getMessage().contains("不能确认索引已清理"));
        verify(transactionManager, times(2)).commit(transaction);
    }

    private MaterialSegment pending(Long id) {
        MaterialSegment value = new MaterialSegment();
        value.setId(id);
        value.setMaterialId(42L);
        value.setContentText("正文 " + id);
        value.setEmbeddingStatus("PENDING");
        return value;
    }

    private TextEmbeddingService.EmbeddingBatchResult batch() {
        return new TextEmbeddingService.EmbeddingBatchResult("emb", List.of(List.of(0.1D)), false);
    }

    private void assertFailedIndex(Long id) {
        Map<String, Object> saved = savedIndexes.get(id);
        assertNotNull(saved);
        assertEquals("FAILED", saved.get("embedding_status"));
        assertEquals("UNKNOWN", saved.get("embedding_mode"));
        assertEquals(100L, saved.get("embedding_task_id"));
        assertNotNull(saved.get("embedding_attempt_id"));
        assertTrue(saved.containsKey("vector_id"));
        assertNull(saved.get("vector_id"));
        assertTrue(saved.containsKey("embedded_at"));
        assertNull(saved.get("embedded_at"));
    }
}
