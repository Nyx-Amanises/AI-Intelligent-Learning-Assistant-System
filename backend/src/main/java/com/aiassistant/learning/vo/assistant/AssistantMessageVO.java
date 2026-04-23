package com.aiassistant.learning.vo.assistant;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 助手会话消息展示对象。
 */
@Data
@Builder
public class AssistantMessageVO {

    /** 消息 ID。 */
    private Long id;

    /** 消息角色：USER、ASSISTANT、SYSTEM 等。 */
    private String role;

    /** 消息类型：普通文本、工具结果等。 */
    private String messageType;

    /** 消息正文。 */
    private String contentText;

    /** 推理信息 JSON，便于后续调试助手行为。 */
    private String reasoningJson;

    /** 工具计划 JSON，记录助手准备调用哪些工具。 */
    private String toolPlanJson;

    /** 生成该消息使用的模型。 */
    private String modelName;

    /** 输入 token 数估算或统计值。 */
    private Integer tokenInput;

    /** 输出 token 数估算或统计值。 */
    private Integer tokenOutput;

    /** 消息创建时间。 */
    private LocalDateTime createdAt;
}
