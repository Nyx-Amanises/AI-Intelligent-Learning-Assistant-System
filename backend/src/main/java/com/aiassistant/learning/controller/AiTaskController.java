package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.ai.AiTaskCreateRequest;
import com.aiassistant.learning.dto.ai.EmbeddingTaskRequest;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.aiassistant.learning.vo.ai.AiTaskPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/tasks")
public class AiTaskController {

    private final AiTaskService aiTaskService;

    public AiTaskController(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @PostMapping
    public ApiResponse<AiTaskDetailVO> create(@Valid @RequestBody AiTaskCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.createTask(userId, request));
    }

    @PostMapping("/material/{materialId}/summary")
    public ApiResponse<AiTaskDetailVO> submitSummaryTask(
            @PathVariable Long materialId,
            @Valid @RequestBody SummaryGenerateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.submitSummaryTask(userId, materialId, request));
    }

    @PostMapping("/material/{materialId}/question-set")
    public ApiResponse<AiTaskDetailVO> submitQuestionGenerateTask(
            @PathVariable Long materialId,
            @Valid @RequestBody QuestionGenerateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.submitQuestionGenerateTask(userId, materialId, request));
    }

    @PostMapping("/practice/{sessionId}/review")
    public ApiResponse<AiTaskDetailVO> submitPracticeReviewTask(@PathVariable Long sessionId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.submitPracticeReviewTask(userId, sessionId));
    }

    @PostMapping("/material/{materialId}/embedding")
    public ApiResponse<AiTaskDetailVO> submitEmbeddingTask(
            @PathVariable Long materialId,
            @Valid @RequestBody EmbeddingTaskRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.submitEmbeddingTask(userId, materialId, request));
    }

    @GetMapping("/page")
    public ApiResponse<PageVO<AiTaskPageVO>> page(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.pageTasks(userId, current, size, taskType, status));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<AiTaskDetailVO> detail(@PathVariable Long taskId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.getTaskDetail(userId, taskId));
    }

    @GetMapping("/{taskId}/wait")
    public ApiResponse<AiTaskDetailVO> waitForTask(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "120000") Long timeoutMs
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.waitForTask(userId, taskId, timeoutMs));
    }

    @PostMapping("/{taskId}/dispatch")
    public ApiResponse<AiTaskDetailVO> dispatch(@PathVariable Long taskId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.dispatchTask(userId, taskId));
    }

    @PostMapping("/{taskId}/retry")
    public ApiResponse<AiTaskDetailVO> retry(@PathVariable Long taskId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.retryTask(userId, taskId));
    }
}
