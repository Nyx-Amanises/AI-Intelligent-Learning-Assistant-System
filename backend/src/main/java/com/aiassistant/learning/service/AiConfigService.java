package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.AiConfigUpdateRequest;
import com.aiassistant.learning.vo.ai.AiConfigVO;

public interface AiConfigService {

    AiConfigVO getConfig();

    AiConfigVO updateConfig(AiConfigUpdateRequest request);

    ResolvedAiConfig getResolvedConfig();

    record ResolvedAiConfig(
            Boolean enabled,
            Boolean mockMode,
            String baseUrl,
            String chatPath,
            String apiKey,
            String defaultModel
    ) {
    }
}
