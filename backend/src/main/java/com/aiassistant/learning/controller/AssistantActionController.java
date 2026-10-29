package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.assistant.AssistantActionDecisionRequest;
import com.aiassistant.learning.service.assistant.AssistantActionService;
import com.aiassistant.learning.vo.assistant.AssistantActionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assistant/sessions/{sessionId}/actions")
public class AssistantActionController {
    private final AssistantActionService actions;
    public AssistantActionController(AssistantActionService actions) { this.actions = actions; }

    @GetMapping("/pending")
    public ApiResponse<AssistantActionVO> pending(@PathVariable Long sessionId) {
        return ApiResponse.success(actions.pending(UserContext.getCurrentUserId(), sessionId));
    }

    @PostMapping("/{actionId}/decision")
    public ApiResponse<AssistantActionVO> decide(@PathVariable Long sessionId, @PathVariable String actionId,
            @Valid @RequestBody AssistantActionDecisionRequest request) {
        return ApiResponse.success(actions.decide(UserContext.getCurrentUserId(), sessionId, actionId, request));
    }
}
