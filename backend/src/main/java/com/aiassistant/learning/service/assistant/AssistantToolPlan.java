package com.aiassistant.learning.service.assistant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 大模型规划出的工具调用计划。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantToolPlan {

    /** 是否成功生成了计划。 */
    private Boolean planned;

    /** 交互模式，例如 TASK_CREATE、STUDY_QA、CHAT。 */
    private String interactionMode;

    /** 用户意图的简短描述。 */
    private String intent;

    /** 回复策略，例如执行工具、直接回复或追问澄清。 */
    private String replyStrategy;

    /** 不需要工具时的直接回复。 */
    private String directReply;

    /** 需要补充信息时的追问文本。 */
    private String clarificationPrompt;

    /** 当前系统暂不支持的功能描述。 */
    private String unsupportedFeature;

    /** 缺失的必要参数。 */
    @Builder.Default
    private List<String> missingSlots = new ArrayList<>();

    /** 需要执行的工具调用列表。 */
    @Builder.Default
    private List<ToolCall> toolCalls = new ArrayList<>();

    /**
     * 返回一个空计划。
     */
    public static AssistantToolPlan empty() {
        return AssistantToolPlan.builder()
                .planned(false)
                .build();
    }

    /**
     * 判断计划是否有可用内容。
     */
    public boolean hasUsablePlan() {
        return Boolean.TRUE.equals(planned)
                && (interactionMode != null || replyStrategy != null || !toolCalls.isEmpty() || !missingSlots.isEmpty());
    }

    /**
     * 单次工具调用计划。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {

        /** 工具名称。 */
        private String toolName;

        /** 工具参数。 */
        @Builder.Default
        private Map<String, Object> arguments = new LinkedHashMap<>();

        /** 规划器选择该工具的原因。 */
        private String reason;
    }
}
