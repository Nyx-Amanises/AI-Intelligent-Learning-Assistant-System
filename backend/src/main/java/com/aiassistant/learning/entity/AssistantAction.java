package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 助手写入任务之前保存的动作预览及确认结果。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_action")
public class AssistantAction extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提供给客户端的不可预测动作标识。 */
    private String actionId;

    private Long sessionId;

    private Long userId;

    /** 客户端确认时必须匹配的预览版本。 */
    private Long revision;

    /** PENDING、CONFIRMED、CANCELLED、EXPIRED 或 SUPERSEDED。 */
    private String status;

    private Long materialId;

    /** 生成预览时的资料标题快照。 */
    private String materialTitle;

    /** 已规范化、仅由服务端保存的任务配置。 */
    private String tasksJson;

    /** 确认或取消后的结果快照，供重复请求返回同一结果。 */
    private String resultJson;

    private LocalDateTime expiresAt;

    private LocalDateTime decidedAt;
}
