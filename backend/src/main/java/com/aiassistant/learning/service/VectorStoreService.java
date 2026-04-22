package com.aiassistant.learning.service;

import java.util.List;

/**
 * 向量存储服务接口。
 *
 * <p>当前实现通常会对接 Qdrant 之类的向量数据库。</p>
 */
public interface VectorStoreService {

    /**
     * 写入或更新学习资料切片向量。
     */
    void upsertMaterialSegments(List<MaterialSegmentVector> segmentVectors);

    /**
     * 删除某份资料的所有切片向量。
     */
    void deleteMaterialSegments(Long userId, Long materialId);

    /**
     * 根据查询向量召回相似资料切片。
     */
    List<RetrievedSegment> searchMaterialSegments(Long userId, Long materialId, List<Double> queryVector, int limit);

    /**
     * 写入向量库时使用的资料切片向量。
     */
    record MaterialSegmentVector(
            /** 切片 ID。 */
            Long segmentId,
            /** 用户 ID，用于隔离不同用户的数据。 */
            Long userId,
            /** 学习资料 ID。 */
            Long materialId,
            /** 切片序号。 */
            Integer segmentNo,
            /** 页码，可能为空。 */
            Integer pageNo,
            /** 章节标题，帮助检索结果展示上下文。 */
            String sectionTitle,
            /** 切片正文。 */
            String contentText,
            /** 切片关键词。 */
            String keywords,
            /** 生成向量所用模型。 */
            String modelName,
            /** 文本向量。 */
            List<Double> vector
    ) {
    }

    /**
     * 从向量库召回的相似切片。
     */
    record RetrievedSegment(
            /** 切片 ID。 */
            Long segmentId,
            /** 切片序号。 */
            Integer segmentNo,
            /** 页码。 */
            Integer pageNo,
            /** 章节标题。 */
            String sectionTitle,
            /** 切片正文。 */
            String contentText,
            /** 关键词。 */
            String keywords,
            /** 相似度分数，越高通常表示越相关。 */
            Double score
    ) {
    }
}
