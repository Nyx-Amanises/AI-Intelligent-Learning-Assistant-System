package com.aiassistant.learning.service;

import com.aiassistant.learning.entity.AssistantSession;
import java.util.List;

public interface AssistantMemoryService {

    List<MemorySnippet> findRelevantMemories(Long userId, String queryText, Integer limit);

    void captureConversationMemory(Long userId, AssistantSession session, String userMessage, String assistantReply);

    record MemorySnippet(
            Long id,
            String memoryScope,
            String memoryType,
            String topicName,
            String summaryText,
            String contentText
    ) {
    }
}
