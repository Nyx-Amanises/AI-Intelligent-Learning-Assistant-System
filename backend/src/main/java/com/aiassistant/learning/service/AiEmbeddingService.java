package com.aiassistant.learning.service;

import com.aiassistant.learning.vo.ai.EmbeddingTaskResultVO;

/**
 * 学习资料向量化服务接口。
 *
 * <p>向量化会把资料切片文本转换成向量，写入向量数据库，之后才能进行语义检索。</p>
 */
public interface AiEmbeddingService {

    /**
     * 准备并执行某份资料的向量化。
     *
     * @param userId 当前用户 ID
     * @param materialId 学习资料 ID
     * @param modelName 指定的 Embedding 模型；为空时使用默认模型
     * @param forceRegenerate 是否强制重新生成已有向量
     * @param taskId 关联的 AI 任务 ID，便于回写进度
     * @return 向量化结果统计
     */
    EmbeddingTaskResultVO prepareMaterialEmbedding(
            Long userId,
            Long materialId,
            String modelName,
            Boolean forceRegenerate,
            Long taskId
    );
}
