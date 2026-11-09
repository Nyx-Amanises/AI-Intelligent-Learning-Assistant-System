package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.util.MaterialTextChunker;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.dto.material.MaterialPageQuery;
import com.aiassistant.learning.dto.material.MaterialParseRequest;
import com.aiassistant.learning.dto.material.MaterialUpdateRequest;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.entity.StudyResource;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.VectorStoreService;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * StudyMaterialServiceImpl 单元测试：文本资料创建、TXT 解析切片、重命名 / 删除、助手搜索排序与向量状态汇总。
 *
 * <p>ServiceImpl 自带的 save / getOne / updateById 等方法依赖真实 Mapper，这里用 spy 替换掉它们。</p>
 */
class StudyMaterialServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long MATERIAL_ID = 42L;

    private final MaterialSegmentMapper segments = mock(MaterialSegmentMapper.class);
    private final StudyMaterialMapper materials = mock(StudyMaterialMapper.class);
    private final FileStorageProperties storage = new FileStorageProperties();
    private final VectorStoreService vectorStore = mock(VectorStoreService.class);
    private final AiTaskService aiTaskService = mock(AiTaskService.class);
    private final AiConfigService aiConfigService = mock(AiConfigService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final Object parseSavepoint = new Object();
    private final StudyMaterialServiceImpl service = spy(new StudyMaterialServiceImpl(
            segments, storage, vectorStore, aiTaskService, aiConfigService, transactionManager));

    /** 记录 insert 进来的分段，模拟数据库。 */
    private final List<MaterialSegment> insertedSegments = new ArrayList<>();

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "material-service-test");
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
        TableInfoHelper.initTableInfo(assistant, MaterialSegment.class);
    }

    @BeforeEach
    void setUp() {
        doReturn(materials).when(service).getBaseMapper();
        storage.setUploadDir(temporaryDirectory.toString());
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(transactionStatus.createSavepoint()).thenReturn(parseSavepoint);
        when(segments.insert(any(MaterialSegment.class))).thenAnswer(invocation -> {
            MaterialSegment segment = invocation.getArgument(0);
            segment.setId((long) insertedSegments.size() + 1);
            insertedSegments.add(segment);
            return 1;
        });
        when(segments.selectMaps(any())).thenReturn(List.of());
        when(segments.selectList(any())).thenReturn(List.of());
        doReturn(true).when(service).updateById(any(StudyMaterial.class));
        doReturn(true).when(service).update(any(Wrapper.class));
        doReturn(true).when(service).removeById(any(Long.class));
        doAnswer(invocation -> {
            StudyMaterial material = invocation.getArgument(0);
            material.setId(MATERIAL_ID);
            return true;
        }).when(service).save(any(StudyMaterial.class));
        when(aiConfigService.getResolvedConfig(USER_ID)).thenReturn(config(true, false));
    }

    @Test
    void createTextMaterialStoresOneSegmentAndDispatchesTheAutoSummaryTask() {
        MaterialCreateRequest request = new MaterialCreateRequest();
        request.setTitle("光合作用");
        request.setMaterialType("TEXT");
        request.setContentText("光合作用是植物把光能转化为化学能的过程。");

        Long materialId = service.createTextMaterial(USER_ID, request);

        assertEquals(MATERIAL_ID, materialId);
        ArgumentCaptor<StudyMaterial> saved = ArgumentCaptor.forClass(StudyMaterial.class);
        verify(service).save(saved.capture());
        assertEquals("MANUAL", saved.getValue().getSourceType());
        assertEquals("SUCCESS", saved.getValue().getParseStatus());
        assertEquals(3, saved.getValue().getDifficultyLevel());
        assertEquals(request.getContentText().length(), saved.getValue().getTotalCharacters());
        assertEquals(request.getContentText(), saved.getValue().getSourceText());
        assertEquals("CHAR_WINDOW_V2", saved.getValue().getChunkStrategy());
        assertEquals(900, saved.getValue().getChunkSize());
        assertEquals(140, saved.getValue().getChunkOverlap());
        assertEquals(MaterialTextChunker.contentHash(request.getContentText()), saved.getValue().getContentHash());
        assertEquals(1, insertedSegments.size());
        assertEquals(MATERIAL_ID, insertedSegments.get(0).getMaterialId());
        assertTrue(insertedSegments.get(0).getSectionTitle().startsWith("第1段 · 光合作用"));
        assertEquals("PENDING", insertedSegments.get(0).getEmbeddingStatus());
        verify(aiTaskService).submitSummaryTask(eq(USER_ID), eq(MATERIAL_ID), any(SummaryGenerateRequest.class));
        verify(aiTaskService, never()).submitEmbeddingTask(any(), any(), any());
    }

    @Test
    void parseMaterialSplitsATextFileIntoHeadedSegments() throws Exception {
        Path file = temporaryDirectory.resolve("cell.txt");
        StringBuilder text = new StringBuilder("第一章 细胞的结构\n");
        for (int index = 0; index < 20; index++) {
            text.append("细胞膜由磷脂双分子层和蛋白质构成，具有选择透过性，能够控制物质进出细胞。这一段用来把分段长度撑过阈值。\n");
        }
        text.append("第二章 细胞的代谢\n");
        for (int index = 0; index < 6; index++) {
            text.append("酶是活细胞产生的具有催化作用的有机物，绝大多数是蛋白质，少数是 RNA。这一段同样用于填充长度。\n");
        }
        Files.writeString(file, text, StandardCharsets.UTF_8);
        StudyMaterial material = material(MATERIAL_ID, "细胞", "PENDING");
        material.setMaterialType("TXT");
        material.setFileUrl(file.toString());
        doReturn(material).when(service).getOne(any());

        service.parseMaterial(USER_ID, MATERIAL_ID);

        assertEquals("SUCCESS", material.getParseStatus());
        assertEquals(text.toString().trim().length(), material.getTotalCharacters());
        verify(vectorStore, never()).deleteMaterialSegments(any(), any());
        verify(vectorStore, never()).deleteSegmentVectors(any(), any(), anyList(), any());
        verify(segments).delete(any());
        assertTrue(insertedSegments.size() >= 3, "长文本应该被切成多段");
        assertEquals("第1段 · 第一章 细胞的结构", insertedSegments.get(0).getSectionTitle());
        assertTrue(insertedSegments.get(0).getContentText().startsWith("第一章 细胞的结构"));
        assertTrue(insertedSegments.get(1).getSectionTitle().endsWith("第一章 细胞的结构"), "同一章的后续分段沿用章节标题");
        assertTrue(insertedSegments.get(insertedSegments.size() - 1).getSectionTitle().endsWith("第二章 细胞的代谢"));
        for (int index = 0; index < insertedSegments.size(); index++) {
            MaterialSegment segment = insertedSegments.get(index);
            assertEquals(index + 1, segment.getSegmentNo());
            assertTrue(segment.getContentText().length() <= 900, "每段包含重叠后的总长度也不能超过900字符");
            assertEquals("PENDING", segment.getEmbeddingStatus());
        }
        verify(aiTaskService).submitSummaryTask(eq(USER_ID), eq(MATERIAL_ID), any(SummaryGenerateRequest.class));
    }

    @Test
    void customChunkSettingsActuallyChangeLongSingleLineFileSegments() throws Exception {
        Path file = temporaryDirectory.resolve("long-line.txt");
        String text = "长行正文内容".repeat(200);
        Files.writeString(file, text, StandardCharsets.UTF_8);
        StudyMaterial material = material(MATERIAL_ID, "长行", "SUCCESS");
        material.setMaterialType("TXT");
        material.setFileUrl(file.toString());
        doReturn(material).when(service).getOne(any());
        MaterialParseRequest request = new MaterialParseRequest();
        request.setChunkSize(200);
        request.setChunkOverlap(0);

        service.parseMaterial(USER_ID, MATERIAL_ID, request);

        assertTrue(insertedSegments.size() >= 6);
        assertTrue(insertedSegments.stream().allMatch(segment -> segment.getContentText().length() <= 200));
        assertEquals(text, String.join("", insertedSegments.stream().map(MaterialSegment::getContentText).toList()));
        assertEquals(200, material.getChunkSize());
        assertEquals(0, material.getChunkOverlap());
        assertEquals("CHAR_WINDOW_V2", material.getChunkStrategy());
        assertEquals(MaterialTextChunker.contentHash(text), material.getContentHash());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void manualTextRechunkUsesCompleteSavedSourceRatherThanOverlappingOldSegments() {
        String text = "原始正文".repeat(220);
        StudyMaterial material = material(MATERIAL_ID, "手工文本", "SUCCESS");
        material.setMaterialType("TEXT");
        material.setSourceType("MANUAL");
        material.setSourceText(text);
        doReturn(material).when(service).getOne(any());
        MaterialParseRequest request = new MaterialParseRequest();
        request.setChunkSize(200);
        request.setChunkOverlap(0);

        service.parseMaterial(USER_ID, MATERIAL_ID, request);

        assertEquals(text, String.join("", insertedSegments.stream().map(MaterialSegment::getContentText).toList()));
        assertEquals(MaterialTextChunker.contentHash(text), material.getContentHash());
        ArgumentCaptor<Wrapper<MaterialSegment>> oldSegments = ArgumentCaptor.forClass(Wrapper.class);
        verify(segments).selectList(oldSegments.capture());
        assertEquals("id", ((LambdaQueryWrapper<MaterialSegment>) oldSegments.getValue()).getSqlSelect());
        assertTrue(oldSegments.getValue().getSqlSegment().contains("material_id ="));
        verify(vectorStore, never()).deleteMaterialSegments(any(), any());
        verify(vectorStore, never()).deleteSegmentVectors(any(), any(), anyList(), any());
    }

    @Test
    void historicalManualTextWithoutSourceIsNotReconstructedFromSegments() {
        StudyMaterial material = material(MATERIAL_ID, "旧资料", "SUCCESS");
        material.setMaterialType("TEXT");
        material.setSourceType("MANUAL");
        doReturn(material).when(service).getOne(any());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.parseMaterial(USER_ID, MATERIAL_ID, new MaterialParseRequest()));

        assertTrue(exception.getMessage().contains("未保存完整原文"));
        assertEquals("SUCCESS", material.getParseStatus());
        verify(segments, never()).delete(any());
        verify(vectorStore, never()).deleteMaterialSegments(any(), any());
        verify(vectorStore, never()).deleteSegmentVectors(any(), any(), anyList(), any());
    }

    @Test
    void invalidChunkSettingsFailBeforeChangingMaterialOrIndex() {
        MaterialParseRequest request = new MaterialParseRequest();
        request.setChunkSize(200);
        request.setChunkOverlap(200);

        assertThrows(BusinessException.class, () -> service.parseMaterial(USER_ID, MATERIAL_ID, request));
        verify(service, never()).getOne(any());
        verify(service, never()).updateById(any(StudyMaterial.class));
        verify(vectorStore, never()).deleteMaterialSegments(any(), any());
        verify(vectorStore, never()).deleteSegmentVectors(any(), any(), anyList(), any());
    }

    @Test
    void importedContextCannotBeSilentlyRechunkedAsManualText() {
        StudyMaterial material = material(MATERIAL_ID, "CMRC2018", "SUCCESS");
        material.setSourceType("IMPORT");
        material.setChunkStrategy("ORIGINAL_CONTEXT");
        doReturn(material).when(service).getOne(any());

        assertThrows(BusinessException.class, () -> service.parseMaterial(USER_ID, MATERIAL_ID));

        assertEquals("ORIGINAL_CONTEXT", material.getChunkStrategy());
        verify(segments, never()).delete(any());
        verify(vectorStore, never()).deleteMaterialSegments(any(), any());
        verify(vectorStore, never()).deleteSegmentVectors(any(), any(), anyList(), any());
    }

    @Test
    void failedSegmentWriteDoesNotClearThePreviousExternalIndex() throws Exception {
        Path file = temporaryDirectory.resolve("valid.txt");
        Files.writeString(file, "有效正文".repeat(100), StandardCharsets.UTF_8);
        StudyMaterial material = material(MATERIAL_ID, "旧资料", "SUCCESS");
        material.setMaterialType("TXT");
        material.setFileUrl(file.toString());
        doReturn(material).when(service).getOne(any());
        when(segments.insert(any(MaterialSegment.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(BusinessException.class, () -> service.parseMaterial(USER_ID, MATERIAL_ID));

        verify(vectorStore, never()).deleteMaterialSegments(any(), any());
        verify(vectorStore, never()).deleteSegmentVectors(any(), any(), anyList(), any());
        verify(aiTaskService, never()).submitSummaryTask(any(), any(), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void indexCleanupRunsOnlyAfterCommitAndDeletesOnlyTheReplacedSegments() throws Exception {
        Path file = temporaryDirectory.resolve("valid.txt");
        Files.writeString(file, "有效正文".repeat(100), StandardCharsets.UTF_8);
        StudyMaterial material = material(MATERIAL_ID, "旧资料", "SUCCESS");
        material.setMaterialType("TXT");
        material.setFileUrl(file.toString());
        doReturn(material).when(service).getOne(any());
        MaterialSegment firstOldSegment = new MaterialSegment();
        firstOldSegment.setId(101L);
        MaterialSegment secondOldSegment = new MaterialSegment();
        secondOldSegment.setId(102L);
        when(segments.selectList(any())).thenReturn(List.of(firstOldSegment, secondOldSegment));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.parseMaterial(USER_ID, MATERIAL_ID);
            verify(vectorStore, never()).deleteMaterialSegments(any(), any());
            verify(vectorStore, never()).deleteSegmentVectors(any(), any(), anyList(), any());
            var order = inOrder(service, segments);
            ArgumentCaptor<Wrapper<StudyMaterial>> lock = ArgumentCaptor.forClass(Wrapper.class);
            order.verify(service).getOne(lock.capture());
            assertTrue(lock.getValue().getSqlSegment().endsWith("limit 1 for update"));
            order.verify(segments).selectList(any());
            order.verify(segments).delete(any());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(vectorStore).deleteSegmentVectors(USER_ID, MATERIAL_ID, List.of(101L, 102L), null);
            verify(vectorStore, never()).deleteMaterialSegments(any(), any());
            assertFalse(insertedSegments.isEmpty());
            assertTrue(insertedSegments.stream().noneMatch(segment -> List.of(101L, 102L).contains(segment.getId())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadDatabaseFailureIsNotSwallowedAsACompletedUpload() {
        when(segments.insert(any(MaterialSegment.class))).thenThrow(new IllegalStateException("database unavailable"));
        MockMultipartFile file = new MockMultipartFile("file", "valid.txt", "text/plain",
                "有效正文".repeat(100).getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> service.uploadMaterial(USER_ID, file));

        verify(vectorStore, never()).deleteMaterialSegments(any(), any());
        verify(vectorStore, never()).deleteSegmentVectors(any(), any(), anyList(), any());
        verify(aiTaskService, never()).submitSummaryTask(any(), any(), any());
    }

    @Test
    void parseMaterialMarksTheMaterialFailedWhenTheFileIsMissing() {
        StudyMaterial material = material(MATERIAL_ID, "细胞", "PENDING");
        material.setMaterialType("TXT");
        material.setFileUrl(temporaryDirectory.resolve("missing.txt").toString());
        doReturn(material).when(service).getOne(any());

        assertThrows(BusinessException.class, () -> service.parseMaterial(USER_ID, MATERIAL_ID));

        assertEquals("FAILED", material.getParseStatus());
        verify(segments, never()).delete(any());
        verify(vectorStore, never()).deleteMaterialSegments(any(), any());
        verify(vectorStore, never()).deleteSegmentVectors(any(), any(), anyList(), any());
        verify(segments, never()).insert(any(MaterialSegment.class));
        verify(aiTaskService, never()).submitSummaryTask(any(), any(), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void parsingFailureCommitsOnlyItsFailureFieldsBeforeItReachesTheCaller() {
        StudyMaterial material = uploadedMaterial("missing.txt");
        material.setChunkStrategy("EXISTING_CHUNKS");
        material.setContentHash("old-content-hash");
        doReturn(material).when(service).getOne(any());
        List<String> events = new ArrayList<>();
        doAnswer(invocation -> { events.add("failure-written"); return true; }).when(service).update(any(Wrapper.class));
        doAnswer(invocation -> { events.add("commit"); return null; }).when(transactionManager).commit(transactionStatus);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.parseMaterial(USER_ID, MATERIAL_ID));
        events.add("caller-received-error");

        assertTrue(failure.getMessage().contains("原始文件不存在"));
        assertEquals(List.of("failure-written", "commit", "caller-received-error"), events);
        assertEquals("FAILED", material.getParseStatus());
        assertNotNull(material.getParseAttemptId());
        assertEquals("EXISTING_CHUNKS", material.getChunkStrategy());
        assertEquals("old-content-hash", material.getContentHash());
        var order = inOrder(transactionStatus, service, transactionManager);
        order.verify(transactionStatus).createSavepoint();
        order.verify(service).updateById(material);
        order.verify(transactionStatus).rollbackToSavepoint(parseSavepoint);
        order.verify(transactionStatus).releaseSavepoint(parseSavepoint);
        order.verify(service).update(any(Wrapper.class));
        order.verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
        ArgumentCaptor<Wrapper<StudyMaterial>> writes = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(writes.capture());
        LambdaUpdateWrapper<StudyMaterial> update = (LambdaUpdateWrapper<StudyMaterial>) writes.getValue();
        assertEquals(Set.of("parse_status", "parse_attempt_id", "parse_error_message"), assignedColumns(update));
        assertTrue(update.getSqlSegment().contains("user_id ="));
        assertTrue(update.getParamNameValuePairs().containsValue(USER_ID));
        assertTrue(update.getParamNameValuePairs().containsValue(MATERIAL_ID));
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getValue().getPropagationBehavior());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void savepointRollbackRestoresOldSegmentsAndMetadataAfterAPartialReplacement() throws Exception {
        Files.writeString(temporaryDirectory.resolve("replace.txt"), "新正文".repeat(200), StandardCharsets.UTF_8);
        StudyMaterial original = uploadedMaterial("replace.txt");
        original.setChunkStrategy("OLD_STRATEGY"); original.setChunkSize(900); original.setChunkOverlap(140);
        original.setContentHash("original-hash"); original.setTotalCharacters(999);
        AtomicReference<StudyMaterial> databaseMaterial = new AtomicReference<>(copyMaterial(original));
        List<String> databaseSegments = new ArrayList<>(List.of("旧分段一", "旧分段二"));
        AtomicReference<List<String>> savedSegments = new AtomicReference<>();
        AtomicReference<StudyMaterial> savedMaterial = new AtomicReference<>();
        doAnswer(invocation -> copyMaterial(databaseMaterial.get())).when(service).getOne(any());
        when(transactionStatus.createSavepoint()).thenAnswer(invocation -> {
            savedMaterial.set(copyMaterial(databaseMaterial.get()));
            savedSegments.set(List.copyOf(databaseSegments));
            return parseSavepoint;
        });
        doAnswer(invocation -> {
            databaseMaterial.set(copyMaterial(invocation.getArgument(0)));
            return true;
        }).when(service).updateById(any(StudyMaterial.class));
        when(segments.delete(any())).thenAnswer(invocation -> { databaseSegments.clear(); return 2; });
        AtomicInteger inserted = new AtomicInteger();
        when(segments.insert(any(MaterialSegment.class))).thenAnswer(invocation -> {
            if (inserted.incrementAndGet() == 2) throw new IllegalStateException("second segment insert failed");
            databaseSegments.add(((MaterialSegment) invocation.getArgument(0)).getContentText());
            return 1;
        });
        doAnswer(invocation -> {
            databaseSegments.clear(); databaseSegments.addAll(savedSegments.get());
            databaseMaterial.set(copyMaterial(savedMaterial.get()));
            return null;
        }).when(transactionStatus).rollbackToSavepoint(parseSavepoint);
        doAnswer(invocation -> {
            LambdaUpdateWrapper<StudyMaterial> write = (LambdaUpdateWrapper<StudyMaterial>) invocation.getArgument(0);
            assertEquals(Set.of("parse_status", "parse_attempt_id", "parse_error_message"), assignedColumns(write));
            Map<String, Object> values = assignedValues(write);
            databaseMaterial.get().setParseStatus((String) values.get("parse_status"));
            databaseMaterial.get().setParseAttemptId((String) values.get("parse_attempt_id"));
            databaseMaterial.get().setParseErrorMessage((String) values.get("parse_error_message"));
            return true;
        }).when(service).update(any(Wrapper.class));
        MaterialParseRequest request = new MaterialParseRequest();
        request.setChunkSize(200); request.setChunkOverlap(0);

        assertThrows(BusinessException.class, () -> service.parseMaterial(USER_ID, MATERIAL_ID, request));

        assertEquals(2, inserted.get());
        assertEquals(List.of("旧分段一", "旧分段二"), databaseSegments);
        assertEquals("FAILED", databaseMaterial.get().getParseStatus());
        assertEquals("OLD_STRATEGY", databaseMaterial.get().getChunkStrategy());
        assertEquals("original-hash", databaseMaterial.get().getContentHash());
        assertEquals(999, databaseMaterial.get().getTotalCharacters());
        assertEquals(900, databaseMaterial.get().getChunkSize());
        verify(transactionManager).commit(transactionStatus);
        verify(vectorStore, never()).deleteMaterialSegments(any(), any());
        verify(vectorStore, never()).deleteSegmentVectors(any(), any(), anyList(), any());
        verify(aiTaskService, never()).submitSummaryTask(any(), any(), any());
    }

    @Test
    void schemaFailureAndFailedCompensationReportThatFailureStateCouldNotBeSaved() {
        StudyMaterial material = uploadedMaterial("missing.txt");
        doAnswer(invocation -> copyMaterial(material)).when(service).getOne(any());
        IllegalStateException schemaFailure = new IllegalStateException("unknown column parse_error_message");
        doThrow(schemaFailure).when(service).updateById(any(StudyMaterial.class));
        doThrow(schemaFailure).when(service).update(any(Wrapper.class));

        BusinessException error = assertThrows(BusinessException.class, () -> service.parseMaterial(USER_ID, MATERIAL_ID));

        assertTrue(error.getMessage().contains("失败状态保存未完成"));
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(2)).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
        verify(segments, never()).delete(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"previous-attempt"})
    @SuppressWarnings({"unchecked", "rawtypes"})
    void commitFailureCanCompensateOnlyTheSameOrPreviousAttempt(String previousAttempt) {
        StudyMaterial original = uploadedMaterial("missing.txt");
        original.setParseAttemptId(previousAttempt);
        doAnswer(invocation -> copyMaterial(original)).when(service).getOne(any());
        TransactionStatus recovery = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus, recovery);
        IllegalStateException commitFailure = new IllegalStateException("connection lost during commit");
        doThrow(commitFailure).when(transactionManager).commit(transactionStatus);

        assertSame(commitFailure, assertThrows(IllegalStateException.class, () -> service.parseMaterial(USER_ID, MATERIAL_ID)));

        verify(transactionManager).commit(recovery);
        ArgumentCaptor<Wrapper<StudyMaterial>> writes = ArgumentCaptor.forClass(Wrapper.class);
        verify(service, times(2)).update(writes.capture());
        Map<String, Object> first = assignedValues((LambdaUpdateWrapper<StudyMaterial>) writes.getAllValues().get(0));
        Map<String, Object> second = assignedValues((LambdaUpdateWrapper<StudyMaterial>) writes.getAllValues().get(1));
        assertEquals(first.get("parse_attempt_id"), second.get("parse_attempt_id"));
        assertEquals("FAILED", second.get("parse_status"));
        assertTrue(second.get("parse_error_message").toString().contains("文件读取或分段保存失败"));
    }

    @Test
    void compensationAfterCommitFailureCannotOverwriteANewerParseAttempt() {
        StudyMaterial older = uploadedMaterial("missing.txt");
        older.setParseAttemptId("older-attempt");
        StudyMaterial newer = copyMaterial(older);
        newer.setParseAttemptId("newer-attempt"); newer.setParseStatus("SUCCESS"); newer.setContentHash("newer-hash");
        doReturn(older, newer).when(service).getOne(any());
        TransactionStatus recovery = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus, recovery);
        IllegalStateException commitFailure = new IllegalStateException("unknown commit outcome");
        doThrow(commitFailure).when(transactionManager).commit(transactionStatus);

        assertSame(commitFailure, assertThrows(IllegalStateException.class, () -> service.parseMaterial(USER_ID, MATERIAL_ID)));

        verify(service, times(1)).update(any(Wrapper.class));
        verify(transactionManager).commit(recovery);
        assertEquals("newer-attempt", newer.getParseAttemptId());
        assertEquals("SUCCESS", newer.getParseStatus());
        assertEquals("newer-hash", newer.getContentHash());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void successfulRetryClearsTheOldFailureMessageAndCommits() throws Exception {
        Files.writeString(temporaryDirectory.resolve("retry.txt"), "重新解析的正文", StandardCharsets.UTF_8);
        StudyMaterial material = uploadedMaterial("retry.txt");
        material.setParseStatus("FAILED"); material.setParseErrorMessage("上次解析失败"); material.setParseAttemptId("old");
        doReturn(material).when(service).getOne(any());

        service.parseMaterial(USER_ID, MATERIAL_ID);

        assertEquals("SUCCESS", material.getParseStatus());
        assertEquals(null, material.getParseErrorMessage());
        ArgumentCaptor<Wrapper<StudyMaterial>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(update.capture());
        assertEquals(Set.of("parse_error_message"), assignedColumns((LambdaUpdateWrapper<StudyMaterial>) update.getValue()));
        assertTrue(assignedValues((LambdaUpdateWrapper<StudyMaterial>) update.getValue()).containsKey("parse_error_message"));
        assertEquals(null, assignedValues((LambdaUpdateWrapper<StudyMaterial>) update.getValue()).get("parse_error_message"));
        verify(transactionStatus, never()).rollbackToSavepoint(any());
        verify(transactionManager).commit(transactionStatus);
    }

    private StudyMaterial uploadedMaterial(String fileName) {
        StudyMaterial material = material(MATERIAL_ID, "已有资料", "SUCCESS");
        material.setSourceType("UPLOAD"); material.setMaterialType("TXT");
        material.setFileUrl(temporaryDirectory.resolve(fileName).toString());
        return material;
    }

    private StudyMaterial copyMaterial(StudyMaterial source) {
        StudyMaterial copy = new StudyMaterial();
        BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private Map<String, Object> assignedValues(LambdaUpdateWrapper<StudyMaterial> statement) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (String assignment : statement.getSqlSet().split(",")) {
            int separator = assignment.indexOf('=');
            String column = assignment.substring(0, separator).trim();
            String expression = assignment.substring(separator + 1).trim();
            if (expression.equals("null")) values.put(column, null);
            else {
                String parameter = expression.substring(expression.lastIndexOf('.') + 1, expression.indexOf('}'));
                values.put(column, statement.getParamNameValuePairs().get(parameter));
            }
        }
        return values;
    }

    @Test
    void renameMaterialTrimsTheTitleAndRenamesTheFirstSegment() {
        StudyMaterial material = material(MATERIAL_ID, "旧标题", "SUCCESS");
        doReturn(material).when(service).getOne(any());

        service.renameMaterial(USER_ID, MATERIAL_ID, "  新标题  ");

        assertEquals("新标题", material.getTitle());
        verify(service).updateById(material);
        verify(segments).update(isNull(), any());
    }

    @Test
    void renameMaterialRejectsBlankOrOverlongTitles() {
        doReturn(material(MATERIAL_ID, "旧标题", "SUCCESS")).when(service).getOne(any());

        assertThrows(BusinessException.class, () -> service.renameMaterial(USER_ID, MATERIAL_ID, " "));
        assertThrows(BusinessException.class, () -> service.renameMaterial(USER_ID, MATERIAL_ID, "标".repeat(201)));
        verify(service, never()).updateById(any(StudyMaterial.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deleteMaterialCleansVectorsAndSegmentsBeforeTheRow() {
        doReturn(material(MATERIAL_ID, "细胞", "SUCCESS")).when(service).getOne(any());

        service.deleteMaterial(USER_ID, MATERIAL_ID);

        var order = inOrder(service, vectorStore, segments);
        ArgumentCaptor<Wrapper<StudyMaterial>> lock = ArgumentCaptor.forClass(Wrapper.class);
        order.verify(service).getOne(lock.capture());
        assertTrue(lock.getValue().getSqlSegment().endsWith("limit 1 for update"));
        assertTrue(lock.getValue().getSqlSegment().contains("user_id ="));
        assertTrue(((LambdaQueryWrapper<StudyMaterial>) lock.getValue()).getParamNameValuePairs().containsValue(USER_ID));
        order.verify(vectorStore).deleteMaterialSegments(USER_ID, MATERIAL_ID);
        order.verify(segments).delete(any());
        order.verify(service).removeById(MATERIAL_ID);
    }

    @Test
    void materialsOfOtherUsersCannotBeRenamedOrDeleted() {
        doReturn(null).when(service).getOne(any());

        assertThrows(BusinessException.class, () -> service.renameMaterial(USER_ID, MATERIAL_ID, "标题"));
        assertThrows(BusinessException.class, () -> service.deleteMaterial(USER_ID, MATERIAL_ID));
        assertThrows(BusinessException.class, () -> service.updateMaterialInfo(USER_ID, MATERIAL_ID, updateRequest("标题")));
        verify(segments, never()).delete(any());
        verify(service, never()).removeById(any(Long.class));
        verify(service, never()).update(any(Wrapper.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateMaterialInfoWritesEditableColumnsAndNormalizesTags() {
        doReturn(material(MATERIAL_ID, "旧标题", "SUCCESS")).when(service).getOne(any());
        doReturn(true).when(service).update(any(Wrapper.class));
        MaterialUpdateRequest request = updateRequest("  新标题 ");
        request.setTags(" 期末复习，第三章、期末复习 ;重点 ");
        request.setDifficultyLevel(4);
        request.setDescription("  来源：人教版必修1 课件 ");

        service.updateMaterialInfo(USER_ID, MATERIAL_ID, request);

        ArgumentCaptor<Wrapper<StudyMaterial>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(update.capture());
        LambdaUpdateWrapper<StudyMaterial> statement = (LambdaUpdateWrapper<StudyMaterial>) update.getValue();
        assertEquals(Set.of("title", "tags", "description", "difficulty_level"), assignedColumns(statement));
        assertTrue(statement.getSqlSegment().contains("user_id ="));
        assertTrue(statement.getParamNameValuePairs().containsValue("新标题"));
        assertTrue(statement.getParamNameValuePairs().containsValue("期末复习,第三章,重点"));
        assertTrue(statement.getParamNameValuePairs().containsValue("来源：人教版必修1 课件"));
        assertTrue(statement.getParamNameValuePairs().containsValue(4));
        verify(segments).update(isNull(), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateMaterialInfoClearsBlankTagsAndKeepsDifficultyWhenOmitted() {
        doReturn(material(MATERIAL_ID, "标题", "SUCCESS")).when(service).getOne(any());
        doReturn(true).when(service).update(any(Wrapper.class));
        MaterialUpdateRequest request = updateRequest("标题");
        request.setTags(" ， ");
        request.setDescription("   ");

        service.updateMaterialInfo(USER_ID, MATERIAL_ID, request);

        ArgumentCaptor<Wrapper<StudyMaterial>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(update.capture());
        LambdaUpdateWrapper<StudyMaterial> statement = (LambdaUpdateWrapper<StudyMaterial>) update.getValue();
        assertEquals(Set.of("title", "tags", "description"), assignedColumns(statement));
        assertTrue(statement.getSqlSet().contains("tags=null") || statement.getParamNameValuePairs().containsValue(null));
        verify(segments, never()).update(isNull(), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pageKeywordSearchesTitleTagsAndDescription() {
        doReturn(new Page<StudyMaterial>(1, 10)).when(service).page(any(), any(Wrapper.class));
        MaterialPageQuery query = new MaterialPageQuery();
        query.setKeyword(" 期末 ");

        service.pageMaterials(USER_ID, query);

        ArgumentCaptor<Wrapper<StudyMaterial>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).page(any(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("title LIKE"));
        assertTrue(sql.contains("tags LIKE"));
        assertTrue(sql.contains("description LIKE"));
        assertTrue(((LambdaQueryWrapper<StudyMaterial>) wrapper.getValue()).getParamNameValuePairs().containsValue("%期末%"));
    }

    @Test
    void listAndDetailExposeVersionedCoverMetadataWithoutStoragePath() {
        StudyMaterial material = material(MATERIAL_ID, "资料封面", "SUCCESS");
        material.setCoverUrl("material-covers/" + "a".repeat(32) + ".jpg");
        doReturn(material).when(service).getOne(any());
        Page<StudyMaterial> page = new Page<>(1, 10);
        page.setRecords(List.of(material));
        page.setTotal(1);
        doReturn(page).when(service).page(any(), any(Wrapper.class));
        String expected = "/api/material/42/cover?v=" + "a".repeat(32);
        assertEquals(expected, service.getMaterialDetail(USER_ID, MATERIAL_ID).getCoverUrl());
        assertEquals(expected, service.pageMaterials(USER_ID, new MaterialPageQuery()).getRecords().get(0).getCoverUrl());
        material.setCoverUrl(null);
        assertEquals(null, service.getMaterialDetail(USER_ID, MATERIAL_ID).getCoverUrl());
    }

    @Test
    void detailReturnsPublishedDocumentWithoutLoadingTheFullText() {
        doReturn(material(MATERIAL_ID, "完整教材", "SUCCESS")).when(service).getOne(any());
        StudyResource source = new StudyResource();
        source.setId(15L);
        source.setDocumentUrl("/open-books/example/book.pdf");
        when(materials.findPublishedSourceDocument(USER_ID, MATERIAL_ID)).thenReturn(source);
        MaterialSegment segment = new MaterialSegment();
        segment.setId(101L);
        segment.setSegmentNo(1);
        segment.setContentText("总结页仍可读取的资料片段");
        when(segments.selectList(any())).thenReturn(List.of(segment));

        var result = service.getMaterialDetail(USER_ID, MATERIAL_ID);

        assertEquals(15L, result.getResourceId());
        assertEquals("/open-books/example/book.pdf", result.getDocumentUrl());
        assertEquals(null, result.getContentText());
        assertEquals(1, result.getSegments().size());
        assertEquals("总结页仍可读取的资料片段", result.getSegments().get(0).getContentText());
        verify(materials, never()).findOwnedSourceText(any(), any());
        verify(segments).selectList(any());
    }

    @Test
    void detailLoadsCompleteTextOnlyWhenNoPublishedDocumentIsAvailable() {
        doReturn(material(MATERIAL_ID, "课堂笔记", "SUCCESS")).when(service).getOne(any());
        when(materials.findOwnedSourceText(USER_ID, MATERIAL_ID)).thenReturn("第一行\n\n完整的正文。");

        var result = service.getMaterialDetail(USER_ID, MATERIAL_ID);

        assertEquals(null, result.getResourceId());
        assertEquals(null, result.getDocumentUrl());
        assertEquals("第一行\n\n完整的正文。", result.getContentText());
        verify(materials).findPublishedSourceDocument(USER_ID, MATERIAL_ID);
    }

    @Test
    void detailRejectsUnownedMaterialBeforeLookingUpItsSource() {
        doReturn(null).when(service).getOne(any());

        assertThrows(BusinessException.class, () -> service.getMaterialDetail(USER_ID, MATERIAL_ID));

        verify(materials, never()).findPublishedSourceDocument(any(), any());
        verify(materials, never()).findOwnedSourceText(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deletingMaterialCleansCoverOnlyAfterSuccessfulCommit(boolean commit) throws Exception {
        String key = "material-covers/" + "b".repeat(32) + ".jpg";
        Files.createDirectories(temporaryDirectory.resolve("material-covers"));
        Path cover = Files.write(temporaryDirectory.resolve(key), new byte[]{1, 2});
        StudyMaterial material = material(MATERIAL_ID, "资料封面", "SUCCESS");
        material.setCoverUrl(key);
        doReturn(material).when(service).getOne(any());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteMaterial(USER_ID, MATERIAL_ID);
            assertTrue(Files.exists(cover));
            if (commit) TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(
                    commit ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertEquals(!commit, Files.exists(cover));
    }

    @Test
    void deleteMaterialRemovesTheUploadedFileOnlyAfterCommit() throws Exception {
        Path stored = Files.writeString(temporaryDirectory.resolve("stored.pdf"), "%PDF-1.4");
        StudyMaterial material = material(MATERIAL_ID, "细胞", "SUCCESS");
        material.setFileUrl(stored.toString());
        doReturn(material).when(service).getOne(any());

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteMaterial(USER_ID, MATERIAL_ID);
            assertTrue(Files.exists(stored), "事务提交前不能删除文件");
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertFalse(Files.exists(stored));
    }

    @Test
    void deleteMaterialNeverTouchesFilesOutsideTheUploadDirectory(@TempDir Path elsewhere) throws Exception {
        Path outside = Files.writeString(elsewhere.resolve("important.txt"), "keep me");
        StudyMaterial material = material(MATERIAL_ID, "细胞", "SUCCESS");
        material.setFileUrl(outside.toString());
        doReturn(material).when(service).getOne(any());

        service.deleteMaterial(USER_ID, MATERIAL_ID);

        assertTrue(Files.exists(outside));
    }

    @Test
    void uploadRejectsFilesWhoseContentDoesNotMatchTheExtension() throws Exception {
        assertThrows(BusinessException.class, () -> service.uploadMaterial(USER_ID,
                new MockMultipartFile("file", "notes.pdf", "application/pdf", "just text".getBytes(StandardCharsets.UTF_8))));
        assertThrows(BusinessException.class, () -> service.uploadMaterial(USER_ID,
                new MockMultipartFile("file", "old.docx", "application/msword",
                        new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0})));
        assertThrows(BusinessException.class, () -> service.uploadMaterial(USER_ID,
                new MockMultipartFile("file", "utf16.txt", "text/plain", "study notes".getBytes(StandardCharsets.UTF_16LE))));

        verify(service, never()).save(any(StudyMaterial.class));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(0, files.count(), "被拒绝的文件不应写入上传目录");
        }
    }

    @Test
    void uploadAcceptsAPlainUtf8TextFile() {
        MockMultipartFile file = new MockMultipartFile("file", "细胞.txt", "text/plain",
                "第一章 细胞\n细胞是生命活动的基本单位。".getBytes(StandardCharsets.UTF_8));

        Long materialId = service.uploadMaterial(USER_ID, file);

        assertEquals(MATERIAL_ID, materialId);
        ArgumentCaptor<StudyMaterial> saved = ArgumentCaptor.forClass(StudyMaterial.class);
        verify(service).save(saved.capture());
        assertEquals("TXT", saved.getValue().getMaterialType());
        assertEquals("细胞", saved.getValue().getTitle());
        assertEquals("SUCCESS", saved.getValue().getParseStatus());
    }

    private MaterialUpdateRequest updateRequest(String title) {
        MaterialUpdateRequest request = new MaterialUpdateRequest();
        request.setTitle(title);
        return request;
    }

    private Set<String> assignedColumns(LambdaUpdateWrapper<StudyMaterial> statement) {
        return Arrays.stream(statement.getSqlSet().split(","))
                .map(assignment -> assignment.substring(0, assignment.indexOf('=')).trim())
                .collect(Collectors.toSet());
    }

    @Test
    void assistantSearchIgnoresPunctuationAndRanksExactTitlesFirst() {
        doReturn(List.of(
                material(1L, "Java核心知识全面梳理", "SUCCESS"),
                material(2L, "Java 核心知识", "SUCCESS"),
                material(3L, "Python 入门", "SUCCESS")
        )).when(service).list(any(Wrapper.class));

        List<MaterialPageVO> results = service.searchAssistantMaterials(USER_ID, "java核心知识", 10);

        assertEquals(List.of(2L, 1L), results.stream().map(MaterialPageVO::getId).toList());
        assertEquals("NOT_READY", results.get(0).getEmbeddingStatus());
    }

    @Test
    void embeddingStatusIsSummarisedFromSegmentStatistics() {
        doReturn(List.of(
                material(1L, "部分完成", "SUCCESS"),
                material(2L, "全部失败", "SUCCESS"),
                material(3L, "全部完成", "SUCCESS"),
                material(4L, "解析中", "PROCESSING")
        )).when(service).list(any(Wrapper.class));
        when(segments.selectMaps(any())).thenReturn(List.of(
                stats(1L, 4, 2, 0, 1),
                stats(2L, 3, 0, 3, 0),
                stats(3L, 2, 2, 0, 0)
        ));

        List<MaterialPageVO> results = service.searchAssistantMaterials(USER_ID, null, 10);

        assertEquals("PARTIAL", results.get(0).getEmbeddingStatus());
        assertEquals("FAILED", results.get(1).getEmbeddingStatus());
        assertEquals("SUCCESS", results.get(2).getEmbeddingStatus());
        assertEquals("PARSING", results.get(3).getEmbeddingStatus());
        assertEquals(2, results.get(0).getEmbeddedSegmentCount());
        assertEquals(4, results.get(0).getTotalSegmentCount());
        verify(segments, times(1)).selectMaps(any());
    }

    private Map<String, Object> stats(long materialId, int total, int embedded, int failed, int queued) {
        return Map.of(
                "material_id", materialId,
                "total_segments", total,
                "embedded_segments", embedded,
                "failed_segments", failed,
                "queued_segments", queued
        );
    }

    private StudyMaterial material(Long id, String title, String parseStatus) {
        StudyMaterial material = new StudyMaterial();
        material.setId(id);
        material.setUserId(USER_ID);
        material.setTitle(title);
        material.setMaterialType("TEXT");
        material.setParseStatus(parseStatus);
        return material;
    }

    private AiConfigService.ResolvedAiConfig config(boolean autoSummary, boolean autoEmbedding) {
        return new AiConfigService.ResolvedAiConfig(
                true, true, autoSummary, autoEmbedding, "OPENAI_COMPATIBLE", "https://example.test", "/v1/chat/completions",
                "OPENAI_COMPATIBLE", null, null, "test-key", null, "test-model", null);
    }
}
