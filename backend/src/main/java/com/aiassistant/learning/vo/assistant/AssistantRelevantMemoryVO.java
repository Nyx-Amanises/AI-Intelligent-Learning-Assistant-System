package com.aiassistant.learning.vo.assistant;

import lombok.Builder;
import lombok.Data;

/**
 * 助手回复中使用到的相关记忆。
 */
@Data
@Builder
public class AssistantRelevantMemoryVO {

    /** 记忆 ID。 */
    private Long id;

    /** 记忆作用范围，例如用户级、资料级。 */
    private String memoryScope;

    /** 记忆类型，例如偏好、知识点、历史行为。 */
    private String memoryType;

    /** 记忆主题。 */
    private String topicName;

    /** 记忆摘要。 */
    private String summaryText;
}
