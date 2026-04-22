package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.practice.PracticeSessionRenameRequest;
import com.aiassistant.learning.dto.practice.PracticeStartRequest;
import com.aiassistant.learning.dto.practice.PracticeSubmitRequest;
import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.aiassistant.learning.vo.practice.PracticeReviewStatusVO;
import com.aiassistant.learning.vo.practice.PracticeSessionPageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 练习作答接口。
 *
 * <p>这个 Controller 覆盖一次练习的完整流程：
 * 开始练习、提交答案、查看练习记录、重命名、等待 AI 判分和删除练习。</p>
 */
@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practiceService;

    /**
     * 构造方法注入练习服务。
     *
     * @param practiceService 练习业务服务
     */
    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    /**
     * 开始一次练习。
     *
     * @param request 包含题集 ID 的请求参数
     * @return 新建的练习详情
     */
    @PostMapping("/start")
    public ApiResponse<PracticeDetailVO> start(@Valid @RequestBody PracticeStartRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(practiceService.startPractice(userId, request));
    }

    /**
     * 提交练习答案。
     *
     * @param request 提交的练习会话 ID 和答案列表
     * @return 提交后的练习详情，包含判分结果
     */
    @PostMapping("/submit")
    public ApiResponse<PracticeDetailVO> submit(@Valid @RequestBody PracticeSubmitRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(practiceService.submitPractice(userId, request));
    }

    /**
     * 分页查询当前用户的练习记录。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @return 练习记录分页结果
     */
    @GetMapping("/page")
    public ApiResponse<PageVO<PracticeSessionPageVO>> page(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(practiceService.pagePracticeSessions(userId, current, size));
    }

    /**
     * 查询练习详情。
     *
     * @param sessionId 练习会话 ID
     * @return 练习详情和每道题的作答情况
     */
    @GetMapping("/{sessionId}")
    public ApiResponse<PracticeDetailVO> detail(@PathVariable Long sessionId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(practiceService.getPracticeDetail(userId, sessionId));
    }

    /**
     * 修改练习名称。
     *
     * @param sessionId 练习会话 ID
     * @param request 新练习名称
     * @return 修改成功提示
     */
    @PutMapping("/{sessionId}/name")
    public ApiResponse<Void> rename(
            @PathVariable Long sessionId,
            @Valid @RequestBody PracticeSessionRenameRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        practiceService.renamePracticeSession(userId, sessionId, request.getSessionName());
        return ApiResponse.success("练习名称已更新", null);
    }

    /**
     * 等待简答题 AI 判分完成。
     *
     * <p>前端提交后可以调用这个接口轮询状态，最多等待 timeoutMs 指定的时间。</p>
     *
     * @param sessionId 练习会话 ID
     * @param timeoutMs 最长等待毫秒数
     * @return AI 判分是否完成
     */
    @GetMapping("/{sessionId}/review-status")
    public ApiResponse<PracticeReviewStatusVO> waitForReview(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "60000") Long timeoutMs
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(practiceService.waitForAiReview(userId, sessionId, timeoutMs));
    }

    /**
     * 删除练习记录。
     *
     * @param sessionId 练习会话 ID
     * @return 删除成功提示
     */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(@PathVariable Long sessionId) {
        Long userId = UserContext.getCurrentUserId();
        practiceService.deletePracticeSession(userId, sessionId);
        return ApiResponse.success("删除成功", null);
    }
}
