package com.aiassistant.learning.service;

import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import java.util.List;

public interface RetrievalService {

    List<RetrievedSegment> retrieveMaterialSegments(Long userId, Long materialId, String queryText, Integer limit);
}
