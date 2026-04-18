package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_session")
public class AssistantSession extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String status;

    private Integer pinned;

    private String currentContextType;

    private Long currentContextId;

    private Long currentMaterialId;

    private Long currentQuestionSetId;

    private Long currentPracticeSessionId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String pendingActionType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String pendingActionPayloadJson;

    private LocalDateTime lastMessageAt;
}
