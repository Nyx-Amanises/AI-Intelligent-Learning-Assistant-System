package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.service.AiEmbeddingService;
import com.aiassistant.learning.vo.ai.EmbeddingTaskResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiEmbeddingServiceImpl implements AiEmbeddingService {

    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String EMBEDDING_STATUS_QUEUED = "QUEUED";
    private static final String EMBEDDING_STATUS_SUCCESS = "SUCCESS";

    private final StudyMaterialMapper studyMaterialMapper;
    private final MaterialSegmentMapper materialSegmentMapper;
    private final DataSource dataSource;

    public AiEmbeddingServiceImpl(
            StudyMaterialMapper studyMaterialMapper,
            MaterialSegmentMapper materialSegmentMapper,
            DataSource dataSource
    ) {
        this.studyMaterialMapper = studyMaterialMapper;
        this.materialSegmentMapper = materialSegmentMapper;
        this.dataSource = dataSource;
    }

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

        String resolvedModel = StringUtils.hasText(modelName) ? modelName.trim() : DEFAULT_EMBEDDING_MODEL;
        boolean force = Boolean.TRUE.equals(forceRegenerate);
        int queuedSegments = 0;
        int skippedSegments = 0;

        for (MaterialSegment segment : segments) {
            boolean alreadyEmbedded = EMBEDDING_STATUS_SUCCESS.equalsIgnoreCase(segment.getEmbeddingStatus());
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
            queuedSegments++;
        }

        return EmbeddingTaskResultVO.builder()
                .materialId(material.getId())
                .materialTitle(material.getTitle())
                .modelName(resolvedModel)
                .totalSegments(segments.size())
                .queuedSegments(queuedSegments)
                .skippedSegments(skippedSegments)
                .vectorStoreReady(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

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
