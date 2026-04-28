package com.aiassistant.learning.dto.assistant;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改助手会话置顶状态的请求参数。
 */
@Data
public class AssistantSessionPinRequest {

    /** 是否置顶。 */
    @NotNull(message = "置顶状态不能为空")
    private Boolean pinned;
}
