package com.aiassistant.learning.service;

public interface AiChatService {

    String chat(String systemPrompt, String userPrompt, String modelName, Double temperature);
}
