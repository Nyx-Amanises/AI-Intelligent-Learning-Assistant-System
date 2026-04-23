package com.aiassistant.learning.vo.assistant;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 助手会话分页列表项。
 */
@Data
@Builder
public class AssistantSessionPageVO {

    /** 会话 ID。 */
    private Long id;

    /** 会话标题。 */
    private String title;

    /** 会话状态。 */
    private String status;

    /** 是否置顶。 */
    private Integer pinned;

    /** 当前上下文类型。 */
    private String currentContextType;

    /** 当前上下文 ID。 */
    private Long currentContextId;

    /** 最近一条消息预览。 */
    private String lastMessagePreview;

    /** 最近一次消息时间。 */
    private LocalDateTime lastMessageAt;

    /** 会话创建时间。 */
    private LocalDateTime createdAt;
}
