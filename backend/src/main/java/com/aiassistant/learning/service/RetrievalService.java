package com.aiassistant.learning.service;

import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import java.util.List;

/**
 * RAG 检索服务接口。
 */
public interface RetrievalService {

    /**
     * 在指定资料范围内检索与问题最相关的分段。
     *
     * @param userId 当前用户 ID，用于确认资料归属
     * @param materialId 要检索的资料 ID
     * @param queryText 查询文本
     * @param limit 最多返回多少条结果，为空时使用系统默认值
     * @return 按相关性排序后的资料分段
     */
    List<RetrievedSegment> retrieveMaterialSegments(Long userId, Long materialId, String queryText, Integer limit);
}
