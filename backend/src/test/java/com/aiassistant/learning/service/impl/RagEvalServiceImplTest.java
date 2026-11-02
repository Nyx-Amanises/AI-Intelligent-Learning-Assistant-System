package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.rag.RagEvalDatasetCreateRequest;
import com.aiassistant.learning.dto.rag.RagEvalRunRequest;
import com.aiassistant.learning.dto.rag.RagEvalRunPageQuery;
import com.aiassistant.learning.dto.rag.RagEvalSampleBatchCreateRequest;
import com.aiassistant.learning.dto.rag.RagEvalSampleCreateRequest;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.RagEvalDataset;
import com.aiassistant.learning.entity.RagEvalRun;
import com.aiassistant.learning.entity.RagEvalRunItem;
import com.aiassistant.learning.entity.RagEvalSample;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.mapper.RagEvalDatasetMapper;
import com.aiassistant.learning.mapper.RagEvalRunItemMapper;
import com.aiassistant.learning.mapper.RagEvalRunMapper;
import com.aiassistant.learning.mapper.RagEvalSampleMapper;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.RetrievalService.RetrievalHit;
import com.aiassistant.learning.service.RetrievalService.RetrievalResult;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.vo.rag.CmrcImportResultVO;
import com.aiassistant.learning.vo.rag.RagEvalDatasetVO;
import com.aiassistant.learning.vo.rag.RagEvalRunVO;
import com.aiassistant.learning.vo.rag.RagEvalSampleVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;

/**
 * RagEvalServiceImpl 单元测试：评测集与样本维护、检索指标计算、CMRC2018 导入。
 */
class RagEvalServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long MATERIAL_ID = 42L;
    private static final long DATASET_ID = 9L;

    private final RagEvalDatasetMapper datasets = mock(RagEvalDatasetMapper.class);
    private final RagEvalSampleMapper samples = mock(RagEvalSampleMapper.class);
    private final RagEvalRunMapper runs = mock(RagEvalRunMapper.class);
    private final RagEvalRunItemMapper runItems = mock(RagEvalRunItemMapper.class);
    private final MaterialSegmentMapper segments = mock(MaterialSegmentMapper.class);
    private final StudyMaterialService studyMaterialService = mock(StudyMaterialService.class);
    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final AiTaskService aiTaskService = mock(AiTaskService.class);
    private final RagEvalServiceImpl service = new RagEvalServiceImpl(
            datasets, samples, runs, runItems, segments, studyMaterialService, retrievalService, aiTaskService,
            new ObjectMapper());

    private final List<RagEvalSample> insertedSamples = new ArrayList<>();
    private final List<RagEvalRunItem> insertedRunItems = new ArrayList<>();
    private final AtomicLong idSequence = new AtomicLong(100);

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "rag-eval-service-test");
        TableInfoHelper.initTableInfo(assistant, RagEvalDataset.class);
        TableInfoHelper.initTableInfo(assistant, RagEvalSample.class);
        TableInfoHelper.initTableInfo(assistant, RagEvalRun.class);
        TableInfoHelper.initTableInfo(assistant, RagEvalRunItem.class);
        TableInfoHelper.initTableInfo(assistant, MaterialSegment.class);
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
    }

    @BeforeEach
    void setUp() {
        when(studyMaterialService.getOne(any())).thenReturn(material());
        when(datasets.selectOne(any())).thenReturn(dataset());
        when(segments.selectList(any())).thenReturn(List.of(storedSegment(11L, 1), storedSegment(12L, 3), storedSegment(50L, 5)));
        when(datasets.insert(any(RagEvalDataset.class))).thenAnswer(invocation -> {
            RagEvalDataset dataset = invocation.getArgument(0);
            dataset.setId(DATASET_ID);
            return 1;
        });
        when(samples.insert(any(RagEvalSample.class))).thenAnswer(invocation -> {
            RagEvalSample sample = invocation.getArgument(0);
            sample.setId(idSequence.getAndIncrement());
            insertedSamples.add(sample);
            return 1;
        });
        when(samples.selectCount(any())).thenAnswer(invocation -> (long) insertedSamples.size());
        when(runs.insert(any(RagEvalRun.class))).thenAnswer(invocation -> {
            RagEvalRun run = invocation.getArgument(0);
            run.setId(77L);
            return 1;
        });
        when(runItems.insert(any(RagEvalRunItem.class))).thenAnswer(invocation -> {
            RagEvalRunItem item = invocation.getArgument(0);
            item.setId(idSequence.getAndIncrement());
            insertedRunItems.add(item);
            return 1;
        });
        when(segments.insert(any(MaterialSegment.class))).thenAnswer(invocation -> {
            MaterialSegment segment = invocation.getArgument(0);
            segment.setId(idSequence.getAndIncrement());
            return 1;
        });
        doAnswer(invocation -> {
            StudyMaterial material = invocation.getArgument(0);
            material.setId(MATERIAL_ID);
            return true;
        }).when(studyMaterialService).save(any(StudyMaterial.class));
    }

    @Test
    void createDatasetBindsTheOwnedMaterialAndStartsEmpty() {
        RagEvalDatasetCreateRequest request = new RagEvalDatasetCreateRequest();
        request.setMaterialId(MATERIAL_ID);
        request.setName("  生物评测集 ");
        request.setDescription(" ");

        RagEvalDatasetVO created = service.createDataset(USER_ID, request);

        assertEquals(DATASET_ID, created.getId());
        assertEquals("生物评测集", created.getName());
        ArgumentCaptor<RagEvalDataset> saved = ArgumentCaptor.forClass(RagEvalDataset.class);
        verify(datasets).insert(saved.capture());
        assertEquals("ACTIVE", saved.getValue().getStatus());
        assertEquals(0, saved.getValue().getSampleCount());
        assertNull(saved.getValue().getDescription());
    }

    @Test
    void createDatasetRejectsMaterialsOfOtherUsers() {
        when(studyMaterialService.getOne(any())).thenReturn(null);
        RagEvalDatasetCreateRequest request = new RagEvalDatasetCreateRequest();
        request.setMaterialId(MATERIAL_ID);
        request.setName("评测集");

        assertThrows(BusinessException.class, () -> service.createDataset(USER_ID, request));
        verify(datasets, never()).insert(any(RagEvalDataset.class));
    }

    @Test
    void addSamplesRequiresAnAnnotationAndVerifiesSegmentOwnership() {
        when(segments.selectCount(any())).thenReturn(2L);

        List<RagEvalSampleVO> created = service.addSamples(USER_ID, DATASET_ID, batch(
                sample("为什么说细胞是生命活动的基本单位", List.of(11L, 12L), null),
                sample("酶的作用", null, List.of(3))
        ));

        assertEquals(2, created.size());
        assertEquals("[11,12]", insertedSamples.get(0).getExpectedSegmentIds());
        assertEquals("[3]", insertedSamples.get(1).getExpectedPageNos());
        assertEquals(3, insertedSamples.get(0).getDifficulty());
        assertEquals(List.of(11L, 12L), created.get(0).getExpectedSegmentIds());
        verify(datasets).updateById(any(RagEvalDataset.class));
    }

    @Test
    void addSamplesRejectsSamplesWithoutTargetsOrWithForeignSegments() {
        assertThrows(BusinessException.class,
                () -> service.addSamples(USER_ID, DATASET_ID, batch(sample("无标注", null, null))));
        assertThrows(BusinessException.class,
                () -> service.addSamples(USER_ID, DATASET_ID, batch(sample("页码非法", null, List.of(0)))));
        when(segments.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class,
                () -> service.addSamples(USER_ID, DATASET_ID, batch(sample("段落不属于资料", List.of(11L, 99L), null))));

        verify(samples, never()).insert(any(RagEvalSample.class));
    }

    @Test
    void deletingAnEvaluatedSamplePreservesHistoricalIdsSnapshotsMetricsAndComparisons() {
        RagEvalDataset dataset = dataset();
        dataset.setSampleCount(2);
        dataset.setLastRunId(72L);
        dataset.setLastRunAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        List<RagEvalSample> activeSamples = new ArrayList<>(List.of(
                storedSample(1L, "待删除的问题", "[11]", "[1]"),
                storedSample(2L, "保留的问题", "[12]", "[3]")));
        installOwnedSamples(dataset, activeSamples);
        List<RagEvalRun> history = List.of(comparableRun(71L), comparableRun(72L));
        for (RagEvalRun run : history) {
            run.setTotalSamples(1);
            run.setEvaluatedSamples(1);
            run.setFailedSamples(0);
            run.setHitAt1(0D);
            run.setHitAt3(1D);
            run.setHitAt5(1D);
            run.setRecallAt1(0D);
            run.setRecallAt3(1D);
            run.setRecallAt5(1D);
            run.setMrr(0.5D);
            run.setAvgLatencyMs(18D);
        }
        installOwnedRuns(history);
        List<RagEvalRunItem> historicalItems = history.stream().map(this::historicalItem).toList();
        doAnswer(invocation -> {
            LambdaQueryWrapper<RagEvalRunItem> query = invocation.getArgument(0);
            assertEquals(USER_ID, equality(query, "user_id"));
            Long runId = (Long) equality(query, "run_id");
            return historicalItems.stream().filter(item -> runId.equals(item.getRunId())).toList();
        }).when(runItems).selectList(any());
        var before = service.compareRuns(USER_ID, DATASET_ID, List.of(71L, 72L));

        service.deleteSample(USER_ID, DATASET_ID, 1L);

        assertEquals(List.of(2L), service.listSamples(USER_ID, DATASET_ID).stream().map(RagEvalSampleVO::getId).toList());
        assertEquals(1, dataset.getSampleCount());
        assertEquals(72L, dataset.getLastRunId());
        assertEquals(LocalDateTime.of(2026, 11, 1, 10, 0), dataset.getLastRunAt());
        var after = service.compareRuns(USER_ID, DATASET_ID, List.of(71L, 72L));
        assertTrue(after.isComparable());
        assertEquals(before, after, "删除活跃样本不能改变过去的运行指标、快照或对比结果");
        for (RagEvalRunVO run : after.getRuns()) {
            assertEquals(1, run.getTotalSamples());
            assertEquals(0.5D, run.getMrr());
            assertEquals(1L, run.getItems().get(0).getSampleId());
            assertEquals("评测时的问题", run.getItems().get(0).getQueryText());
            assertEquals(List.of(11L), run.getItems().get(0).getExpectedSegmentIds());
            assertEquals(List.of(12L, 11L), run.getItems().get(0).getRetrievedSegmentIds());
            assertEquals("评测时的原文", run.getItems().get(0).getRetrievedSnapshot().get(0).path("contentText").asText());
        }
        verify(samples).deleteById(1L);
        verify(datasets).updateById(dataset);
        verify(runs, never()).updateById(any(RagEvalRun.class));
        verify(runs, never()).delete(any());
        verify(runs, never()).deleteBatchIds(any());
        verify(runItems, never()).updateById(any(RagEvalRunItem.class));
        verify(runItems, never()).delete(any());
        verifyNoInteractions(retrievalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign-user", "foreign-dataset", "missing"})
    void deletingAnInaccessibleSampleIsRejectedWithoutChangingHistoryOrCount(String kind) {
        RagEvalDataset dataset = dataset();
        dataset.setSampleCount(1);
        RagEvalSample sample = storedSample(1L, "受保护的问题", "[11]", "[1]");
        if ("foreign-user".equals(kind)) sample.setUserId(USER_ID + 1);
        if ("foreign-dataset".equals(kind)) sample.setDatasetId(DATASET_ID + 1);
        List<RagEvalSample> activeSamples = new ArrayList<>("missing".equals(kind) ? List.of() : List.of(sample));
        installOwnedSamples(dataset, activeSamples);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteSample(USER_ID, DATASET_ID, 1L));

        assertEquals(404, exception.getCode());
        assertEquals("评测样本不存在", exception.getMessage());
        assertEquals("missing".equals(kind) ? 0 : 1, activeSamples.size());
        assertEquals(1, dataset.getSampleCount());
        verify(samples, never()).deleteById(any(Long.class));
        verify(samples, never()).selectCount(any());
        verify(datasets, never()).updateById(any(RagEvalDataset.class));
        verifyNoInteractions(runs, runItems);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign-user", "missing"})
    void deletingASampleRequiresAnOwnedExistingDatasetBeforeLookingUpTheSample(String kind) {
        RagEvalDataset dataset = dataset();
        if ("foreign-user".equals(kind)) dataset.setUserId(USER_ID + 1);
        if ("missing".equals(kind)) dataset.setId(DATASET_ID + 1);
        installOwnedSamples(dataset, new ArrayList<>(List.of(storedSample(1L, "问题", "[11]", "[1]"))));

        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.deleteSample(USER_ID, DATASET_ID, 1L)).getCode());

        verifyNoInteractions(samples, runs, runItems);
        verify(datasets, never()).updateById(any(RagEvalDataset.class));
    }

    @Test
    void aSampleDeletedConcurrentlyIsReportedMissingWithoutRewritingDatasetOrHistory() {
        RagEvalDataset dataset = dataset();
        installOwnedSamples(dataset, new ArrayList<>(List.of(storedSample(1L, "问题", "[11]", "[1]"))));
        doReturn(0).when(samples).deleteById(1L);

        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.deleteSample(USER_ID, DATASET_ID, 1L)).getCode());

        verify(samples, never()).selectCount(any());
        verify(datasets, never()).updateById(any(RagEvalDataset.class));
        verifyNoInteractions(runs, runItems);
    }

    @Test
    void deletingTheDatasetStillRemovesHistoricalItemsBeforeRunsAndRemainingSamples() {
        installOwnedSamples(dataset(), new ArrayList<>());
        List<RagEvalRun> history = List.of(comparableRun(71L), comparableRun(72L));
        when(runs.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RagEvalRun> query = invocation.getArgument(0);
            assertEquals(DATASET_ID, equality(query, "dataset_id"));
            assertEquals(USER_ID, equality(query, "user_id"));
            return history;
        });
        when(runItems.delete(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RagEvalRunItem> query = invocation.getArgument(0);
            assertEquals(DATASET_ID, equality(query, "dataset_id"));
            assertEquals(USER_ID, equality(query, "user_id"));
            assertFalse(query.getSqlSegment().contains("sample_id"), "应清理已删除样本的历史明细");
            return 2;
        });
        when(samples.delete(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RagEvalSample> query = invocation.getArgument(0);
            assertEquals(DATASET_ID, equality(query, "dataset_id"));
            assertEquals(USER_ID, equality(query, "user_id"));
            return 0;
        });

        service.deleteDataset(USER_ID, DATASET_ID);

        InOrder deletion = inOrder(runItems, runs, samples, datasets);
        deletion.verify(runItems).delete(any());
        deletion.verify(runs).deleteBatchIds(List.of(71L, 72L));
        deletion.verify(samples).delete(any());
        deletion.verify(datasets).deleteById(DATASET_ID);
    }

    @Test
    void runDatasetComputesHitRecallAndMrrFromRetrievalResults() {
        RagEvalSample hitAtSecond = storedSample(1L, "问题一", "[11,12]", null);
        RagEvalSample pageHitAtFirst = storedSample(2L, "问题二", "[]", "[3]");
        RagEvalSample miss = storedSample(3L, "问题三", "[50]", null);
        when(samples.selectList(any())).thenReturn(List.of(hitAtSecond, pageHitAtFirst, miss));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("问题一"), anyInt(), anyString()))
                .thenReturn(vectorResult(List.of(segment(20L, 1), segment(11L, 2), segment(12L, 4))));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("问题二"), anyInt(), anyString()))
                .thenReturn(vectorResult(List.of(segment(21L, 3), segment(22L, 5))));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("问题三"), anyInt(), anyString()))
                .thenReturn(vectorResult(List.of(segment(30L, 1))));
        RagEvalRunRequest request = new RagEvalRunRequest();
        request.setLimit(5);

        RagEvalRunVO run = service.runDataset(USER_ID, DATASET_ID, request);

        assertEquals("SUCCESS", run.getStatus());
        assertEquals(3, run.getTotalSamples());
        assertEquals(3, run.getEvaluatedSamples());
        assertEquals(0, run.getFailedSamples());
        assertEquals(3, run.getItems().size());
        assertEquals(2, run.getItems().get(0).getHitRank());
        assertEquals(1, run.getItems().get(1).getHitRank());
        assertNull(run.getItems().get(2).getHitRank());
        // 样本一：前 1 条没命中，前 3 条命中 11 和 12 两个期望段落中的 2 个。
        assertEquals(0D, run.getItems().get(0).getRecallAt1());
        assertEquals(1D, run.getItems().get(0).getRecallAt3());
        // Hit@1 只有页码样本命中，Hit@3 两条命中。
        assertEquals(1D / 3, run.getHitAt1(), 1e-9);
        assertEquals(2D / 3, run.getHitAt3(), 1e-9);
        assertEquals((0.5 + 1 + 0) / 3, run.getMrr(), 1e-9);
        assertEquals((1 + 1 + 0) / 3D, run.getRecallAt3(), 1e-9);
        ArgumentCaptor<RagEvalRun> finished = ArgumentCaptor.forClass(RagEvalRun.class);
        verify(runs).updateById(finished.capture());
        assertNotNull(finished.getValue().getFinishedAt());
        verify(datasets).updateById(any(RagEvalDataset.class));
    }

    @Test
    void runDatasetRecordsRetrievalFailuresPerSampleInsteadOfAborting() {
        when(samples.selectList(any())).thenReturn(List.of(
                storedSample(1L, "正常", "[11]", null),
                storedSample(2L, "异常", "[12]", null)
        ));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("正常"), anyInt(), anyString()))
                .thenReturn(vectorResult(List.of(segment(11L, 1))));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("异常"), anyInt(), anyString()))
                .thenThrow(new BusinessException(500, "向量库不可用"));

        RagEvalRunVO run = service.runDataset(USER_ID, DATASET_ID, new RagEvalRunRequest());

        assertEquals("PARTIAL_FAILED", run.getStatus());
        assertEquals(1, run.getEvaluatedSamples());
        assertEquals(1, run.getFailedSamples());
        assertTrue(run.getErrorMessage().contains("1 条样本评测失败"));
        assertEquals("向量库不可用", run.getItems().get(1).getErrorMessage());
        assertEquals(0D, run.getItems().get(1).getReciprocalRank());
        verify(runItems, times(2)).insert(any(RagEvalRunItem.class));
    }

    @Test
    void runSummaryWarnsWhenRetrievalDegradedOrUsedMockVectors() {
        when(samples.selectList(any())).thenReturn(List.of(
                storedSample(1L, "降级", "[11]", null),
                storedSample(2L, "演示", "[12]", null)
        ));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("降级"), anyInt(), anyString()))
                .thenReturn(new RetrievalResult(MATERIAL_ID, "资料", RetrievalService.MODE_TEXT, true,
                        "向量模型调用失败，已改用数据库文本检索", false,
                        List.of(new RetrievalHit(segment(11L, 1), null, 0.5, 0.45))));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("演示"), anyInt(), anyString()))
                .thenReturn(new RetrievalResult(MATERIAL_ID, "资料", RetrievalService.MODE_VECTOR, false, null, true,
                        List.of(new RetrievalHit(segment(12L, 1), 0.3, 0.5, 0.67))));

        RagEvalRunVO run = service.runDataset(USER_ID, DATASET_ID, new RagEvalRunRequest());

        assertEquals("SUCCESS", run.getStatus());
        assertTrue(run.getErrorMessage().contains("1 条样本的检索降级为数据库文本检索"));
        assertTrue(run.getErrorMessage().contains("向量模型调用失败"));
        assertTrue(run.getErrorMessage().contains("Mock 演示向量"));
    }

    @Test
    void runDatasetRefusesAnEmptyDataset() {
        when(samples.selectList(any())).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.runDataset(USER_ID, DATASET_ID, new RagEvalRunRequest()));
        verify(runs, never()).insert(any(RagEvalRun.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void textStrategyAndActualEvidenceArePersistedWithoutProviderSecrets() throws Exception {
        StudyMaterial material = material();
        material.setChunkStrategy("SLIDING_WINDOW");
        material.setChunkSize(700);
        material.setChunkOverlap(90);
        material.setContentHash("source-content-hash");
        when(studyMaterialService.getOne(any())).thenReturn(material);
        when(samples.selectList(any())).thenReturn(List.of(storedSample(1L, "问题一", "[11]", null)));
        var actual = new RetrievalResult(MATERIAL_ID, "资料", "TEXT", false, null, false,
                List.of(new RetrievalHit(segment(11L, 1), null, 0.8, 0.72)), "TEXT");
        when(retrievalService.retrieve(USER_ID, MATERIAL_ID, "问题一", 3, "TEXT")).thenReturn(actual);
        RagEvalRunRequest request = new RagEvalRunRequest();
        request.setName("  文本基线 ");
        request.setStrategy("TEXT");
        request.setLimit(3);

        RagEvalRunVO result = service.runDataset(USER_ID, DATASET_ID, request);

        verify(retrievalService).retrieve(USER_ID, MATERIAL_ID, "问题一", 3, "TEXT");
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("文本基线", result.getName());
        assertEquals("TEXT", result.getStrategy());
        assertEquals(List.of("TEXT"), result.getActualStrategies());
        assertEquals(false, result.getMockEmbedding());
        assertEquals(0, result.getDegradedSamples());
        assertEquals("TEXT", result.getItems().get(0).getActualStrategy());
        assertNotNull(result.getItems().get(0).getRetrievedSnapshot());

        ArgumentCaptor<RagEvalRun> persisted = ArgumentCaptor.forClass(RagEvalRun.class);
        verify(runs).insert(persisted.capture());
        var snapshot = new ObjectMapper().readTree(persisted.getValue().getParameterSnapshotJson());
        assertEquals("retrieval-v2", snapshot.path("version").asText());
        assertEquals("TEXT", snapshot.path("strategy").asText());
        assertEquals(3, snapshot.path("limit").asInt());
        assertEquals(700, snapshot.path("materials").get(0).path("chunkSize").asInt());
        assertEquals(90, snapshot.path("materials").get(0).path("chunkOverlap").asInt());
        assertEquals("source-content-hash", snapshot.path("materials").get(0).path("contentHash").asText());
        assertEquals(64, snapshot.path("sampleFingerprint").asText().length());
        assertEquals(64, snapshot.path("corpusFingerprint").asText().length());
        String savedJson = persisted.getValue().getParameterSnapshotJson();
        assertFalse(savedJson.contains("apiKey") || savedJson.contains("api-key") || savedJson.contains("Authorization"));
        assertEquals("TEXT", insertedRunItems.get(0).getActualStrategy());
        assertTrue(insertedRunItems.get(0).getRetrievedSnapshotJson().contains("正文"));
        // 后续改配置或改资料对象，已经保存的字符串快照不应随之变化。
        material.setChunkSize(1500);
        assertEquals(savedJson, persisted.getValue().getParameterSnapshotJson());
        assertEquals(700, ((Map<?, ?>) ((List<?>) result.getParameterSnapshot().get("materials")).get(0)).get("chunkSize"));
        ArgumentCaptor<LambdaQueryWrapper<StudyMaterial>> materialQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(studyMaterialService).getOne(materialQuery.capture());
        assertEquals(USER_ID, equality(materialQuery.getValue(), "user_id"));
        assertEquals(MATERIAL_ID, equality(materialQuery.getValue(), "id"));
        assertTrue(materialQuery.getValue().getSqlSegment().contains("for share"));
        ArgumentCaptor<LambdaQueryWrapper<MaterialSegment>> segmentQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(segments).selectList(segmentQuery.capture());
        assertEquals(MATERIAL_ID, equality(segmentQuery.getValue(), "material_id"));
        assertTrue(segmentQuery.getValue().getSqlSegment().contains("for share"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void historyPagesAreScopedToOwnerAndDatasetAndDoNotLoadAllRunItems() {
        RagEvalRunPageQuery query = new RagEvalRunPageQuery();
        query.setCurrent(2L);
        query.setSize(5L);
        Page<RagEvalRun> page = new Page<>(2L, 5L, 8L);
        page.setRecords(List.of(comparableRun(71L), comparableRun(70L)));
        when(runs.selectPage(any(), any())).thenReturn(page);

        var result = service.pageRuns(USER_ID, DATASET_ID, query);

        assertEquals(2L, result.getCurrent());
        assertEquals(5L, result.getSize());
        assertEquals(8L, result.getTotal());
        assertEquals(List.of(71L, 70L), result.getRecords().stream().map(RagEvalRunVO::getId).toList());
        assertTrue(result.getRecords().stream().allMatch(item -> item.getItems().isEmpty()));
        ArgumentCaptor<LambdaQueryWrapper<RagEvalRun>> filters = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(runs).selectPage(any(), filters.capture());
        assertEquals(USER_ID, equality(filters.getValue(), "user_id"));
        assertEquals(DATASET_ID, equality(filters.getValue(), "dataset_id"));
        assertTrue(filters.getValue().getSqlSegment().contains("id DESC"));
        verifyNoInteractions(runItems);
    }

    @Test
    void historyOfAnotherUsersDatasetIsRejectedBeforeLoadingRuns() {
        when(datasets.selectOne(any())).thenReturn(null);

        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.pageRuns(USER_ID, DATASET_ID, new RagEvalRunPageQuery())).getCode());
        verifyNoInteractions(runs, runItems);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void comparesTwoOrThreeOwnedRunsInRequestedOrderWhenConditionsMatch(int count) {
        List<RagEvalRun> history = List.of(comparableRun(71L), comparableRun(72L), comparableRun(73L));
        history.get(0).setStrategy("TEXT");
        history.get(1).setStrategy("VECTOR_ONLY");
        history.get(2).setStrategy("VECTOR_RERANK");
        installOwnedRuns(history);
        List<Long> ids = List.of(73L, 71L, 72L).subList(0, count);

        var comparison = service.compareRuns(USER_ID, DATASET_ID, ids);

        assertTrue(comparison.isComparable());
        assertTrue(comparison.getReasons().isEmpty());
        assertEquals(ids, comparison.getRuns().stream().map(RagEvalRunVO::getId).toList());
    }

    @Test
    void comparisonRequiresTwoOrThreeDistinctNonNullIds() {
        for (List<Long> ids : Arrays.asList(null, List.<Long>of(), List.of(71L), List.of(71L, 71L),
                List.of(71L, 72L, 73L, 74L), Arrays.asList(71L, null))) {
            assertThrows(BusinessException.class, () -> service.compareRuns(USER_ID, DATASET_ID, ids));
        }
        verifyNoInteractions(runs, runItems);
    }

    @Test
    void comparisonRejectsAnotherUsersRunWithoutReadingItsItems() {
        RagEvalRun foreign = comparableRun(72L);
        foreign.setUserId(8L);
        installOwnedRuns(List.of(comparableRun(71L), foreign));

        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.compareRuns(USER_ID, DATASET_ID, List.of(71L, 72L))).getCode());
        // 仅本人的第一条运行可进入明细查询。
        verify(runItems, times(1)).selectList(any());
    }

    @Test
    void comparisonRejectsAnOwnedRunFromAnotherDataset() {
        RagEvalRun anotherDataset = comparableRun(72L);
        anotherDataset.setDatasetId(99L);
        installOwnedRuns(List.of(comparableRun(71L), anotherDataset));

        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.compareRuns(USER_ID, DATASET_ID, List.of(71L, 72L))).getCode());
    }

    @ParameterizedTest
    @CsvSource({"SAMPLES,样本范围", "CORPUS,资料原文", "MOCK,模拟向量", "DEGRADED,检索降级",
            "FAILED,失败样本", "PARTIAL_FAILED,失败样本", "RUNNING,未完成", "TOP_K,Top K", "VERSION,版本"})
    void differingOrUnreliableConditionsAreViewableButNotComparable(String changed, String reason) {
        RagEvalRun baseline = comparableRun(71L);
        RagEvalRun candidate = comparableRun(72L);
        switch (changed) {
            case "SAMPLES" -> candidate.setParameterSnapshotJson("{\"sampleFingerprint\":\"other\",\"corpusFingerprint\":\"corpus\"}");
            case "CORPUS" -> candidate.setParameterSnapshotJson("{\"sampleFingerprint\":\"samples\",\"corpusFingerprint\":\"other\"}");
            case "MOCK" -> candidate.setMockEmbedding(true);
            case "DEGRADED" -> candidate.setDegradedSamples(1);
            case "TOP_K" -> candidate.setRetrievalLimit(3);
            case "VERSION" -> candidate.setParameterSnapshotJson("{\"version\":\"retrieval-v1\",\"sampleFingerprint\":\"samples\",\"corpusFingerprint\":\"corpus\"}");
            default -> candidate.setStatus(changed);
        }
        installOwnedRuns(List.of(baseline, candidate));

        var comparison = service.compareRuns(USER_ID, DATASET_ID, List.of(71L, 72L));

        assertFalse(comparison.isComparable());
        assertEquals(2, comparison.getRuns().size());
        assertTrue(comparison.getReasons().stream().anyMatch(item -> item.contains(reason)), comparison.getReasons().toString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-json", "{}", "null"})
    void historicalRunsWithoutReliableSnapshotsRemainExplicitlyNonComparable(String snapshot) {
        RagEvalRun old = comparableRun(72L);
        old.setParameterSnapshotJson(snapshot);
        old.setMockEmbedding(null);
        old.setDegradedSamples(null);
        installOwnedRuns(List.of(comparableRun(71L), old));

        var result = service.compareRuns(USER_ID, DATASET_ID, List.of(71L, 72L));

        assertFalse(result.isComparable());
        assertTrue(result.getReasons().stream().anyMatch(reason -> reason.contains("历史运行未记录")));
        assertTrue(result.getReasons().stream().anyMatch(reason -> reason.contains("未记录向量来源")));
        assertTrue(result.getReasons().stream().anyMatch(reason -> reason.contains("未记录降级")));
    }

    @Test
    void nonexistentOrForeignSampleIdsCannotSilentlyRunASubset() {
        when(samples.selectList(any())).thenReturn(List.of(storedSample(1L, "问题一", "[11]", null)));
        for (List<Long> ids : List.of(List.of(1L, 99L), List.of(-1L), Arrays.asList(1L, null))) {
            RagEvalRunRequest request = new RagEvalRunRequest();
            request.setSampleIds(ids);
            assertThrows(BusinessException.class, () -> service.runDataset(USER_ID, DATASET_ID, request));
        }
        verify(runs, never()).insert(any(RagEvalRun.class));
        verifyNoInteractions(runItems, retrievalService);
    }

    @ParameterizedTest
    @CsvSource({"[99],[]", "[],[99]", "[],[]"})
    void staleOrMissingAnnotationsAreRejectedBeforeCreatingARun(String expectedSegments, String expectedPages) {
        when(samples.selectList(any())).thenReturn(List.of(storedSample(1L, "重新分块前的问题", expectedSegments, expectedPages)));

        assertEquals(409, assertThrows(BusinessException.class,
                () -> service.runDataset(USER_ID, DATASET_ID, new RagEvalRunRequest())).getCode());
        verify(runs, never()).insert(any(RagEvalRun.class));
        verifyNoInteractions(runItems, retrievalService);
    }

    private void installOwnedRuns(List<RagEvalRun> history) {
        when(runs.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RagEvalRun> query = invocation.getArgument(0);
            Long id = (Long) equality(query, "id");
            Long userId = (Long) equality(query, "user_id");
            assertEquals(USER_ID, userId);
            return history.stream().filter(run -> run.getId().equals(id) && run.getUserId().equals(userId)).findFirst().orElse(null);
        });
        when(runItems.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RagEvalRunItem> query = invocation.getArgument(0);
            assertEquals(USER_ID, equality(query, "user_id"));
            assertNotNull(equality(query, "run_id"));
            return List.of();
        });
    }

    private void installOwnedSamples(RagEvalDataset dataset, List<RagEvalSample> activeSamples) {
        when(datasets.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RagEvalDataset> query = invocation.getArgument(0);
            Long id = (Long) equality(query, "id");
            Long userId = (Long) equality(query, "user_id");
            return dataset.getId().equals(id) && dataset.getUserId().equals(userId) ? dataset : null;
        });
        when(samples.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RagEvalSample> query = invocation.getArgument(0);
            Long id = (Long) equality(query, "id");
            Long datasetId = (Long) equality(query, "dataset_id");
            Long userId = (Long) equality(query, "user_id");
            return activeSamples.stream().filter(sample -> sample.getId().equals(id)
                    && sample.getDatasetId().equals(datasetId) && sample.getUserId().equals(userId)).findFirst().orElse(null);
        });
        when(samples.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RagEvalSample> query = invocation.getArgument(0);
            Long datasetId = (Long) equality(query, "dataset_id");
            Long userId = (Long) equality(query, "user_id");
            return activeSamples.stream().filter(sample -> sample.getDatasetId().equals(datasetId)
                    && sample.getUserId().equals(userId)).toList();
        });
        when(samples.selectCount(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RagEvalSample> query = invocation.getArgument(0);
            Long datasetId = (Long) equality(query, "dataset_id");
            Long userId = (Long) equality(query, "user_id");
            return activeSamples.stream().filter(sample -> sample.getDatasetId().equals(datasetId)
                    && sample.getUserId().equals(userId)).count();
        });
        when(samples.deleteById(any(Long.class))).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return activeSamples.removeIf(sample -> sample.getId().equals(id)) ? 1 : 0;
        });
    }

    private RagEvalRunItem historicalItem(RagEvalRun run) {
        RagEvalRunItem item = new RagEvalRunItem();
        item.setId(run.getId() + 100);
        item.setRunId(run.getId());
        item.setDatasetId(DATASET_ID);
        item.setUserId(USER_ID);
        item.setMaterialId(MATERIAL_ID);
        item.setSampleId(1L);
        item.setQueryText("评测时的问题");
        item.setExpectedSegmentIds("[11]");
        item.setExpectedPageNos("[1]");
        item.setRetrievedSegmentIds("[12,11]");
        item.setRetrievedPageNos("[3,1]");
        item.setRetrievedSnapshotJson("[{\"contentText\":\"评测时的原文\"}]");
        item.setHitRank(2);
        item.setReciprocalRank(0.5D);
        item.setActualStrategy("VECTOR_RERANK");
        item.setMockEmbedding(false);
        item.setLatencyMs(18L);
        return item;
    }

    private RagEvalRun comparableRun(long id) {
        RagEvalRun run = new RagEvalRun();
        run.setId(id);
        run.setUserId(USER_ID);
        run.setDatasetId(DATASET_ID);
        run.setMaterialId(MATERIAL_ID);
        run.setStatus("SUCCESS");
        run.setStrategy("VECTOR_RERANK");
        run.setRetrievalLimit(5);
        run.setMockEmbedding(false);
        run.setDegradedSamples(0);
        run.setParameterSnapshotJson("{\"version\":\"retrieval-v2\",\"sampleFingerprint\":\"samples\",\"corpusFingerprint\":\"corpus\"}");
        run.setActualStrategiesJson("[\"VECTOR_RERANK\"]");
        return run;
    }

    private Object equality(LambdaQueryWrapper<?> query, String column) {
        var matcher = Pattern.compile("\\b" + column + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}")
                .matcher(query.getSqlSegment());
        assertTrue(matcher.find(), "Missing owner/identity condition: " + column);
        return query.getParamNameValuePairs().get(matcher.group(1));
    }

    private MaterialSegment storedSegment(long id, int pageNo) {
        MaterialSegment segment = new MaterialSegment();
        segment.setId(id);
        segment.setMaterialId(MATERIAL_ID);
        segment.setSegmentNo((int) id);
        segment.setPageNo(pageNo);
        segment.setContentText("正文 " + id);
        segment.setEmbeddingStatus("SUCCESS");
        segment.setEmbeddingModel("embedding-test-model");
        return segment;
    }

    @Test
    void importCmrc2018CreatesOneSegmentPerContextAndOneSamplePerQuestion() {
        String json = """
                {"data": [{"title": "细胞", "paragraphs": [
                  {"context": "细胞是生命活动的基本单位。", "qas": [
                    {"id": "Q1", "question": "生命活动的基本单位是什么？", "answers": [{"text": "细胞"}]},
                    {"id": "Q2", "question": "细胞有什么作用？", "answers": [{"text": "生命活动"}]}
                  ]},
                  {"context": "酶是生物催化剂。", "qas": [
                    {"id": "Q3", "question": "酶是什么？", "answers": [{"text": "生物催化剂"}]}
                  ]}
                ]}]}
                """;
        MockMultipartFile file = new MockMultipartFile("file", "dev.json", "application/json", json.getBytes(StandardCharsets.UTF_8));

        CmrcImportResultVO result = service.importCmrc2018(USER_ID, file, null, null, null, 2, false);

        assertEquals(MATERIAL_ID, result.getMaterialId());
        assertEquals(DATASET_ID, result.getDatasetId());
        assertEquals(2, result.getSegmentCount());
        assertEquals(2, result.getSampleCount(), "maxSamples=2 时只导入前两个问题");
        assertNull(result.getEmbeddingTaskId());
        assertTrue(result.getMaterialTitle().contains("CMRC2018 dev"));
        verify(segments, times(2)).insert(any(MaterialSegment.class));
        assertEquals(2, insertedSamples.size());
        assertEquals("生命活动的基本单位是什么？", insertedSamples.get(0).getQueryText());
        assertEquals("细胞", insertedSamples.get(0).getExpectedKeywords());
        assertEquals("IMPORTED", insertedSamples.get(0).getSourceType());
        assertTrue(insertedSamples.get(0).getNote().contains("Q1"));
        verify(aiTaskService, never()).submitEmbeddingTask(any(), any(), any());
    }

    @Test
    void importCmrc2018RejectsEmptyOrUnparseableFiles() {
        MockMultipartFile empty = new MockMultipartFile("file", "dev.json", "application/json", new byte[0]);
        MockMultipartFile broken = new MockMultipartFile("file", "dev.json", "application/json", "not json".getBytes(StandardCharsets.UTF_8));

        assertThrows(BusinessException.class, () -> service.importCmrc2018(USER_ID, empty, null, null, null, null, false));
        assertThrows(BusinessException.class, () -> service.importCmrc2018(USER_ID, broken, null, null, null, null, false));
        verify(studyMaterialService, never()).save(any(StudyMaterial.class));
    }

    private RagEvalSampleBatchCreateRequest batch(RagEvalSampleCreateRequest... items) {
        RagEvalSampleBatchCreateRequest request = new RagEvalSampleBatchCreateRequest();
        request.setSamples(List.of(items));
        return request;
    }

    private RagEvalSampleCreateRequest sample(String queryText, List<Long> segmentIds, List<Integer> pageNos) {
        RagEvalSampleCreateRequest request = new RagEvalSampleCreateRequest();
        request.setQueryText(queryText);
        request.setExpectedSegmentIds(segmentIds);
        request.setExpectedPageNos(pageNos);
        return request;
    }

    private RagEvalSample storedSample(Long id, String queryText, String segmentIdsJson, String pageNosJson) {
        RagEvalSample sample = new RagEvalSample();
        sample.setId(id);
        sample.setDatasetId(DATASET_ID);
        sample.setUserId(USER_ID);
        sample.setMaterialId(MATERIAL_ID);
        sample.setQueryText(queryText);
        sample.setExpectedSegmentIds(segmentIdsJson);
        sample.setExpectedPageNos(pageNosJson);
        return sample;
    }

    private RetrievalResult vectorResult(List<RetrievedSegment> retrieved) {
        return new RetrievalResult(MATERIAL_ID, "资料", RetrievalService.MODE_VECTOR, false, null, false,
                retrieved.stream().map(segment -> new RetrievalHit(segment, segment.score(), 0D, 0D)).toList());
    }

    private RetrievedSegment segment(Long segmentId, Integer pageNo) {
        return new RetrievedSegment(segmentId, segmentId.intValue(), pageNo, "章节", "正文", null, 0.9);
    }

    private RagEvalDataset dataset() {
        RagEvalDataset dataset = new RagEvalDataset();
        dataset.setId(DATASET_ID);
        dataset.setUserId(USER_ID);
        dataset.setMaterialId(MATERIAL_ID);
        dataset.setName("评测集");
        dataset.setStatus("ACTIVE");
        dataset.setSampleCount(0);
        return dataset;
    }

    private StudyMaterial material() {
        StudyMaterial material = new StudyMaterial();
        material.setId(MATERIAL_ID);
        material.setUserId(USER_ID);
        material.setTitle("生物必修一");
        return material;
    }
}
