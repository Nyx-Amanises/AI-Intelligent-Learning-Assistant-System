package com.aiassistant.learning.service.assistant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantToolPlan {

    private Boolean planned;

    private String interactionMode;

    private String intent;

    private String replyStrategy;

    private String directReply;

    private String clarificationPrompt;

    private String unsupportedFeature;

    @Builder.Default
    private List<String> missingSlots = new ArrayList<>();

    @Builder.Default
    private List<ToolCall> toolCalls = new ArrayList<>();

    public static AssistantToolPlan empty() {
        return AssistantToolPlan.builder()
                .planned(false)
                .build();
    }

    public boolean hasUsablePlan() {
        return Boolean.TRUE.equals(planned)
                && (interactionMode != null || replyStrategy != null || !toolCalls.isEmpty() || !missingSlots.isEmpty());
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {

        private String toolName;

        @Builder.Default
        private Map<String, Object> arguments = new LinkedHashMap<>();

        private String reason;
    }
}
