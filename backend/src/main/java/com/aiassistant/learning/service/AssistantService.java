package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.assistant.AssistantMessageSendRequest;
import com.aiassistant.learning.dto.assistant.AssistantSessionCreateRequest;
import com.aiassistant.learning.vo.assistant.AssistantChatReplyVO;
import com.aiassistant.learning.vo.assistant.AssistantSessionDetailVO;
import com.aiassistant.learning.vo.assistant.AssistantSessionPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AssistantService {

    AssistantSessionDetailVO createSession(Long userId, AssistantSessionCreateRequest request);

    PageVO<AssistantSessionPageVO> pageSessions(Long userId, Long current, Long size);

    AssistantSessionDetailVO getSessionDetail(Long userId, Long sessionId, Integer messageLimit);

    AssistantChatReplyVO sendMessage(Long userId, Long sessionId, AssistantMessageSendRequest request);

    SseEmitter streamMessage(Long userId, Long sessionId, AssistantMessageSendRequest request);

    void deleteSession(Long userId, Long sessionId);
}
