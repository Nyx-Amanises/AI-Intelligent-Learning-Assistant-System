package com.aiassistant.learning.vo.assistant;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssistantChatReplyVO {

    private Long sessionId;

    private String sessionTitle;

    private AssistantMessageVO userMessage;

    private AssistantMessageVO assistantMessage;

    private List<AssistantToolCallVO> toolCalls;

    private List<AssistantRelevantMemoryVO> usedMemories;
}
