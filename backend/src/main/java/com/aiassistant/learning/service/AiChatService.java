package com.aiassistant.learning.service;

import java.util.function.Consumer;

public interface AiChatService {

    String chat(String systemPrompt, String userPrompt, String modelName, Double temperature);

    void streamChat(
            String systemPrompt,
            String userPrompt,
            String modelName,
            Double temperature,
            Consumer<String> onDelta
    );
}
