package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.service.AiConfigService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 决定本轮是否调用真实大模型、以及使用哪个模型。
 */
@Component
public class AssistantModelPolicy {

    private final AiConfigService aiConfigService;

    public AssistantModelPolicy(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    /**
     * 判断当前 AI 配置是否允许调用真实模型。
     */
    public boolean shouldUseAiModel() {
        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        return Boolean.TRUE.equals(config.enabled())
                && !Boolean.TRUE.equals(config.mockMode())
                && StringUtils.hasText(config.apiKey());
    }

    /**
     * 根据交互模式判断是否需要调用大模型生成最终回复。
     */
    public boolean shouldUseAiModelForMode(AssistantInteractionMode interactionMode) {
        return shouldUseAiModel() && interactionMode != AssistantInteractionMode.UNSUPPORTED;
    }

    /**
     * 用户未指定模型时回退到配置里的默认模型。
     */
    public String resolveModelName(String modelName) {
        if (StringUtils.hasText(modelName)) {
            return modelName.trim();
        }
        return aiConfigService.getResolvedConfig().defaultModel();
    }
}
