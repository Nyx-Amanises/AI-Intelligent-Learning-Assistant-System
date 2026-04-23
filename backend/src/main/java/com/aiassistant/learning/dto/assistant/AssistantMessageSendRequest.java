package com.aiassistant.learning.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送助手消息的请求参数。
 */
@Data
public class AssistantMessageSendRequest {

    /** 用户输入的消息正文。 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容长度不能超过4000个字符")
    private String contentText;

    /** 指定使用的大模型名称；为空时使用系统默认模型。 */
    @Size(max = 100, message = "模型名称长度不能超过100个字符")
    private String modelName;

    /** 当前消息关联的上下文类型，例如资料、题集或练习会话。 */
    @Size(max = 32, message = "上下文类型长度不能超过32个字符")
    private String contextType;

    /** 通用上下文 ID。 */
    private Long contextId;

    /** 关联的学习资料 ID。 */
    private Long materialId;

    /** 关联的题集 ID。 */
    private Long questionSetId;

    /** 关联的练习会话 ID。 */
    private Long practiceSessionId;
}
