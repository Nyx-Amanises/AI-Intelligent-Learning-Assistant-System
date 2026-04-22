package com.aiassistant.learning.service;

import java.util.List;

/**
 * 文本向量生成服务接口。
 */
public interface TextEmbeddingService {

    /**
     * 批量把文本转换成向量。
     */
    EmbeddingBatchResult embedTexts(List<String> texts, String modelName);

    /**
     * Embedding 批量生成结果。
     */
    record EmbeddingBatchResult(
            /** 实际使用的模型名称。 */
            String modelName,
            /** 每段文本对应一个向量，顺序和输入 texts 保持一致。 */
            List<List<Double>> vectors
    ) {
    }
}
