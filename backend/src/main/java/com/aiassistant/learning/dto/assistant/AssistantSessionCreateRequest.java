package com.aiassistant.learning.dto.assistant;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssistantSessionCreateRequest {

    @Size(max = 200, message = "会话标题长度不能超过200个字符")
    private String title;

    @Size(max = 32, message = "上下文类型长度不能超过32个字符")
    private String contextType;

    private Long contextId;

    private Long materialId;

    private Long questionSetId;

    private Long practiceSessionId;
}
