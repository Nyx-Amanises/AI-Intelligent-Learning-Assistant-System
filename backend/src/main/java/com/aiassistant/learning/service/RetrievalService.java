package com.aiassistant.learning.service;

import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import java.util.List;

/**
 * RAG 检索服务接口。
 */
public interface RetrievalService {

    /** 检索方式：Qdrant 向量召回 + 词面重排。 */
    String MODE_VECTOR = "VECTOR";
    /** 检索方式：直接读取数据库中的资料分段，按词面匹配排序。 */
    String MODE_TEXT = "TEXT";

    /**
     * 在指定资料范围内检索与问题最相关的分段，并返回检索方式、是否降级等信息。
     *
     * <p>向量检索不可用（未启用、向量模型调用失败、向量库查询失败、资料尚未建立向量索引）时，
     * 自动降级为数据库文本检索，并在结果中标明降级原因，而不是直接报错。</p>
     *
     * @param userId 当前用户 ID，用于确认资料归属
     * @param materialId 要检索的资料 ID
     * @param queryText 查询文本
     * @param limit 最多返回多少条结果，为空时使用系统默认值
     * @return 检索结果
     */
    RetrievalResult retrieve(Long userId, Long materialId, String queryText, Integer limit);

    /**
     * 在指定资料范围内检索与问题最相关的分段。
     *
     * @param userId 当前用户 ID，用于确认资料归属
     * @param materialId 要检索的资料 ID
     * @param queryText 查询文本
     * @param limit 最多返回多少条结果，为空时使用系统默认值
     * @return 按相关性排序后的资料分段
     */
    default List<RetrievedSegment> retrieveMaterialSegments(Long userId, Long materialId, String queryText, Integer limit) {
        return retrieve(userId, materialId, queryText, limit).segments();
    }

    /**
     * 一次检索的完整结果。
     */
    record RetrievalResult(
            /** 资料 ID。 */
            Long materialId,
            /** 资料标题，便于展示出处。 */
            String materialTitle,
            /** 实际使用的检索方式：{@link #MODE_VECTOR} 或 {@link #MODE_TEXT}。 */
            String mode,
            /** 是否因向量检索不可用而降级为文本检索。 */
            boolean degraded,
            /** 降级原因，未降级时为 null。 */
            String degradeReason,
            /** 查询向量是否来自 Mock 模式（文本哈希生成，相似度没有语义意义）。 */
            boolean mockEmbedding,
            /** 按相关性排序后的命中结果。 */
            List<RetrievalHit> hits
    ) {
        /**
         * 只取命中的分段，兼容只关心分段本身的调用方。
         */
        public List<RetrievedSegment> segments() {
            return hits.stream().map(RetrievalHit::segment).toList();
        }
    }

    /**
     * 单条命中结果及其得分。
     */
    record RetrievalHit(
            /** 命中的分段。 */
            RetrievedSegment segment,
            /** 向量相似度；文本检索时为 null。 */
            Double semanticScore,
            /** 词面覆盖分，问题中的词在分段里出现得越多越高。 */
            double lexicalScore,
            /** 排序用的综合得分。 */
            double relevanceScore
    ) {
    }
}
