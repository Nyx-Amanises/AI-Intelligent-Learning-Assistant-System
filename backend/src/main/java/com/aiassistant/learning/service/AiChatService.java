package com.aiassistant.learning.service;

import java.util.function.Consumer;

/**
 * AI 聊天调用服务接口。
 *
 * <p>这里封装了和大模型聊天接口的交互，业务层只需要传入提示词和模型参数。</p>
 */
public interface AiChatService {

    /**
     * 一次性获取模型回复。
     */
    String chat(String systemPrompt, String userPrompt, String modelName, Double temperature);

    /**
     * 以流式方式获取模型回复。
     *
     * @param onDelta 每收到一段增量文本时触发的回调
     */
    void streamChat(
            String systemPrompt,
            String userPrompt,
            String modelName,
            Double temperature,
            Consumer<String> onDelta
    );
}
