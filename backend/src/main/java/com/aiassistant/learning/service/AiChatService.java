package com.aiassistant.learning.service;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * AI 聊天调用服务接口。
 *
 * <p>这里封装了和大模型聊天接口的交互，业务层只需要传入提示词和模型参数。</p>
 */
public interface AiChatService {

    /** 会话历史只允许真实 user/assistant 角色；系统指令必须通过独立参数提供。 */
    record ChatMessage(String role, String content) {
        public ChatMessage {
            role = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
            if (!"user".equals(role) && !"assistant".equals(role)) {
                throw new IllegalArgumentException("会话消息角色只允许 user 或 assistant");
            }
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("会话消息内容不能为空");
            }
        }
    }

    /**
     * 一次性获取模型回复。
     */
    String chat(String systemPrompt, String userPrompt, String modelName, Double temperature);

    /** 按给定顺序传递会话消息，系统指令独立于历史内容。 */
    String chat(String systemPrompt, List<ChatMessage> messages, String modelName, Double temperature);

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

    /** 按给定顺序传递会话消息并消费增量回复，系统指令独立于历史内容。 */
    void streamChat(String systemPrompt, List<ChatMessage> messages, String modelName, Double temperature, Consumer<String> onDelta);
}
