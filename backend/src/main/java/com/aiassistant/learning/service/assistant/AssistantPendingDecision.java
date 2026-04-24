package com.aiassistant.learning.service.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 大模型对 pending 状态的结构化决策。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantPendingDecision {

    /** 决策：CONTINUE、INTERRUPT、CANCEL、CLARIFY、UNKNOWN。 */
    private String decision;

    /** 本轮消息更像属于哪种交互模式。 */
    private String interactionMode;

    /** 简短原因，主要用于调试和轨迹展示。 */
    private String reason;

    /** 置信度，0 到 1。 */
    private Double confidence;

    /**
     * 返回未知决策。
     */
    public static AssistantPendingDecision unknown() {
        return AssistantPendingDecision.builder()
                .decision("UNKNOWN")
                .build();
    }

    /**
     * 判断是否有明确可用的模型决策。
     */
    public boolean hasActionableDecision() {
        return StringUtils.hasText(decision)
                && !"UNKNOWN".equalsIgnoreCase(decision.trim());
    }
}
