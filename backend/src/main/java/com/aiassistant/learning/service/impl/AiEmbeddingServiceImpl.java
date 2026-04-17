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

    public AiEmbeddingServiceImpl(
            StudyMaterialMapper studyMaterialMapper,
            MaterialSegmentMapper materialSegmentMapper
    ) {
        this.studyMaterialMapper = studyMaterialMapper;
        this.materialSegmentMapper = materialSegmentMapper;
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
            throw new BusinessException(404, "Study material not found");
        }
        if (!PARSE_STATUS_SUCCESS.equalsIgnoreCase(material.getParseStatus())) {
            throw new BusinessException("Material parsing is not finished yet");
        }

        List<MaterialSegment> segments = materialSegmentMapper.selectList(new LambdaQueryWrapper<MaterialSegment>()
                .eq(MaterialSegment::getMaterialId, materialId)
                .orderByAsc(MaterialSegment::getSegmentNo));
        if (segments.isEmpty()) {
            throw new BusinessException("No parsed segments found for this material");
        }

        String resolvedModel = StringUtils.hasText(modelName) ? modelName.trim() : DEFAULT_EMBEDDING_MODEL;
        boolean force = Boolean.TRUE.equals(forceRegenerate);
        int queuedSegments = 0;
        int skippedSegments = 0;

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
}
