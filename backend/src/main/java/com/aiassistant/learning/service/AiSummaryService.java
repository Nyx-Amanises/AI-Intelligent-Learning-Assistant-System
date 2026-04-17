package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.vo.ai.SummaryHistoryVO;
import com.aiassistant.learning.vo.ai.SummaryResultVO;
import java.util.List;

public interface AiSummaryService {

    SummaryResultVO generateMaterialSummary(Long userId, Long materialId, SummaryGenerateRequest request);

    SummaryResultVO getLatestMaterialSummary(Long userId, Long materialId);

    List<SummaryHistoryVO> listMaterialSummaries(Long userId, Long materialId);

    List<SummaryHistoryVO> listAllSummaries(Long userId);
}
