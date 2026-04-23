package com.aiassistant.learning.dto.assistant;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建助手会话的请求参数。
 */
@Data
public class AssistantSessionCreateRequest {

    /** 会话标题；为空时服务端会生成默认标题。 */
    @Size(max = 200, message = "会话标题长度不能超过200个字符")
    private String title;

    /** 会话初始关联的上下文类型。 */
    @Size(max = 32, message = "上下文类型长度不能超过32个字符")
    private String contextType;

    /** 通用上下文 ID。 */
    private Long contextId;

    /** 初始关联的学习资料 ID。 */
    private Long materialId;

    /** 初始关联的题集 ID。 */
    private Long questionSetId;

    /** 初始关联的练习会话 ID。 */
    private Long practiceSessionId;
}
