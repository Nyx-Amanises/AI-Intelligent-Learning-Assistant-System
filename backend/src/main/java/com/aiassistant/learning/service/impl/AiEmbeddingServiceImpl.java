package com.aiassistant.learning.service.impl;

import lombok.extern.slf4j.Slf4j;
import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.QdrantProperties;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiEmbeddingService;
import com.aiassistant.learning.service.TextEmbeddingService;
import com.aiassistant.learning.service.VectorStoreService;
import com.aiassistant.learning.vo.ai.EmbeddingTaskResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 学习资料向量化服务实现类。
 *
 * <p>它会读取资料分段，调用 Embedding 模型生成向量，再写入向量库。</p>
 */
@Slf4j
@Service
public class AiEmbeddingServiceImpl implements AiEmbeddingService {

    /** 只有解析成功的资料才能向量化。 */
    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    /** 分段已进入向量化队列。 */
    private static final String EMBEDDING_STATUS_QUEUED = "QUEUED";
    /** 分段向量化成功。 */
    private static final String EMBEDDING_STATUS_SUCCESS = "SUCCESS";
    /** 分段向量化失败。 */
    private static final String EMBEDDING_STATUS_FAILED = "FAILED";

    /** 学习资料 Mapper。 */
    private final StudyMaterialMapper studyMaterialMapper;
    /** 资料分段 Mapper。 */
    private final MaterialSegmentMapper materialSegmentMapper;
    /** AI 配置服务，用于解析默认 Embedding 模型。 */
    private final AiConfigService aiConfigService;
    /** 文本向量生成服务。 */
    private final TextEmbeddingService textEmbeddingService;
    /** 向量库服务。 */
    private final VectorStoreService vectorStoreService;
    /** Qdrant 配置。 */
    private final QdrantProperties qdrantProperties;
    /** 用于检查数据库字段是否已经迁移。 */
    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;

