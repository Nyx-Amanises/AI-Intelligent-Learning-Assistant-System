package com.aiassistant.learning.vo.assistant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssistantRelevantMemoryVO {

    private Long id;

    private String memoryScope;

    private String memoryType;

    private String topicName;

    private String summaryText;
}
