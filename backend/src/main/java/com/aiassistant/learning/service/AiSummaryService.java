package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.vo.ai.SummaryResultVO;

public interface AiSummaryService {

    SummaryResultVO generateMaterialSummary(Long userId, Long materialId, SummaryGenerateRequest request);

    SummaryResultVO getLatestMaterialSummary(Long userId, Long materialId);
}
