package com.aiassistant.learning.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改助手会话标题的请求参数。
 */
@Data
public class AssistantSessionRenameRequest {

    /** 新的会话标题。 */
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 200, message = "会话标题长度不能超过200个字符")
    private String title;
}
