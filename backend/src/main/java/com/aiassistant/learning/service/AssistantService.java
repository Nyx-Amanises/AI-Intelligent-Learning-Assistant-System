package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.assistant.AssistantMessageSendRequest;
import com.aiassistant.learning.dto.assistant.AssistantSessionCreateRequest;
import com.aiassistant.learning.vo.assistant.AssistantChatReplyVO;
import com.aiassistant.learning.vo.assistant.AssistantSessionDetailVO;
import com.aiassistant.learning.vo.assistant.AssistantSessionPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 智能助手会话服务接口。
 */
public interface AssistantService {

    /**
     * 创建助手会话。
     */
    AssistantSessionDetailVO createSession(Long userId, AssistantSessionCreateRequest request);

    /**
     * 分页查询助手会话。
     */
    PageVO<AssistantSessionPageVO> pageSessions(Long userId, Long current, Long size);

    /**
     * 查询会话详情。
     */
    AssistantSessionDetailVO getSessionDetail(Long userId, Long sessionId, Integer messageLimit);

    /**
     * 非流式发送消息并获取完整回复。
     */
    AssistantChatReplyVO sendMessage(Long userId, Long sessionId, AssistantMessageSendRequest request);

    /**
     * 流式发送消息，返回 SSE 推送对象。
     */
    SseEmitter streamMessage(Long userId, Long sessionId, AssistantMessageSendRequest request);

    /**
     * 删除助手会话。
     */
    void deleteSession(Long userId, Long sessionId);
}
