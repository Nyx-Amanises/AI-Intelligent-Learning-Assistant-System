package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 助手消息实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_message")
public class AssistantMessage extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID。 */
    private Long sessionId;

    /** 用户 ID。 */
    private Long userId;

    /** 消息角色：USER、ASSISTANT、SYSTEM。 */
    private String role;

    /** 消息类型。 */
    private String messageType;

    /** 消息正文。 */
    private String contentText;

    /** 助手推理或解析过程 JSON。 */
    private String reasoningJson;

    /** 工具调用计划 JSON。 */
    private String toolPlanJson;

    /** 使用的模型名称。 */
    private String modelName;

    /** 输入 token 数。 */
    private Integer tokenInput;

    /** 输出 token 数。 */
    private Integer tokenOutput;
}
