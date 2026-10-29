package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.service.AiChatService.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/** 将同一份已筛选历史与本阶段的当前提示词组合，不修改历史快照。 */
public final class AssistantConversationMessages {
    private AssistantConversationMessages() { }

    public static List<ChatMessage> withCurrent(List<ChatMessage> history, String currentPrompt) {
        List<ChatMessage> messages = new ArrayList<>(history == null ? List.of() : history);
        messages.add(new ChatMessage("user", currentPrompt));
        return List.copyOf(messages);
    }
}
