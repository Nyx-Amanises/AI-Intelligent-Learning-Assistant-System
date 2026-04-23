package com.aiassistant.learning.vo.assistant;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 助手工具调用展示对象。
 */
@Data
@Builder
public class AssistantToolCallVO {

    /** 工具调用 ID。 */
    private Long id;

    /** 关联的助手消息 ID。 */
    private Long messageId;

    /** 工具名称。 */
    private String toolName;

    /** 工具入参 JSON。 */
    private String toolArgsJson;

    /** 工具执行结果 JSON。 */
    private String toolResultJson;

    /** 调用状态：SUCCESS、FAILED 等。 */
    private String status;

    /** 失败原因。 */
    private String errorMessage;

    /** 开始执行时间。 */
    private LocalDateTime startedAt;

    /** 结束执行时间。 */
    private LocalDateTime finishedAt;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;
}
