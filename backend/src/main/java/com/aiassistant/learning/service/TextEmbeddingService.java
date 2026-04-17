package com.aiassistant.learning.service;

import java.util.List;

public interface TextEmbeddingService {

    EmbeddingBatchResult embedTexts(List<String> texts, String modelName);

    record EmbeddingBatchResult(
            String modelName,
            List<List<Double>> vectors
    ) {
    }
}
