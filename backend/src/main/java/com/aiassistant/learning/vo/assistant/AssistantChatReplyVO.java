package com.aiassistant.learning.vo.assistant;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 助手聊天回复结果。
 */
@Data
@Builder
public class AssistantChatReplyVO {

    /** 会话 ID。 */
    private Long sessionId;

    /** 会话标题。 */
    private String sessionTitle;

    /** 本次保存的用户消息。 */
    private AssistantMessageVO userMessage;

    /** 本次保存的助手回复消息。 */
    private AssistantMessageVO assistantMessage;

    /** 本轮助手调用过的工具记录。 */
    private List<AssistantToolCallVO> toolCalls;

    /** 本轮回复使用到的用户记忆。 */
    private List<AssistantRelevantMemoryVO> usedMemories;
}
