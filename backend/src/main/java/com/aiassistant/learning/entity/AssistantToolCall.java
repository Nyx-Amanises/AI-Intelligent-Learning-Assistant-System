package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 助手工具调用记录实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_tool_call")
public class AssistantToolCall extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工具调用主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID。 */
    private Long sessionId;

    /** 关联的助手消息 ID。 */
    private Long messageId;

    /** 工具名称。 */
    private String toolName;

    /** 工具参数 JSON。 */
    private String toolArgsJson;

    /** 工具结果 JSON。 */
    private String toolResultJson;

    /** 调用状态。 */
    private String status;

    /** 错误信息。 */
    private String errorMessage;

    /** 调用开始时间。 */
    private LocalDateTime startedAt;

    /** 调用结束时间。 */
    private LocalDateTime finishedAt;
}