    public AiEmbeddingServiceImpl(
            StudyMaterialMapper studyMaterialMapper,
            MaterialSegmentMapper materialSegmentMapper,
            AiConfigService aiConfigService,
            TextEmbeddingService textEmbeddingService,
            VectorStoreService vectorStoreService,
            QdrantProperties qdrantProperties,
            DataSource dataSource,
            PlatformTransactionManager transactionManager
    ) {
        this.studyMaterialMapper = studyMaterialMapper;
        this.materialSegmentMapper = materialSegmentMapper;
        this.aiConfigService = aiConfigService;
        this.textEmbeddingService = textEmbeddingService;
        this.vectorStoreService = vectorStoreService;
        this.qdrantProperties = qdrantProperties;
        this.dataSource = dataSource;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 执行资料向量化。
     */
    @Override
    public EmbeddingTaskResultVO prepareMaterialEmbedding(
            Long userId,
            Long materialId,
            String modelName,
            Boolean forceRegenerate,
            Long taskId
    ) {
        EmbeddingAttempt attempt = new EmbeddingAttempt(userId, materialId, taskId);
        EmbeddingTaskResultVO result;
        try {
            result = transactionTemplate.execute(status -> executeEmbedding(
                    attempt, modelName, forceRegenerate, status));
        } catch (RuntimeException exception) {
            if (!attempt.started || attempt.pending.isEmpty()) throw exception;
            // execute 已释放原事务和资料锁，此时才能开启独立事务补偿，避免等待自己持有的锁。
            throw compensateAfterTransactionFailure(attempt, exception);
        }
        // FAILED 状态已提交后再向任务处理器抛错，不能让错误状态跟随业务异常一起回滚。
        if (attempt.failure != null) throw attempt.failure;
        return result;
    }

    private EmbeddingTaskResultVO executeEmbedding(EmbeddingAttempt attempt, String modelName,
            Boolean forceRegenerate, TransactionStatus status) {
        Long userId = attempt.userId;
        Long materialId = attempt.materialId;
        Long taskId = attempt.taskId;
        StudyMaterial material = lockOwnedMaterial(userId, materialId);
        if (material == null) {
            throw new BusinessException(404, "资料不存在");
        }
        if (!PARSE_STATUS_SUCCESS.equalsIgnoreCase(material.getParseStatus())) {
            throw new BusinessException("资料解析尚未完成");
        }

        List<MaterialSegment> segments = materialSegmentMapper.selectList(segmentQuery(materialId));
        if (segments.isEmpty()) {
            throw new BusinessException("当前资料还没有可用于向量化的分段内容");
        }
        ensureEmbeddingColumnsReady();

        String resolvedModel = resolveEmbeddingModel(modelName);
        attempt.modelName = resolvedModel;
        String targetMode = Boolean.TRUE.equals(aiConfigService.getResolvedConfig().mockMode()) ? "MOCK" : "LIVE";
        boolean force = Boolean.TRUE.equals(forceRegenerate);
        int skippedSegments = 0;
        List<MaterialSegment> pendingSegments = attempt.pending;

        for (MaterialSegment segment : segments) {
            boolean alreadyEmbedded = EMBEDDING_STATUS_SUCCESS.equalsIgnoreCase(segment.getEmbeddingStatus())
                    && StringUtils.hasText(segment.getVectorId())
                    && resolvedModel.equals(segment.getEmbeddingModel())
                    && targetMode.equals(segment.getEmbeddingMode());
            if (alreadyEmbedded && !force) {
                skippedSegments++;
                continue;
            }

            attempt.before.put(segment.getId(), SegmentState.from(segment));
            pendingSegments.add(segment);
        }

        int storedSegments = 0;
        Object savepoint = status.createSavepoint();
        attempt.started = true;
        try {
            for (MaterialSegment segment : pendingSegments) {
                segment.setEmbeddingStatus(EMBEDDING_STATUS_QUEUED);
                segment.setEmbeddingModel(resolvedModel);
                segment.setEmbeddingMode("UNKNOWN");
                segment.setEmbeddingTaskId(taskId);
                segment.setEmbeddingAttemptId(attempt.id);
                segment.setVectorId(null);
                segment.setEmbeddedAt(null);
                persistIndexState(segment);
            }
            int batchSize = resolveBatchSize();
            for (int start = 0; start < pendingSegments.size(); start += batchSize) {
                // 分批向量化，避免一次请求内容过多导致模型接口超限。
                int end = Math.min(start + batchSize, pendingSegments.size());
                List<MaterialSegment> batch = pendingSegments.subList(start, end);
                List<String> texts = batch.stream()
                        .map(MaterialSegment::getContentText)
                        .toList();

                TextEmbeddingService.EmbeddingBatchResult embeddingBatch = textEmbeddingService.embedTexts(texts, resolvedModel);
                List<List<Double>> vectors = embeddingBatch.vectors();
                if (vectors.size() != batch.size()) {
                    throw new BusinessException("Embedding 向量数量与资料分段数量不一致");
                }

                List<VectorStoreService.MaterialSegmentVector> vectorBatch = new ArrayList<>(batch.size());
                for (int index = 0; index < batch.size(); index++) {
                    MaterialSegment segment = batch.get(index);
                    vectorBatch.add(new VectorStoreService.MaterialSegmentVector(
                            segment.getId(),
                            userId,
                            materialId,
                            segment.getSegmentNo(),
                            segment.getPageNo(),
                            segment.getSectionTitle(),
                            segment.getContentText(),
                            segment.getKeywords(),
                            embeddingBatch.modelName(),
                            attempt.id,
                            vectors.get(index)
                    ));
                }

                // 先写入向量库，再把数据库分段状态标记为 SUCCESS。
                vectorStoreService.upsertMaterialSegments(vectorBatch);

                LocalDateTime embeddedAt = LocalDateTime.now();
                for (MaterialSegment segment : batch) {
                    segment.setEmbeddingStatus(EMBEDDING_STATUS_SUCCESS);
                    segment.setEmbeddingModel(embeddingBatch.modelName());
                    segment.setEmbeddingMode(embeddingBatch.mock() ? "MOCK" : "LIVE");
                    segment.setEmbeddingTaskId(taskId);
                    segment.setVectorId(String.valueOf(segment.getId()));
                    segment.setEmbeddedAt(embeddedAt);
                    persistIndexState(segment);
                    storedSegments++;
                }
            }
        } catch (RuntimeException exception) {
            log.error("资料向量化失败 taskId={} model={}: {}", taskId, resolvedModel, exception.getMessage(), exception);
            status.rollbackToSavepoint(savepoint);
            String cleanupWarning = cleanupVectors(attempt, pendingSegments.stream().map(MaterialSegment::getId).toList(), null);
            markPendingSegmentsFailed(pendingSegments, attempt);
            attempt.failure = processingFailure(exception, cleanupWarning);
            return null;
        } finally {
            status.releaseSavepoint(savepoint);
        }

        return EmbeddingTaskResultVO.builder()
                .materialId(material.getId())
                .materialTitle(material.getTitle())
                .modelName(resolvedModel)
                .embeddingMode(resolveResultMode(segments))
                .totalSegments(segments.size())
                .queuedSegments(pendingSegments.size())
                .skippedSegments(skippedSegments)
                .storedSegments(storedSegments)
                .collectionName(qdrantProperties.getCollectionName())
                .vectorStoreReady(Boolean.TRUE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String resolveResultMode(List<MaterialSegment> segments) {
        List<String> modes = segments.stream().map(MaterialSegment::getEmbeddingMode).distinct().toList();
        if (modes.size() == 1 && ("MOCK".equals(modes.get(0)) || "LIVE".equals(modes.get(0)))) {
            return modes.get(0);
        }
        return modes.stream().anyMatch(mode -> mode == null || "UNKNOWN".equals(mode)) ? "UNKNOWN" : "MIXED";
    }

    /**
     * 一次执行整体失败时，已写入批次和未开始批次都失效，保留本次跳过的旧索引。
     */
    private void markPendingSegmentsFailed(List<MaterialSegment> pendingSegments, EmbeddingAttempt attempt) {
        for (MaterialSegment segment : pendingSegments) {
            segment.setEmbeddingStatus(EMBEDDING_STATUS_FAILED);
            segment.setEmbeddingModel(attempt.modelName);
            segment.setEmbeddingMode("UNKNOWN");
            segment.setEmbeddingTaskId(attempt.taskId);
            segment.setEmbeddingAttemptId(attempt.id);
            segment.setVectorId(null);
            segment.setEmbeddedAt(null);
            persistIndexState(segment);
        }
    }

    private void persistIndexState(MaterialSegment segment) {
        int updated = materialSegmentMapper.update(null, new LambdaUpdateWrapper<MaterialSegment>()
                .eq(MaterialSegment::getId, segment.getId())
                .eq(MaterialSegment::getMaterialId, segment.getMaterialId())
                .set(MaterialSegment::getEmbeddingStatus, segment.getEmbeddingStatus())
                .set(MaterialSegment::getEmbeddingModel, segment.getEmbeddingModel())
                .set(MaterialSegment::getEmbeddingMode, segment.getEmbeddingMode())
                .set(MaterialSegment::getEmbeddingTaskId, segment.getEmbeddingTaskId())
                .set(MaterialSegment::getEmbeddingAttemptId, segment.getEmbeddingAttemptId())
                .set(MaterialSegment::getVectorId, segment.getVectorId())
                .set(MaterialSegment::getEmbeddedAt, segment.getEmbeddedAt()));
        if (updated != 1) throw new BusinessException(409, "资料分段已变化，请刷新后重试向量化");
    }

    private RuntimeException compensateAfterTransactionFailure(EmbeddingAttempt attempt, RuntimeException original) {
        try {
            return transactionTemplate.execute(status -> {
                StudyMaterial material = lockOwnedMaterial(attempt.userId, attempt.materialId);
                List<MaterialSegment> eligible = material == null ? List.of()
                        : materialSegmentMapper.selectList(segmentQuery(attempt.materialId)
                                .in(MaterialSegment::getId, attempt.before.keySet())).stream()
                        .filter(segment -> attempt.before.containsKey(segment.getId()))
                        .filter(segment -> attempt.id.equals(segment.getEmbeddingAttemptId())
                                || attempt.before.get(segment.getId()).equals(SegmentState.from(segment)))
                        .toList();
                // 重新持锁后，只有本次状态或尚未被后来尝试改变的原状态可以失效。
                String warning = cleanupVectors(attempt, eligible.stream().map(MaterialSegment::getId).toList(), null);
                // 同时清除分段被重新解析/删除后可能遗留的本次写入，不能匹配后来任务的 payload。
                String orphanWarning = cleanupVectors(attempt, attempt.pending.stream().map(MaterialSegment::getId).toList(), attempt.id);
                markPendingSegmentsFailed(eligible, attempt);
                return processingFailure(original, joinWarnings("数据库提交未完成，请重试向量化", warning, orphanWarning));
            });
        } catch (RuntimeException compensationError) {
            log.error("向量化失败状态补偿未完成 attemptId={}", attempt.id, compensationError);
            String warning = cleanupVectors(attempt, attempt.pending.stream().map(MaterialSegment::getId).toList(), attempt.id);
            return processingFailure(original, joinWarnings("失败状态保存未完成，请重试向量化；不能确认索引已清理", warning));
        }
    }

    private StudyMaterial lockOwnedMaterial(Long userId, Long materialId) {
        return studyMaterialMapper.selectOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, materialId).eq(StudyMaterial::getUserId, userId)
                .last("limit 1 for update"));
    }

    private LambdaQueryWrapper<MaterialSegment> segmentQuery(Long materialId) {
        return new LambdaQueryWrapper<MaterialSegment>()
                .select(MaterialSegment::getId, MaterialSegment::getMaterialId, MaterialSegment::getSegmentNo,
                        MaterialSegment::getPageNo, MaterialSegment::getSectionTitle, MaterialSegment::getContentText,
                        MaterialSegment::getKeywords, MaterialSegment::getEmbeddingStatus, MaterialSegment::getEmbeddingModel,
                        MaterialSegment::getEmbeddingMode, MaterialSegment::getVectorId, MaterialSegment::getEmbeddedAt,
                        MaterialSegment::getEmbeddingTaskId, MaterialSegment::getEmbeddingAttemptId)
                .eq(MaterialSegment::getMaterialId, materialId).orderByAsc(MaterialSegment::getSegmentNo);
    }

    private String cleanupVectors(EmbeddingAttempt attempt, List<Long> ids, String onlyAttemptId) {
        if (ids.isEmpty()) return null;
        try {
            vectorStoreService.deleteSegmentVectors(attempt.userId, attempt.materialId, ids, onlyAttemptId);
            return null;
        } catch (RuntimeException exception) {
            log.error("向量化补偿清理失败 attemptId={} segmentIds={}", attempt.id, ids, exception);
            return "向量清理未完成，可能残留部分索引，请重试向量化";
        }
    }

    private RuntimeException processingFailure(RuntimeException original, String warning) {
        if (!StringUtils.hasText(warning)) return original;
        BusinessException failure = new BusinessException(500,
                Objects.toString(original.getMessage(), "资料向量化失败") + "；" + warning);
        failure.initCause(original);
        return failure;
    }

    private String joinWarnings(String... warnings) {
        return java.util.Arrays.stream(warnings).filter(StringUtils::hasText).distinct()
                .collect(java.util.stream.Collectors.joining("；"));
    }

    private static class EmbeddingAttempt {
        private final String id = UUID.randomUUID().toString();
        private final Long userId;
        private final Long materialId;
        private final Long taskId;
        private final List<MaterialSegment> pending = new ArrayList<>();
        private final Map<Long, SegmentState> before = new LinkedHashMap<>();
        private String modelName;
        private boolean started;
        private RuntimeException failure;

        private EmbeddingAttempt(Long userId, Long materialId, Long taskId) {
            this.userId = userId;
            this.materialId = materialId;
            this.taskId = taskId;
        }
    }

    private record SegmentState(String status, String model, String mode, Long taskId,
            String attemptId, String vectorId, LocalDateTime embeddedAt) {
        private static SegmentState from(MaterialSegment segment) {
            return new SegmentState(segment.getEmbeddingStatus(), segment.getEmbeddingModel(), segment.getEmbeddingMode(),
                    segment.getEmbeddingTaskId(), segment.getEmbeddingAttemptId(), segment.getVectorId(), segment.getEmbeddedAt());
        }
    }

    /**
     * 解析本次要使用的 Embedding 模型名称。
     */
    private String resolveEmbeddingModel(String modelName) {
        if (StringUtils.hasText(modelName)) {
            return modelName.trim();
        }
        String defaultEmbeddingModel = aiConfigService.getResolvedConfig().defaultEmbeddingModel();
        if (!StringUtils.hasText(defaultEmbeddingModel)) {
            throw new BusinessException("未配置默认 Embedding 模型");
        }
        return defaultEmbeddingModel.trim();
    }

    /**
     * 获取写入向量库时的批量大小。
     */
    private int resolveBatchSize() {
        Integer batchSize = qdrantProperties.getUpsertBatchSize();
        return batchSize == null || batchSize <= 0 ? 16 : batchSize;
    }

    /**
     * 向量化依赖新增字段，这里提前检查数据库迁移是否已经执行。
     */
    private void ensureEmbeddingColumnsReady() {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            java.sql.DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            if ((columnExists(metaData, catalog, "material_segment", "embedding_model")
                    || columnExists(metaData, catalog, "MATERIAL_SEGMENT", "EMBEDDING_MODEL"))
                    && (columnExists(metaData, catalog, "material_segment", "embedding_mode")
                    || columnExists(metaData, catalog, "MATERIAL_SEGMENT", "EMBEDDING_MODE"))
                    && (columnExists(metaData, catalog, "material_segment", "embedding_attempt_id")
                    || columnExists(metaData, catalog, "MATERIAL_SEGMENT", "EMBEDDING_ATTEMPT_ID"))) {
                return;
            }
        } catch (java.sql.SQLException exception) {
            throw new BusinessException(500, "检查 material_segment 表结构失败: " + exception.getMessage());
        }
        throw new BusinessException(
                "Embedding 相关字段尚未就绪，请先完成数据库迁移 V17"
        );
    }

    /**
     * 判断指定表字段是否存在。
     */
    private boolean columnExists(
            java.sql.DatabaseMetaData metaData,
            String catalog,
            String tableName,
            String columnName
    ) throws java.sql.SQLException {
        try (java.sql.ResultSet resultSet = metaData.getColumns(catalog, null, tableName, columnName)) {
            return resultSet.next();
        }
    }
}
