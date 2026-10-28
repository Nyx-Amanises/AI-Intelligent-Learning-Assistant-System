package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.rag.RagEvalDatasetCreateRequest;
import com.aiassistant.learning.dto.rag.RagEvalRunRequest;
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
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    void runDatasetComputesHitRecallAndMrrFromRetrievalResults() {
        RagEvalSample hitAtSecond = storedSample(1L, "问题一", "[11,12]", null);
        RagEvalSample pageHitAtFirst = storedSample(2L, "问题二", "[]", "[3]");
        RagEvalSample miss = storedSample(3L, "问题三", "[50]", null);
        when(samples.selectList(any())).thenReturn(List.of(hitAtSecond, pageHitAtFirst, miss));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("问题一"), anyInt()))
                .thenReturn(vectorResult(List.of(segment(20L, 1), segment(11L, 2), segment(12L, 4))));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("问题二"), anyInt()))
                .thenReturn(vectorResult(List.of(segment(21L, 3), segment(22L, 5))));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("问题三"), anyInt()))
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
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("正常"), anyInt()))
                .thenReturn(vectorResult(List.of(segment(11L, 1))));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("异常"), anyInt()))
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
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("降级"), anyInt()))
                .thenReturn(new RetrievalResult(MATERIAL_ID, "资料", RetrievalService.MODE_TEXT, true,
                        "向量模型调用失败，已改用数据库文本检索", false,
                        List.of(new RetrievalHit(segment(11L, 1), null, 0.5, 0.45))));
        when(retrievalService.retrieve(eq(USER_ID), eq(MATERIAL_ID), eq("演示"), anyInt()))
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
