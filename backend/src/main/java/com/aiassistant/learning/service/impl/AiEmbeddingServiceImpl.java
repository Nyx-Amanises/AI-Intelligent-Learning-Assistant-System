package com.aiassistant.learning.service.impl;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 学习资料向量化服务实现类。
 *
 * <p>它会读取资料分段，调用 Embedding 模型生成向量，再写入向量库。</p>
 */
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

    public AiEmbeddingServiceImpl(
            StudyMaterialMapper studyMaterialMapper,
            MaterialSegmentMapper materialSegmentMapper,
            AiConfigService aiConfigService,
            TextEmbeddingService textEmbeddingService,
            VectorStoreService vectorStoreService,
            QdrantProperties qdrantProperties,
            DataSource dataSource
    ) {
        this.studyMaterialMapper = studyMaterialMapper;
        this.materialSegmentMapper = materialSegmentMapper;
        this.aiConfigService = aiConfigService;
        this.textEmbeddingService = textEmbeddingService;
        this.vectorStoreService = vectorStoreService;
        this.qdrantProperties = qdrantProperties;
        this.dataSource = dataSource;
    }

    /**
     * 执行资料向量化。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmbeddingTaskResultVO prepareMaterialEmbedding(
            Long userId,
            Long materialId,
            String modelName,
            Boolean forceRegenerate,
            Long taskId
    ) {
        StudyMaterial material = studyMaterialMapper.selectOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, materialId)
                .eq(StudyMaterial::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(404, "资料不存在");
        }
        if (!PARSE_STATUS_SUCCESS.equalsIgnoreCase(material.getParseStatus())) {
            throw new BusinessException("资料解析尚未完成");
        }

        List<MaterialSegment> segments = materialSegmentMapper.selectList(new LambdaQueryWrapper<MaterialSegment>()
                .eq(MaterialSegment::getMaterialId, materialId)
                .orderByAsc(MaterialSegment::getSegmentNo));
        if (segments.isEmpty()) {
            throw new BusinessException("当前资料还没有可用于向量化的分段内容");
        }
        ensureEmbeddingColumnsReady();

        String resolvedModel = resolveEmbeddingModel(modelName);
        boolean force = Boolean.TRUE.equals(forceRegenerate);
        int skippedSegments = 0;
        List<MaterialSegment> pendingSegments = new ArrayList<>();

        for (MaterialSegment segment : segments) {
            boolean alreadyEmbedded = EMBEDDING_STATUS_SUCCESS.equalsIgnoreCase(segment.getEmbeddingStatus())
                    && StringUtils.hasText(segment.getVectorId());
            if (alreadyEmbedded && !force) {
                skippedSegments++;
                continue;
            }

            segment.setEmbeddingStatus(EMBEDDING_STATUS_QUEUED);
            segment.setEmbeddingModel(resolvedModel);
            segment.setEmbeddingTaskId(taskId);
            if (force) {
                segment.setVectorId(null);
                segment.setEmbeddedAt(null);
            }
            materialSegmentMapper.updateById(segment);
            pendingSegments.add(segment);
        }

        int storedSegments = 0;
        try {
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
                            vectors.get(index)
                    ));
                }

                // 先写入向量库，再把数据库分段状态标记为 SUCCESS。
                vectorStoreService.upsertMaterialSegments(vectorBatch);

                LocalDateTime embeddedAt = LocalDateTime.now();
                for (MaterialSegment segment : batch) {
                    segment.setEmbeddingStatus(EMBEDDING_STATUS_SUCCESS);
                    segment.setEmbeddingModel(embeddingBatch.modelName());
                    segment.setEmbeddingTaskId(taskId);
                    segment.setVectorId(String.valueOf(segment.getId()));
                    segment.setEmbeddedAt(embeddedAt);
                    materialSegmentMapper.updateById(segment);
                    storedSegments++;
                }
            }
        } catch (Exception exception) {
            markPendingSegmentsFailed(pendingSegments, resolvedModel, taskId);
            throw exception;
        }

        return EmbeddingTaskResultVO.builder()
                .materialId(material.getId())
                .materialTitle(material.getTitle())
                .modelName(resolvedModel)
                .totalSegments(segments.size())
                .queuedSegments(pendingSegments.size())
                .skippedSegments(skippedSegments)
                .storedSegments(storedSegments)
                .collectionName(qdrantProperties.getCollectionName())
                .vectorStoreReady(Boolean.TRUE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 如果某批向量化失败，把还没成功的分段标记为失败。
     */
    private void markPendingSegmentsFailed(List<MaterialSegment> pendingSegments, String modelName, Long taskId) {
        for (MaterialSegment segment : pendingSegments) {
            if (EMBEDDING_STATUS_SUCCESS.equalsIgnoreCase(segment.getEmbeddingStatus())) {
                continue;
            }
            segment.setEmbeddingStatus(EMBEDDING_STATUS_FAILED);
            segment.setEmbeddingModel(modelName);
            segment.setEmbeddingTaskId(taskId);
            materialSegmentMapper.updateById(segment);
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
            if (columnExists(metaData, catalog, "material_segment", "embedding_model")
                    || columnExists(metaData, catalog, "MATERIAL_SEGMENT", "EMBEDDING_MODEL")) {
                return;
            }
        } catch (java.sql.SQLException exception) {
            throw new BusinessException(500, "检查 material_segment 表结构失败: " + exception.getMessage());
        }
        throw new BusinessException(
                "Embedding 相关字段尚未就绪，请先执行 db/migrations/2026-04-17_add_material_segment_embedding_fields.sql"
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
