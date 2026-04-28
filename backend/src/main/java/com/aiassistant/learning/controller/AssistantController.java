package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.assistant.AssistantMessageSendRequest;
import com.aiassistant.learning.dto.assistant.AssistantSessionCreateRequest;
import com.aiassistant.learning.dto.assistant.AssistantSessionPinRequest;
import com.aiassistant.learning.dto.assistant.AssistantSessionRenameRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 智能助手会话接口。
 *
 * <p>前端和智能助手的所有聊天操作都从这里进入，包括创建会话、发送消息、流式回复和删除会话。</p>
 */
@Validated
@RestController
@RequestMapping("/api/assistant/sessions")
public class AssistantController {

    /** 智能助手业务服务。 */
    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /**
     * 创建一个新的助手会话。
     */
    @PostMapping
    public ApiResponse<AssistantSessionDetailVO> createSession(@Valid @RequestBody AssistantSessionCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("创建会话成功", assistantService.createSession(userId, request));
    }

    /**
     * 分页查询当前用户的助手会话列表。
     */
    @GetMapping("/page")
    public ApiResponse<PageVO<AssistantSessionPageVO>> pageSessions(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(assistantService.pageSessions(userId, current, size));
    }

    /**
     * 查询会话详情，包含最近若干条消息。
     */
    @GetMapping("/{sessionId}")
    public ApiResponse<AssistantSessionDetailVO> getSessionDetail(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "30") Integer messageLimit
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(assistantService.getSessionDetail(userId, sessionId, messageLimit));
    }

    /**
     * 修改当前用户自己的助手会话标题。
     */
    @PutMapping("/{sessionId}/title")
    public ApiResponse<AssistantSessionDetailVO> renameSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody AssistantSessionRenameRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success("修改会话名称成功", assistantService.renameSession(userId, sessionId, request.getTitle()));
    }

    /**
     * 修改当前用户自己的助手会话置顶状态。
     */
    @PutMapping("/{sessionId}/pinned")
    public ApiResponse<AssistantSessionDetailVO> updateSessionPinned(
            @PathVariable Long sessionId,
            @Valid @RequestBody AssistantSessionPinRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        boolean pinned = Boolean.TRUE.equals(request.getPinned());
        String message = pinned ? "会话已置顶" : "已取消置顶";
        return ApiResponse.success(message, assistantService.updateSessionPinned(userId, sessionId, pinned));
    }

    /**
     * 发送一条普通非流式消息，等待助手完整回复后返回。
     */
    @PostMapping("/{sessionId}/messages")
    public ApiResponse<AssistantChatReplyVO> sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody AssistantMessageSendRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(assistantService.sendMessage(userId, sessionId, request));
    }

    /**
     * 发送流式消息，后端通过 SSE 持续推送助手回复片段。
     */
    @PostMapping(path = "/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody AssistantMessageSendRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return assistantService.streamMessage(userId, sessionId, request);
    }

    /**
     * 删除当前用户自己的助手会话。
     */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = UserContext.getCurrentUserId();
        assistantService.deleteSession(userId, sessionId);
        return ApiResponse.success("删除会话成功", null);
    }
}
