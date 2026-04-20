package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.EmbeddingTaskRequest;
import com.aiassistant.learning.dto.rag.RagEvalDatasetCreateRequest;
import com.aiassistant.learning.dto.rag.RagEvalDatasetPageQuery;
import com.aiassistant.learning.dto.rag.RagEvalRunRequest;
import com.aiassistant.learning.dto.rag.RagEvalSampleBatchCreateRequest;
import com.aiassistant.learning.dto.rag.RagEvalSampleCreateRequest;
import com.aiassistant.learning.dto.rag.RagEvalSampleUpdateRequest;
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
import com.aiassistant.learning.service.RagEvalService;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.aiassistant.learning.vo.rag.CmrcImportResultVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.rag.RagEvalDatasetVO;
import com.aiassistant.learning.vo.rag.RagEvalRunItemVO;
import com.aiassistant.learning.vo.rag.RagEvalRunVO;
import com.aiassistant.learning.vo.rag.RagEvalSampleVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RagEvalServiceImpl implements RagEvalService {

    private static final int DEFAULT_LIMIT = 5;

    private final RagEvalDatasetMapper datasetMapper;
    private final RagEvalSampleMapper sampleMapper;
    private final RagEvalRunMapper runMapper;
    private final RagEvalRunItemMapper runItemMapper;
    private final MaterialSegmentMapper materialSegmentMapper;
    private final StudyMaterialService studyMaterialService;
    private final RetrievalService retrievalService;
    private final AiTaskService aiTaskService;
    private final ObjectMapper objectMapper;

    public RagEvalServiceImpl(
            RagEvalDatasetMapper datasetMapper,
            RagEvalSampleMapper sampleMapper,
            RagEvalRunMapper runMapper,
            RagEvalRunItemMapper runItemMapper,
            MaterialSegmentMapper materialSegmentMapper,
            StudyMaterialService studyMaterialService,
            RetrievalService retrievalService,
            AiTaskService aiTaskService,
            ObjectMapper objectMapper
    ) {
        this.datasetMapper = datasetMapper;
        this.sampleMapper = sampleMapper;
        this.runMapper = runMapper;
        this.runItemMapper = runItemMapper;
        this.materialSegmentMapper = materialSegmentMapper;
        this.studyMaterialService = studyMaterialService;
        this.retrievalService = retrievalService;
        this.aiTaskService = aiTaskService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagEvalDatasetVO createDataset(Long userId, RagEvalDatasetCreateRequest request) {
        StudyMaterial material = getUserOwnedMaterial(userId, request.getMaterialId());

        RagEvalDataset dataset = new RagEvalDataset();
        dataset.setUserId(userId);
        dataset.setMaterialId(material.getId());
        dataset.setName(request.getName().trim());
        dataset.setDescription(trimToNull(request.getDescription()));
        dataset.setStatus("ACTIVE");
        dataset.setSampleCount(0);
        datasetMapper.insert(dataset);
        return toDatasetVO(dataset, Map.of(material.getId(), material.getTitle()));
    }

    @Override
    public PageVO<RagEvalDatasetVO> pageDatasets(Long userId, RagEvalDatasetPageQuery query) {
        Page<RagEvalDataset> page = new Page<>(query.getCurrent(), query.getSize());
        Page<RagEvalDataset> result = datasetMapper.selectPage(page, new LambdaQueryWrapper<RagEvalDataset>()
                .eq(RagEvalDataset::getUserId, userId)
                .eq(query.getMaterialId() != null, RagEvalDataset::getMaterialId, query.getMaterialId())
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(RagEvalDataset::getName, query.getKeyword())
                        .or()
                        .like(RagEvalDataset::getDescription, query.getKeyword()))
                .orderByDesc(RagEvalDataset::getCreatedAt));
        Map<Long, String> titleMap = buildMaterialTitleMap(result.getRecords());
        return PageVO.<RagEvalDatasetVO>builder()
                .current(result.getCurrent())
                .size(result.getSize())
                .total(result.getTotal())
                .pages(result.getPages())
                .records(result.getRecords().stream()
                        .map(dataset -> toDatasetVO(dataset, titleMap))
                        .toList())
                .build();
    }

    @Override
    public RagEvalDatasetVO getDataset(Long userId, Long datasetId) {
        RagEvalDataset dataset = getUserOwnedDataset(userId, datasetId);
        Map<Long, String> titleMap = buildMaterialTitleMap(List.of(dataset));
        return toDatasetVO(dataset, titleMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(Long userId, Long datasetId) {
        RagEvalDataset dataset = getUserOwnedDataset(userId, datasetId);
        List<RagEvalRun> runs = runMapper.selectList(new LambdaQueryWrapper<RagEvalRun>()
                .eq(RagEvalRun::getDatasetId, dataset.getId())
                .eq(RagEvalRun::getUserId, userId));
        if (!runs.isEmpty()) {
            runItemMapper.delete(new LambdaQueryWrapper<RagEvalRunItem>()
                    .eq(RagEvalRunItem::getDatasetId, dataset.getId())
                    .eq(RagEvalRunItem::getUserId, userId));
            runMapper.deleteBatchIds(runs.stream().map(RagEvalRun::getId).toList());
        }
        sampleMapper.delete(new LambdaQueryWrapper<RagEvalSample>()
                .eq(RagEvalSample::getDatasetId, dataset.getId())
                .eq(RagEvalSample::getUserId, userId));
        datasetMapper.deleteById(dataset.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RagEvalSampleVO> addSamples(Long userId, Long datasetId, RagEvalSampleBatchCreateRequest request) {
        RagEvalDataset dataset = getUserOwnedDataset(userId, datasetId);
        List<RagEvalSampleVO> created = request.getSamples().stream()
                .map(item -> createSample(userId, dataset, item))
                .map(this::toSampleVO)
                .toList();
        refreshSampleCount(dataset);
        return created;
    }

    @Override
    public List<RagEvalSampleVO> listSamples(Long userId, Long datasetId) {
        RagEvalDataset dataset = getUserOwnedDataset(userId, datasetId);
        return sampleMapper.selectList(new LambdaQueryWrapper<RagEvalSample>()
                        .eq(RagEvalSample::getDatasetId, dataset.getId())
                        .eq(RagEvalSample::getUserId, userId)
                        .orderByAsc(RagEvalSample::getId))
                .stream()
                .map(this::toSampleVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagEvalSampleVO updateSample(Long userId, Long datasetId, Long sampleId, RagEvalSampleUpdateRequest request) {
        RagEvalDataset dataset = getUserOwnedDataset(userId, datasetId);
        RagEvalSample sample = getUserOwnedSample(userId, dataset.getId(), sampleId);
        Long materialId = request.getMaterialId() == null ? dataset.getMaterialId() : request.getMaterialId();
        validateSampleTarget(userId, materialId, request.getExpectedSegmentIds(), request.getExpectedPageNos());

        sample.setMaterialId(materialId);
        sample.setQueryText(request.getQueryText().trim());
        sample.setExpectedSegmentIds(writeJson(request.getExpectedSegmentIds()));
        sample.setExpectedPageNos(writeJson(request.getExpectedPageNos()));
        sample.setExpectedKeywords(trimToNull(request.getExpectedKeywords()));
        sample.setTag(trimToNull(request.getTag()));
        sample.setDifficulty(request.getDifficulty() == null ? 3 : request.getDifficulty());
        sample.setSourceType(normalizeSourceType(request.getSourceType()));
        sample.setNote(trimToNull(request.getNote()));
        sampleMapper.updateById(sample);
        return toSampleVO(sample);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSample(Long userId, Long datasetId, Long sampleId) {
        RagEvalDataset dataset = getUserOwnedDataset(userId, datasetId);
        RagEvalSample sample = getUserOwnedSample(userId, dataset.getId(), sampleId);
        sampleMapper.deleteById(sample.getId());
        refreshSampleCount(dataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagEvalRunVO runDataset(Long userId, Long datasetId, RagEvalRunRequest request) {
        RagEvalDataset dataset = getUserOwnedDataset(userId, datasetId);
        List<RagEvalSample> samples = selectRunSamples(userId, dataset.getId(), request.getSampleIds());
        if (samples.isEmpty()) {
            throw new BusinessException("评测集暂无样本，请先添加人工标注样本");
        }

        int retrievalLimit = resolveLimit(request.getLimit());
        RagEvalRun run = createRunningRun(userId, dataset, samples.size(), retrievalLimit);
        List<RagEvalRunItem> runItems = samples.stream()
                .map(sample -> evaluateSample(userId, run, sample, retrievalLimit))
                .toList();

        finishRun(run, runItems);
        dataset.setLastRunId(run.getId());
        dataset.setLastRunAt(run.getFinishedAt());
        datasetMapper.updateById(dataset);
        return toRunVO(run, runItems);
    }

    @Override
    public RagEvalRunVO getRun(Long userId, Long runId) {
        RagEvalRun run = runMapper.selectOne(new LambdaQueryWrapper<RagEvalRun>()
                .eq(RagEvalRun::getId, runId)
                .eq(RagEvalRun::getUserId, userId)
                .last("limit 1"));
        if (run == null) {
            throw new BusinessException(404, "评测运行记录不存在");
        }
        List<RagEvalRunItem> items = runItemMapper.selectList(new LambdaQueryWrapper<RagEvalRunItem>()
                .eq(RagEvalRunItem::getRunId, run.getId())
                .eq(RagEvalRunItem::getUserId, userId)
                .orderByAsc(RagEvalRunItem::getId));
        return toRunVO(run, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CmrcImportResultVO importCmrc2018(
            Long userId,
            MultipartFile file,
            String materialTitle,
            String datasetName,
            String splitName,
            Integer maxSamples,
            Boolean submitEmbeddingTask
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CMRC2018 数据文件不能为空");
        }

        String normalizedSplitName = StringUtils.hasText(splitName) ? splitName.trim() : "dev";
        int resolvedMaxSamples = resolveImportSampleLimit(maxSamples);
        String content = readUploadText(file);
        List<CmrcParagraph> paragraphs = parseCmrcParagraphs(content);
        if (paragraphs.isEmpty()) {
            throw new BusinessException("没有从文件中解析到 CMRC2018 段落");
        }

        LinkedHashMap<String, CmrcContextDraft> contextMap = new LinkedHashMap<>();
        List<CmrcSampleDraft> sampleDrafts = buildCmrcSampleDrafts(paragraphs, contextMap, resolvedMaxSamples);
        if (sampleDrafts.isEmpty()) {
            throw new BusinessException("没有从文件中解析到可导入的问题样本");
        }

        String resolvedMaterialTitle = StringUtils.hasText(materialTitle)
                ? materialTitle.trim()
                : "CMRC2018 " + normalizedSplitName + " 阅读理解语料";
        String resolvedDatasetName = StringUtils.hasText(datasetName)
                ? datasetName.trim()
                : "CMRC2018 " + normalizedSplitName + " RAG评测集";

        StudyMaterial material = new StudyMaterial();
        material.setUserId(userId);
        material.setTitle(resolvedMaterialTitle);
        material.setMaterialType("TEXT");
        material.setSourceType("IMPORT");
        material.setParseStatus("SUCCESS");
        material.setSummaryStatus("PENDING");
        material.setDifficultyLevel(3);
        material.setTags("CMRC2018,RAG评测,中文阅读理解");
        material.setTotalCharacters(contextMap.values().stream()
                .map(CmrcContextDraft::context)
                .mapToInt(String::length)
                .sum());
        material.setDeleted(0);
        studyMaterialService.save(material);

        int segmentNo = 1;
        for (CmrcContextDraft contextDraft : contextMap.values()) {
            MaterialSegment segment = new MaterialSegment();
            segment.setMaterialId(material.getId());
            segment.setSegmentNo(segmentNo);
            segment.setSectionTitle(buildCmrcSectionTitle(normalizedSplitName, contextDraft, segmentNo));
            segment.setContentText(contextDraft.context());
            segment.setTokenEstimate(Math.max(1, contextDraft.context().length() / 4));
            segment.setKeywords("CMRC2018");
            segment.setEmbeddingStatus("PENDING");
            materialSegmentMapper.insert(segment);
            contextDraft.setSegmentId(segment.getId());
            segmentNo++;
        }

        RagEvalDataset dataset = new RagEvalDataset();
        dataset.setUserId(userId);
        dataset.setMaterialId(material.getId());
        dataset.setName(resolvedDatasetName);
        dataset.setDescription("从 CMRC2018 " + normalizedSplitName + " 数据导入，样本自动标注到对应 context 分段。");
        dataset.setStatus("ACTIVE");
        dataset.setSampleCount(0);
        datasetMapper.insert(dataset);

        for (CmrcSampleDraft sampleDraft : sampleDrafts) {
            RagEvalSample sample = new RagEvalSample();
            sample.setDatasetId(dataset.getId());
            sample.setUserId(userId);
            sample.setMaterialId(material.getId());
            sample.setQueryText(sampleDraft.question());
            sample.setExpectedSegmentIds(writeJson(List.of(sampleDraft.context().segmentId())));
            sample.setExpectedKeywords(joinAnswers(sampleDraft.answerTexts()));
            sample.setTag("CMRC2018");
            sample.setDifficulty(3);
            sample.setSourceType("IMPORTED");
            sample.setNote(buildCmrcSampleNote(normalizedSplitName, sampleDraft));
            sampleMapper.insert(sample);
        }
        refreshSampleCount(dataset);

        AiTaskDetailVO embeddingTask = null;
        if (Boolean.TRUE.equals(submitEmbeddingTask)) {
            EmbeddingTaskRequest embeddingTaskRequest = new EmbeddingTaskRequest();
            embeddingTaskRequest.setForceRegenerate(false);
            embeddingTask = aiTaskService.submitEmbeddingTask(userId, material.getId(), embeddingTaskRequest);
        }

        return CmrcImportResultVO.builder()
                .materialId(material.getId())
                .materialTitle(material.getTitle())
                .datasetId(dataset.getId())
                .datasetName(dataset.getName())
                .segmentCount(contextMap.size())
                .sampleCount(sampleDrafts.size())
                .embeddingTaskId(embeddingTask == null ? null : embeddingTask.getId())
                .build();
    }

    private RagEvalSample createSample(Long userId, RagEvalDataset dataset, RagEvalSampleCreateRequest request) {
        Long materialId = request.getMaterialId() == null ? dataset.getMaterialId() : request.getMaterialId();
        validateSampleTarget(userId, materialId, request.getExpectedSegmentIds(), request.getExpectedPageNos());

        RagEvalSample sample = new RagEvalSample();
        sample.setDatasetId(dataset.getId());
        sample.setUserId(userId);
        sample.setMaterialId(materialId);
        sample.setQueryText(request.getQueryText().trim());
        sample.setExpectedSegmentIds(writeJson(request.getExpectedSegmentIds()));
        sample.setExpectedPageNos(writeJson(request.getExpectedPageNos()));
        sample.setExpectedKeywords(trimToNull(request.getExpectedKeywords()));
        sample.setTag(trimToNull(request.getTag()));
        sample.setDifficulty(request.getDifficulty() == null ? 3 : request.getDifficulty());
        sample.setSourceType(normalizeSourceType(request.getSourceType()));
        sample.setNote(trimToNull(request.getNote()));
        sampleMapper.insert(sample);
        return sample;
    }

    private void validateSampleTarget(
            Long userId,
            Long materialId,
            List<Long> expectedSegmentIds,
            List<Integer> expectedPageNos
    ) {
        getUserOwnedMaterial(userId, materialId);
        boolean hasSegments = !CollectionUtils.isEmpty(expectedSegmentIds);
        boolean hasPages = !CollectionUtils.isEmpty(expectedPageNos);
        if (!hasSegments && !hasPages) {
            throw new BusinessException("每条评测样本至少需要标注相关段落ID或相关页码");
        }
        if (hasPages && expectedPageNos.stream().anyMatch(pageNo -> pageNo == null || pageNo <= 0)) {
            throw new BusinessException("相关页码必须大于0");
        }
        if (hasSegments) {
            long matchedCount = materialSegmentMapper.selectCount(new LambdaQueryWrapper<MaterialSegment>()
                    .eq(MaterialSegment::getMaterialId, materialId)
                    .in(MaterialSegment::getId, expectedSegmentIds));
            if (matchedCount != new HashSet<>(expectedSegmentIds).size()) {
                throw new BusinessException("存在不属于当前资料的相关段落ID");
            }
        }
    }

    private RagEvalRun createRunningRun(Long userId, RagEvalDataset dataset, int sampleCount, int retrievalLimit) {
        LocalDateTime now = LocalDateTime.now();
        RagEvalRun run = new RagEvalRun();
        run.setDatasetId(dataset.getId());
        run.setUserId(userId);
        run.setMaterialId(dataset.getMaterialId());
        run.setStatus("RUNNING");
        run.setRetrievalLimit(retrievalLimit);
        run.setTotalSamples(sampleCount);
        run.setEvaluatedSamples(0);
        run.setFailedSamples(0);
        run.setStartedAt(now);
        runMapper.insert(run);
        return run;
    }

    private RagEvalRunItem evaluateSample(Long userId, RagEvalRun run, RagEvalSample sample, int retrievalLimit) {
        long startTime = System.currentTimeMillis();
        RagEvalRunItem item = new RagEvalRunItem();
        item.setRunId(run.getId());
        item.setDatasetId(run.getDatasetId());
        item.setSampleId(sample.getId());
        item.setUserId(userId);
        item.setMaterialId(sample.getMaterialId());
        item.setQueryText(sample.getQueryText());
        item.setExpectedSegmentIds(sample.getExpectedSegmentIds());
        item.setExpectedPageNos(sample.getExpectedPageNos());

        try {
            List<RetrievedSegment> retrieved = retrievalService.retrieveMaterialSegments(
                    userId,
                    sample.getMaterialId(),
                    sample.getQueryText(),
                    retrievalLimit
            );
            List<Long> retrievedSegmentIds = retrieved.stream().map(RetrievedSegment::segmentId).toList();
            List<Integer> retrievedPageNos = retrieved.stream().map(RetrievedSegment::pageNo).filter(Objects::nonNull).toList();
            List<Long> expectedSegmentIds = parseLongList(sample.getExpectedSegmentIds());
            List<Integer> expectedPageNos = parseIntList(sample.getExpectedPageNos());

            item.setRetrievedSegmentIds(writeJson(retrievedSegmentIds));
            item.setRetrievedPageNos(writeJson(retrievedPageNos));
            item.setHitRank(findHitRank(retrieved, expectedSegmentIds, expectedPageNos));
            item.setReciprocalRank(item.getHitRank() == null ? 0D : 1D / item.getHitRank());
            item.setRecallAt1(computeRecallAtK(retrieved, expectedSegmentIds, expectedPageNos, 1));
            item.setRecallAt3(computeRecallAtK(retrieved, expectedSegmentIds, expectedPageNos, 3));
            item.setRecallAt5(computeRecallAtK(retrieved, expectedSegmentIds, expectedPageNos, 5));
        } catch (Exception exception) {
            item.setErrorMessage(truncate(exception.getMessage(), 1500));
            item.setReciprocalRank(0D);
            item.setRecallAt1(0D);
            item.setRecallAt3(0D);
            item.setRecallAt5(0D);
        } finally {
            item.setLatencyMs(System.currentTimeMillis() - startTime);
            runItemMapper.insert(item);
        }
        return item;
    }

    private void finishRun(RagEvalRun run, List<RagEvalRunItem> runItems) {
        int total = runItems.size();
        int failed = (int) runItems.stream().filter(item -> StringUtils.hasText(item.getErrorMessage())).count();
        int evaluated = total - failed;
        run.setEvaluatedSamples(evaluated);
        run.setFailedSamples(failed);
        run.setHitAt1(computeHitAtK(runItems, 1));
        run.setHitAt3(computeHitAtK(runItems, 3));
        run.setHitAt5(computeHitAtK(runItems, 5));
        run.setRecallAt1(average(runItems.stream().map(RagEvalRunItem::getRecallAt1).toList()));
        run.setRecallAt3(average(runItems.stream().map(RagEvalRunItem::getRecallAt3).toList()));
        run.setRecallAt5(average(runItems.stream().map(RagEvalRunItem::getRecallAt5).toList()));
        run.setMrr(average(runItems.stream().map(RagEvalRunItem::getReciprocalRank).toList()));
        run.setAvgLatencyMs(average(runItems.stream()
                .map(RagEvalRunItem::getLatencyMs)
                .filter(Objects::nonNull)
                .map(Long::doubleValue)
                .toList()));
        run.setStatus(failed == 0 ? "SUCCESS" : evaluated > 0 ? "PARTIAL_FAILED" : "FAILED");
        run.setFinishedAt(LocalDateTime.now());
        if (failed > 0) {
            run.setErrorMessage("有 " + failed + " 条样本评测失败，请查看运行明细");
        }
        runMapper.updateById(run);
    }

    private List<RagEvalSample> selectRunSamples(Long userId, Long datasetId, List<Long> sampleIds) {
        return sampleMapper.selectList(new LambdaQueryWrapper<RagEvalSample>()
                .eq(RagEvalSample::getDatasetId, datasetId)
                .eq(RagEvalSample::getUserId, userId)
                .in(!CollectionUtils.isEmpty(sampleIds), RagEvalSample::getId, sampleIds)
                .orderByAsc(RagEvalSample::getId));
    }

    private Integer findHitRank(
            List<RetrievedSegment> retrieved,
            List<Long> expectedSegmentIds,
            List<Integer> expectedPageNos
    ) {
        Set<Long> expectedSegmentSet = new HashSet<>(expectedSegmentIds);
        Set<Integer> expectedPageSet = new HashSet<>(expectedPageNos);
        for (int index = 0; index < retrieved.size(); index++) {
            RetrievedSegment segment = retrieved.get(index);
            boolean segmentHit = !expectedSegmentSet.isEmpty() && expectedSegmentSet.contains(segment.segmentId());
            boolean pageHit = !expectedPageSet.isEmpty() && expectedPageSet.contains(segment.pageNo());
            if (segmentHit || pageHit) {
                return index + 1;
            }
        }
        return null;
    }

    private Double computeRecallAtK(
            List<RetrievedSegment> retrieved,
            List<Long> expectedSegmentIds,
            List<Integer> expectedPageNos,
            int k
    ) {
        List<RetrievedSegment> topK = retrieved.stream().limit(k).toList();
        Set<Long> expectedSegmentSet = new HashSet<>(expectedSegmentIds);
        if (!expectedSegmentSet.isEmpty()) {
            long hitCount = topK.stream()
                    .map(RetrievedSegment::segmentId)
                    .filter(expectedSegmentSet::contains)
                    .distinct()
                    .count();
            return expectedSegmentSet.isEmpty() ? 0D : (double) hitCount / expectedSegmentSet.size();
        }

        Set<Integer> expectedPageSet = new HashSet<>(expectedPageNos);
        if (expectedPageSet.isEmpty()) {
            return 0D;
        }
        boolean pageHit = topK.stream()
                .map(RetrievedSegment::pageNo)
                .filter(Objects::nonNull)
                .anyMatch(expectedPageSet::contains);
        return pageHit ? 1D : 0D;
    }

    private Double computeHitAtK(List<RagEvalRunItem> items, int k) {
        if (items.isEmpty()) {
            return 0D;
        }
        long hitCount = items.stream()
                .filter(item -> item.getHitRank() != null && item.getHitRank() <= k)
                .count();
        return (double) hitCount / items.size();
    }

    private Double average(List<Double> values) {
        List<Double> filtered = values.stream().filter(Objects::nonNull).toList();
        if (filtered.isEmpty()) {
            return 0D;
        }
        return filtered.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 20);
    }

    private void refreshSampleCount(RagEvalDataset dataset) {
        long count = sampleMapper.selectCount(new LambdaQueryWrapper<RagEvalSample>()
                .eq(RagEvalSample::getDatasetId, dataset.getId())
                .eq(RagEvalSample::getUserId, dataset.getUserId()));
        dataset.setSampleCount((int) count);
        datasetMapper.updateById(dataset);
    }

    private RagEvalDataset getUserOwnedDataset(Long userId, Long datasetId) {
        RagEvalDataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<RagEvalDataset>()
                .eq(RagEvalDataset::getId, datasetId)
                .eq(RagEvalDataset::getUserId, userId)
                .last("limit 1"));
        if (dataset == null) {
            throw new BusinessException(404, "评测集不存在");
        }
        return dataset;
    }

    private RagEvalSample getUserOwnedSample(Long userId, Long datasetId, Long sampleId) {
        RagEvalSample sample = sampleMapper.selectOne(new LambdaQueryWrapper<RagEvalSample>()
                .eq(RagEvalSample::getId, sampleId)
                .eq(RagEvalSample::getDatasetId, datasetId)
                .eq(RagEvalSample::getUserId, userId)
                .last("limit 1"));
        if (sample == null) {
            throw new BusinessException(404, "评测样本不存在");
        }
        return sample;
    }

    private StudyMaterial getUserOwnedMaterial(Long userId, Long materialId) {
        StudyMaterial material = studyMaterialService.getOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, materialId)
                .eq(StudyMaterial::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(404, "资料不存在");
        }
        return material;
    }

    private Map<Long, String> buildMaterialTitleMap(List<RagEvalDataset> datasets) {
        if (datasets == null || datasets.isEmpty()) {
            return Map.of();
        }
        Set<Long> materialIds = new LinkedHashSet<>();
        for (RagEvalDataset dataset : datasets) {
            if (dataset.getMaterialId() != null) {
                materialIds.add(dataset.getMaterialId());
            }
        }
        if (materialIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> titleMap = new HashMap<>();
        studyMaterialService.listByIds(materialIds)
                .forEach(material -> titleMap.put(material.getId(), material.getTitle()));
        return titleMap;
    }

    private RagEvalDatasetVO toDatasetVO(RagEvalDataset dataset, Map<Long, String> titleMap) {
        return RagEvalDatasetVO.builder()
                .id(dataset.getId())
                .materialId(dataset.getMaterialId())
                .materialTitle(titleMap.get(dataset.getMaterialId()))
                .name(dataset.getName())
                .description(dataset.getDescription())
                .status(dataset.getStatus())
                .sampleCount(dataset.getSampleCount())
                .lastRunId(dataset.getLastRunId())
                .lastRunAt(dataset.getLastRunAt())
                .createdAt(dataset.getCreatedAt())
                .build();
    }

    private RagEvalSampleVO toSampleVO(RagEvalSample sample) {
        StudyMaterial material = studyMaterialService.getById(sample.getMaterialId());
        return RagEvalSampleVO.builder()
                .id(sample.getId())
                .datasetId(sample.getDatasetId())
                .materialId(sample.getMaterialId())
                .materialTitle(material == null ? null : material.getTitle())
                .queryText(sample.getQueryText())
                .expectedSegmentIds(parseLongList(sample.getExpectedSegmentIds()))
                .expectedPageNos(parseIntList(sample.getExpectedPageNos()))
                .expectedKeywords(sample.getExpectedKeywords())
                .tag(sample.getTag())
                .difficulty(sample.getDifficulty())
                .sourceType(sample.getSourceType())
                .note(sample.getNote())
                .createdAt(sample.getCreatedAt())
                .build();
    }

    private RagEvalRunVO toRunVO(RagEvalRun run, List<RagEvalRunItem> items) {
        return RagEvalRunVO.builder()
                .id(run.getId())
                .datasetId(run.getDatasetId())
                .materialId(run.getMaterialId())
                .status(run.getStatus())
                .retrievalLimit(run.getRetrievalLimit())
                .totalSamples(run.getTotalSamples())
                .evaluatedSamples(run.getEvaluatedSamples())
                .failedSamples(run.getFailedSamples())
                .hitAt1(run.getHitAt1())
                .hitAt3(run.getHitAt3())
                .hitAt5(run.getHitAt5())
                .recallAt1(run.getRecallAt1())
                .recallAt3(run.getRecallAt3())
                .recallAt5(run.getRecallAt5())
                .mrr(run.getMrr())
                .avgLatencyMs(run.getAvgLatencyMs())
                .errorMessage(run.getErrorMessage())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .items(items.stream().map(this::toRunItemVO).toList())
                .build();
    }

    private RagEvalRunItemVO toRunItemVO(RagEvalRunItem item) {
        return RagEvalRunItemVO.builder()
                .id(item.getId())
                .sampleId(item.getSampleId())
                .materialId(item.getMaterialId())
                .queryText(item.getQueryText())
                .expectedSegmentIds(parseLongList(item.getExpectedSegmentIds()))
                .expectedPageNos(parseIntList(item.getExpectedPageNos()))
                .retrievedSegmentIds(parseLongList(item.getRetrievedSegmentIds()))
                .retrievedPageNos(parseIntList(item.getRetrievedPageNos()))
                .hitRank(item.getHitRank())
                .reciprocalRank(item.getReciprocalRank())
                .recallAt1(item.getRecallAt1())
                .recallAt3(item.getRecallAt3())
                .recallAt5(item.getRecallAt5())
                .latencyMs(item.getLatencyMs())
                .errorMessage(item.getErrorMessage())
                .build();
    }

    private String readUploadText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(500, "读取 CMRC2018 文件失败");
        }
    }

    private List<CmrcParagraph> parseCmrcParagraphs(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String normalizedContent = content.trim();
        try {
            if (looksLikeJsonLines(normalizedContent)) {
                return parseCmrcJsonLines(normalizedContent);
            }
            JsonNode root = objectMapper.readTree(normalizedContent);
            return parseCmrcJsonNode(root);
        } catch (Exception exception) {
            throw new BusinessException("解析 CMRC2018 JSON 失败: " + exception.getMessage());
        }
    }

    private boolean looksLikeJsonLines(String content) {
        if (content.startsWith("{") || content.startsWith("[")) {
            return false;
        }
        return content.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .map(line -> line.startsWith("{"))
                .orElse(false);
    }

    private List<CmrcParagraph> parseCmrcJsonLines(String content) throws JsonProcessingException {
        List<CmrcParagraph> paragraphs = new ArrayList<>();
        int index = 1;
        for (String line : content.lines().map(String::trim).filter(StringUtils::hasText).toList()) {
            JsonNode node = objectMapper.readTree(line);
            CmrcParagraph paragraph = parseFlatCmrcItem(node, "JSONL-" + index);
            if (paragraph != null) {
                paragraphs.add(paragraph);
            }
            index++;
        }
        return paragraphs;
    }

    private List<CmrcParagraph> parseCmrcJsonNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        if (root.has("data") && root.path("data").isArray()) {
            return parseOriginalCmrcData(root.path("data"));
        }
        if (root.isArray()) {
            List<CmrcParagraph> paragraphs = new ArrayList<>();
            int index = 1;
            for (JsonNode item : root) {
                CmrcParagraph paragraph = parseFlatCmrcItem(item, "ITEM-" + index);
                if (paragraph != null) {
                    paragraphs.add(paragraph);
                }
                index++;
            }
            return paragraphs;
        }
        CmrcParagraph paragraph = parseFlatCmrcItem(root, "ITEM-1");
        return paragraph == null ? List.of() : List.of(paragraph);
    }

    private List<CmrcParagraph> parseOriginalCmrcData(JsonNode dataNode) {
        List<CmrcParagraph> paragraphs = new ArrayList<>();
        for (JsonNode articleNode : dataNode) {
            String title = readText(articleNode, "title", "article_title");
            JsonNode paragraphsNode = articleNode.path("paragraphs");
            if (!paragraphsNode.isArray()) {
                continue;
            }
            int paragraphIndex = 1;
            for (JsonNode paragraphNode : paragraphsNode) {
                String context = readText(paragraphNode, "context");
                if (!StringUtils.hasText(context)) {
                    continue;
                }
                List<CmrcQuestion> questions = new ArrayList<>();
                JsonNode qasNode = paragraphNode.path("qas");
                if (qasNode.isArray()) {
                    for (JsonNode questionNode : qasNode) {
                        String question = readText(questionNode, "question", "query", "query_text");
                        if (!StringUtils.hasText(question)) {
                            continue;
                        }
                        String questionId = readText(questionNode, "id", "qid", "query_id");
                        questions.add(new CmrcQuestion(
                                questionId,
                                question.trim(),
                                readAnswerTexts(questionNode.path("answers"))
                        ));
                    }
                }
                paragraphs.add(new CmrcParagraph(
                        title,
                        "P" + paragraphIndex,
                        context.trim(),
                        questions
                ));
                paragraphIndex++;
            }
        }
        return paragraphs;
    }

    private CmrcParagraph parseFlatCmrcItem(JsonNode item, String fallbackId) {
        String context = readText(item, "context", "context_text", "paragraph", "passage", "text");
        if (!StringUtils.hasText(context)) {
            return null;
        }
        String title = readText(item, "title", "article_title");
        String paragraphId = readText(item, "context_id", "paragraph_id", "id");
        JsonNode qasNode = item.path("qas");
        if (qasNode.isArray()) {
            List<CmrcQuestion> questions = new ArrayList<>();
            for (JsonNode questionNode : qasNode) {
                String nestedQuestion = readText(questionNode, "question", "query", "query_text");
                if (!StringUtils.hasText(nestedQuestion)) {
                    continue;
                }
                String nestedQuestionId = readText(questionNode, "id", "qid", "query_id");
                questions.add(new CmrcQuestion(
                        StringUtils.hasText(nestedQuestionId) ? nestedQuestionId : fallbackId + "_Q" + (questions.size() + 1),
                        nestedQuestion.trim(),
                        readAnswerTexts(questionNode.path("answers"))
                ));
            }
            if (!questions.isEmpty()) {
                return new CmrcParagraph(title, StringUtils.hasText(paragraphId) ? paragraphId : fallbackId, context.trim(), questions);
            }
        }

        String question = readText(item, "question", "query", "query_text");
        if (!StringUtils.hasText(question)) {
            return null;
        }
        String questionId = readText(item, "id", "qid", "query_id");
        CmrcQuestion cmrcQuestion = new CmrcQuestion(
                StringUtils.hasText(questionId) ? questionId : fallbackId,
                question.trim(),
                readAnswerTexts(item.path("answers"))
        );
        return new CmrcParagraph(title, StringUtils.hasText(paragraphId) ? paragraphId : fallbackId, context.trim(), List.of(cmrcQuestion));
    }

    private List<String> readAnswerTexts(JsonNode answersNode) {
        if (answersNode == null || answersNode.isMissingNode() || answersNode.isNull()) {
            return List.of();
        }
        LinkedHashSet<String> answers = new LinkedHashSet<>();
        if (answersNode.isArray()) {
            for (JsonNode answerNode : answersNode) {
                if (answerNode.isTextual()) {
                    answers.add(answerNode.asText().trim());
                    continue;
                }
                String text = readText(answerNode, "text", "answer");
                if (StringUtils.hasText(text)) {
                    answers.add(text.trim());
                }
            }
        } else if (answersNode.isObject()) {
            JsonNode textNode = answersNode.path("text");
            if (textNode.isArray()) {
                for (JsonNode item : textNode) {
                    if (item.isTextual() && StringUtils.hasText(item.asText())) {
                        answers.add(item.asText().trim());
                    }
                }
            } else if (textNode.isTextual() && StringUtils.hasText(textNode.asText())) {
                answers.add(textNode.asText().trim());
            }
        }
        return answers.stream().filter(StringUtils::hasText).toList();
    }

    private String readText(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private List<CmrcSampleDraft> buildCmrcSampleDrafts(
            List<CmrcParagraph> paragraphs,
            LinkedHashMap<String, CmrcContextDraft> contextMap,
            int maxSamples
    ) {
        List<CmrcSampleDraft> sampleDrafts = new ArrayList<>();
        for (CmrcParagraph paragraph : paragraphs) {
            if (!StringUtils.hasText(paragraph.context()) || paragraph.questions().isEmpty()) {
                continue;
            }
            CmrcContextDraft contextDraft = contextMap.computeIfAbsent(
                    paragraph.context(),
                    key -> new CmrcContextDraft(paragraph.title(), paragraph.paragraphId(), paragraph.context())
            );
            for (CmrcQuestion question : paragraph.questions()) {
                if (sampleDrafts.size() >= maxSamples) {
                    return sampleDrafts;
                }
                sampleDrafts.add(new CmrcSampleDraft(
                        contextDraft,
                        question.id(),
                        question.question(),
                        question.answerTexts()
                ));
            }
        }
        return sampleDrafts;
    }

    private int resolveImportSampleLimit(Integer maxSamples) {
        if (maxSamples == null || maxSamples <= 0) {
            return 500;
        }
        return Math.min(maxSamples, 5000);
    }

    private String buildCmrcSectionTitle(String splitName, CmrcContextDraft contextDraft, int segmentNo) {
        String title = StringUtils.hasText(contextDraft.title()) ? contextDraft.title().trim() : "阅读材料";
        return "CMRC2018 " + splitName + " · #" + segmentNo + " · " + truncate(title, 80);
    }

    private String joinAnswers(List<String> answerTexts) {
        if (answerTexts == null || answerTexts.isEmpty()) {
            return null;
        }
        return truncate(String.join(",", answerTexts), 500);
    }

    private String buildCmrcSampleNote(String splitName, CmrcSampleDraft sampleDraft) {
        String idText = StringUtils.hasText(sampleDraft.questionId()) ? sampleDraft.questionId() : "无原始ID";
        return truncate("CMRC2018 " + splitName + " · 原始问题ID: " + idText, 500);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("JSON序列化失败");
        }
    }

    private List<Long> parseLongList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<Integer> parseIntList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String normalizeSourceType(String sourceType) {
        return StringUtils.hasText(sourceType) ? sourceType.trim().toUpperCase() : "HUMAN";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record CmrcParagraph(
            String title,
            String paragraphId,
            String context,
            List<CmrcQuestion> questions
    ) {
    }

    private record CmrcQuestion(
            String id,
            String question,
            List<String> answerTexts
    ) {
    }

    private record CmrcSampleDraft(
            CmrcContextDraft context,
            String questionId,
            String question,
            List<String> answerTexts
    ) {
    }

    private static class CmrcContextDraft {
        private final String title;
        private final String paragraphId;
        private final String context;
        private Long segmentId;

        private CmrcContextDraft(String title, String paragraphId, String context) {
            this.title = title;
            this.paragraphId = paragraphId;
            this.context = context;
        }

        private String title() {
            return title;
        }

        private String paragraphId() {
            return paragraphId;
        }

        private String context() {
            return context;
        }

        private Long segmentId() {
            return segmentId;
        }

        private void setSegmentId(Long segmentId) {
            this.segmentId = segmentId;
        }
    }
}
