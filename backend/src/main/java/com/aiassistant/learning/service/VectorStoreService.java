package com.aiassistant.learning.service;

import java.util.List;

public interface VectorStoreService {

    void upsertMaterialSegments(List<MaterialSegmentVector> segmentVectors);

    void deleteMaterialSegments(Long userId, Long materialId);

    List<RetrievedSegment> searchMaterialSegments(Long userId, Long materialId, List<Double> queryVector, int limit);

    record MaterialSegmentVector(
            Long segmentId,
            Long userId,
            Long materialId,
            Integer segmentNo,
            Integer pageNo,
            String sectionTitle,
            String contentText,
            String keywords,
            String modelName,
            List<Double> vector
    ) {
    }

    record RetrievedSegment(
            Long segmentId,
            Integer segmentNo,
            Integer pageNo,
            String sectionTitle,
            String contentText,
            String keywords,
            Double score
    ) {
    }
}
