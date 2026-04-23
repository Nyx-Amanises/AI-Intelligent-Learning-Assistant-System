package com.aiassistant.learning.vo.assistant;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 助手会话详情展示对象。
 */
@Data
@Builder
public class AssistantSessionDetailVO {

    /** 会话 ID。 */
    private Long id;

    /** 会话标题。 */
    private String title;

    /** 会话状态。 */
    private String status;

    /** 是否置顶，1 表示置顶。 */
    private Integer pinned;

    /** 当前上下文类型。 */
    private String currentContextType;

    /** 当前通用上下文 ID。 */
    private Long currentContextId;

    /** 当前关联的学习资料 ID。 */
    private Long currentMaterialId;

    /** 当前关联的题集 ID。 */
    private Long currentQuestionSetId;

    /** 当前关联的练习会话 ID。 */
    private Long currentPracticeSessionId;

    /** 最近一次消息时间。 */
    private LocalDateTime lastMessageAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 最近消息列表。 */
    private List<AssistantMessageVO> messages;

    /** 最近工具调用列表。 */
    private List<AssistantToolCallVO> recentToolCalls;
}
