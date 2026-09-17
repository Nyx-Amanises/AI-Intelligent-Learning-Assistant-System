package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.entity.AiGenerationRecord;
import com.aiassistant.learning.vo.ai.SummaryHistoryVO;
import com.aiassistant.learning.vo.ai.SummaryResultVO;
import java.util.List;

/**
 * AI 总结服务接口。
 */
public interface AiSummaryService {

    /**
     * 创建生成中的总结记录，不调用 AI。
     */
    AiGenerationRecord createGeneratingSummaryRecord(Long userId, Long materialId, SummaryGenerateRequest request);

    /**
     * 为指定学习资料生成总结，并可选择保存为学习笔记。
     */
    SummaryResultVO generateMaterialSummary(Long userId, Long materialId, SummaryGenerateRequest request);

    /**
     * 生成并更新已有总结记录；已成功的记录直接恢复结果，避免重复生成和保存笔记。
     */
    SummaryResultVO generateMaterialSummary(Long userId, Long materialId, SummaryGenerateRequest request, Long recordId);

    /**
     * 获取某份资料最新的一条 AI 总结。
     */
    SummaryResultVO getLatestMaterialSummary(Long userId, Long materialId);

    /**
     * 查询某份资料已成功的历史总结记录，保留旧接口行为。
     */
    List<SummaryHistoryVO> listMaterialSummaries(Long userId, Long materialId);

    /**
     * 查询当前用户所有资料的总结历史。
     */
    List<SummaryHistoryVO> listAllSummaries(Long userId);
}
