package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.QdrantProperties;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.TextEmbeddingService;
import com.aiassistant.learning.service.VectorStoreService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RetrievalServiceImpl implements RetrievalService {

    private final StudyMaterialService studyMaterialService;
    private final TextEmbeddingService textEmbeddingService;
    private final VectorStoreService vectorStoreService;
    private final QdrantProperties qdrantProperties;

    public RetrievalServiceImpl(
            StudyMaterialService studyMaterialService,
            TextEmbeddingService textEmbeddingService,
            VectorStoreService vectorStoreService,
            QdrantProperties qdrantProperties
    ) {
        this.studyMaterialService = studyMaterialService;
        this.textEmbeddingService = textEmbeddingService;
        this.vectorStoreService = vectorStoreService;
        this.qdrantProperties = qdrantProperties;
    }

    @Override
    public List<RetrievedSegment> retrieveMaterialSegments(Long userId, Long materialId, String queryText, Integer limit) {
        ensureQdrantReady();
        if (!StringUtils.hasText(queryText)) {
            throw new BusinessException("检索语句不能为空");
        }

        StudyMaterial material = studyMaterialService.getOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, materialId)
                .eq(StudyMaterial::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(404, "资料不存在");
        }

        List<Double> queryVector = textEmbeddingService.embedTexts(List.of(queryText.trim()), null).vectors().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("未生成检索向量"));

        int resolvedLimit = resolveLimit(limit);
        return vectorStoreService.searchMaterialSegments(userId, materialId, queryVector, resolvedLimit);
    }

    private void ensureQdrantReady() {
        if (!Boolean.TRUE.equals(qdrantProperties.getEnabled())) {
            throw new BusinessException("Qdrant 未启用");
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit != null && limit > 0) {
            return limit;
        }
        Integer configuredLimit = qdrantProperties.getRetrievalLimit();
        return configuredLimit == null || configuredLimit <= 0 ? 6 : configuredLimit;
    }
}
