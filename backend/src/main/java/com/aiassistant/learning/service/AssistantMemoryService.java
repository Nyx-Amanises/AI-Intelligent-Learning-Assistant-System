package com.aiassistant.learning.service;

import com.aiassistant.learning.entity.AssistantSession;
import java.util.List;

/**
 * 助手记忆服务接口。
 */
public interface AssistantMemoryService {

    /**
     * 根据用户输入查找相关记忆。
     */
    List<MemorySnippet> findRelevantMemories(Long userId, String queryText, Integer limit);

    /**
     * 从一轮对话中提取并保存长期记忆。
     */
    void captureConversationMemory(Long userId, AssistantSession session, String userMessage, String assistantReply);

    /**
     * 检索到的一条记忆片段。
     */
    record MemorySnippet(
            /** 记忆 ID。 */
            Long id,
            /** 记忆范围。 */
            String memoryScope,
            /** 记忆类型。 */
            String memoryType,
            /** 主题名称。 */
            String topicName,
            /** 记忆摘要。 */
            String summaryText,
            /** 记忆正文。 */
            String contentText
    ) {
    }
}
