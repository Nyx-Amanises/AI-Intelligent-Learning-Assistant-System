package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.common.security.RequireAdmin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 任务中心接口。
 *
 * <p>适合耗时较长的 AI 操作，例如生成摘要、生成题集、简答题判分和资料向量化。
 * 这些操作先创建任务记录，再由任务处理器执行，前端可以查询任务进度。</p>
 */
@Validated
@RestController
@RequestMapping("/api/ai/tasks")
public class AiTaskController {

    /** 单页最大条数，避免通过 size 参数一次拉取全表。 */
    private static final long MAX_PAGE_SIZE = 100;

    private final AiTaskService aiTaskService;

    /**
     * 构造方法注入 AI 任务服务。
     *
     * @param aiTaskService AI 任务服务
     */
    public AiTaskController(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    /**
     * 创建一个通用 AI 任务。
     *
     * @param request 通用任务创建请求
     * @return 任务详情
     */
    @PostMapping
    public ApiResponse<AiTaskDetailVO> create(@Valid @RequestBody AiTaskCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.createTask(userId, request));
    }

    /**
     * 提交资料摘要任务。
     *
     * @param materialId 资料 ID
     * @param request 摘要生成参数
     * @return 任务详情
     */
    @PostMapping("/material/{materialId}/summary")
    public ApiResponse<AiTaskDetailVO> submitSummaryTask(
            @PathVariable Long materialId,
            @Valid @RequestBody SummaryGenerateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.submitSummaryTask(userId, materialId, request));
    }

    /**
     * 提交资料出题任务。
     *
     * @param materialId 资料 ID
     * @param request 出题参数
     * @return 任务详情
     */
    @PostMapping("/material/{materialId}/question-set")
    public ApiResponse<AiTaskDetailVO> submitQuestionGenerateTask(
            @PathVariable Long materialId,
            @Valid @RequestBody QuestionGenerateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.submitQuestionGenerateTask(userId, materialId, request));
    }

    /**
     * 提交练习简答题 AI 判分任务。
     *
     * @param sessionId 练习会话 ID
     * @return 任务详情
     */
    @PostMapping("/practice/{sessionId}/review")
    public ApiResponse<AiTaskDetailVO> submitPracticeReviewTask(@PathVariable Long sessionId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.submitPracticeReviewTask(userId, sessionId));
    }

    /**
     * 提交资料向量化任务。
     *
     * @param materialId 资料 ID
     * @param request 向量化参数
     * @return 任务详情
     */
    @PostMapping("/material/{materialId}/embedding")
    public ApiResponse<AiTaskDetailVO> submitEmbeddingTask(
            @PathVariable Long materialId,
            @Valid @RequestBody EmbeddingTaskRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.submitEmbeddingTask(userId, materialId, request));
    }

    /**
     * 分页查询 AI 任务。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param taskType 任务类型筛选
     * @param status 任务状态筛选
     * @return 任务分页结果
     */
    @GetMapping("/page")
    public ApiResponse<PageVO<AiTaskPageVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") Long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数最小为1")
            @Max(value = MAX_PAGE_SIZE, message = "每页条数最大为" + MAX_PAGE_SIZE) Long size,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.pageTasks(userId, current, size, taskType, status));
    }

    /**
     * 查询任务详情。
     *
     * @param taskId 任务 ID
     * @return 任务详情
     */
    @GetMapping("/{taskId}")
    public ApiResponse<AiTaskDetailVO> detail(@PathVariable Long taskId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.getTaskDetail(userId, taskId));
    }

    /**
     * 等待任务执行完成。
     *
     * @param taskId 任务 ID
     * @param timeoutMs 最长等待毫秒数
     * @return 最新任务详情
     */
    @GetMapping("/{taskId}/wait")
    public ApiResponse<AiTaskDetailVO> waitForTask(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "120000") Long timeoutMs
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.waitForTask(userId, taskId, timeoutMs));
    }

    /**
     * 手动触发任务执行。
     *
     * <p>用于排查卡在排队状态的任务，属于开发者功能，仅管理员可用。</p>
     *
     * @param taskId 任务 ID
     * @return 最新任务详情
     */
    @RequireAdmin
    @PostMapping("/{taskId}/dispatch")
    public ApiResponse<AiTaskDetailVO> dispatch(@PathVariable Long taskId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.dispatchTask(userId, taskId));
    }

    /**
     * 重试失败任务。
     *
     * @param taskId 任务 ID
     * @return 最新任务详情
     */
    @PostMapping("/{taskId}/retry")
    public ApiResponse<AiTaskDetailVO> retry(@PathVariable Long taskId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiTaskService.retryTask(userId, taskId));
    }

    /**
     * 删除任务记录。
     *
     * @param taskId 任务 ID
     * @return 删除成功提示
     */
    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> delete(@PathVariable Long taskId) {
        Long userId = UserContext.getCurrentUserId();
        aiTaskService.deleteTask(userId, taskId);
        return ApiResponse.success("任务记录已删除", null);
    }
}
