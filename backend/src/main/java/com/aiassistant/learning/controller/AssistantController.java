package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.assistant.AssistantMessageSendRequest;
import com.aiassistant.learning.dto.assistant.AssistantSessionCreateRequest;
import com.aiassistant.learning.service.AssistantService;
import com.aiassistant.learning.vo.assistant.AssistantChatReplyVO;
import com.aiassistant.learning.vo.assistant.AssistantSessionDetailVO;
import com.aiassistant.learning.vo.assistant.AssistantSessionPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("/api/assistant/sessions")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping
    public ApiResponse<AssistantSessionDetailVO> createSession(@Valid @RequestBody AssistantSessionCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("创建会话成功", assistantService.createSession(userId, request));
    }

    @GetMapping("/page")
    public ApiResponse<PageVO<AssistantSessionPageVO>> pageSessions(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(assistantService.pageSessions(userId, current, size));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<AssistantSessionDetailVO> getSessionDetail(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "30") Integer messageLimit
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(assistantService.getSessionDetail(userId, sessionId, messageLimit));
    }

    @PostMapping("/{sessionId}/messages")
    public ApiResponse<AssistantChatReplyVO> sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody AssistantMessageSendRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(assistantService.sendMessage(userId, sessionId, request));
    }

    @PostMapping(path = "/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody AssistantMessageSendRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return assistantService.streamMessage(userId, sessionId, request);
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = UserContext.getCurrentUserId();
        assistantService.deleteSession(userId, sessionId);
        return ApiResponse.success("删除会话成功", null);
    }
}
