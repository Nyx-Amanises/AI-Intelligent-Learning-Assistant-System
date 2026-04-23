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

/**
 * 助手会话实体。
 *
 * <p>一条会话对应用户和助手的一段连续聊天，同时保存当前学习上下文。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_session")
public class AssistantSession extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 会话标题。 */
    private String title;

    /** 会话状态，例如 ACTIVE。 */
    private String status;

    /** 是否置顶。 */
    private Integer pinned;

    /** 当前上下文类型。 */
    private String currentContextType;

    /** 当前通用上下文 ID。 */
    private Long currentContextId;

    /** 当前资料 ID。 */
    private Long currentMaterialId;

    /** 当前题集 ID。 */
    private Long currentQuestionSetId;

    /** 当前练习会话 ID。 */
    private Long currentPracticeSessionId;

    /** 等待用户确认的动作类型。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String pendingActionType;

    /** 等待确认动作的参数 JSON。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String pendingActionPayloadJson;

    /** 最近消息时间，用于会话列表排序。 */
    private LocalDateTime lastMessageAt;
}
