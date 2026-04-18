package com.aiassistant.learning.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssistantMessageSendRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容长度不能超过4000个字符")
    private String contentText;

    @Size(max = 100, message = "模型名称长度不能超过100个字符")
    private String modelName;

    @Size(max = 32, message = "上下文类型长度不能超过32个字符")
    private String contextType;

    private Long contextId;

    private Long materialId;

    private Long questionSetId;

    private Long practiceSessionId;
}
