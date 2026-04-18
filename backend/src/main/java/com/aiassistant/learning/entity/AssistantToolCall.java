package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_tool_call")
public class AssistantToolCall extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long messageId;

    private String toolName;

    private String toolArgsJson;

    private String toolResultJson;

    private String status;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
