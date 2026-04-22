package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.ai.AiConfigUpdateRequest;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiQuestionService;
import com.aiassistant.learning.service.AiSummaryService;
import com.aiassistant.learning.vo.ai.AiConfigVO;
import com.aiassistant.learning.vo.ai.SummaryHistoryVO;
import com.aiassistant.learning.vo.ai.SummaryResultVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 同步能力接口。
 *
 * <p>这里提供 AI 配置读取/更新，以及直接生成资料摘要、题集等能力。
 * 和任务中心不同，这里的生成接口会在当前请求中直接执行并返回结果。</p>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiQuestionService aiQuestionService;
    private final AiSummaryService aiSummaryService;
    private final AiConfigService aiConfigService;

    /**
     * 构造方法注入 AI 相关服务。
     *
     * @param aiQuestionService AI 出题服务
     * @param aiSummaryService AI 摘要服务
     * @param aiConfigService AI 配置服务
     */
    public AiController(
            AiQuestionService aiQuestionService,
            AiSummaryService aiSummaryService,
            AiConfigService aiConfigService
    ) {
        this.aiQuestionService = aiQuestionService;
        this.aiSummaryService = aiSummaryService;
        this.aiConfigService = aiConfigService;
    }

    /**
     * 获取当前 AI 配置。
     *
     * @return AI 配置展示对象
     */
    @GetMapping("/config")
    public ApiResponse<AiConfigVO> getConfig() {
        return ApiResponse.success(aiConfigService.getConfig());
    }

    /**
     * 更新 AI 配置。
     *
     * @param request 前端提交的 AI 配置
     * @return 更新后的配置
     */
    @PutMapping("/config")
    public ApiResponse<AiConfigVO> updateConfig(@RequestBody AiConfigUpdateRequest request) {
        return ApiResponse.success("AI 配置已更新", aiConfigService.updateConfig(request));
    }

    /**
     * 为指定资料生成摘要。
     *
     * @param id 资料 ID
     * @param request 摘要生成参数
     * @return 摘要生成结果
     */
    @PostMapping("/material/{id}/summary")
    public ApiResponse<SummaryResultVO> generateSummary(
            @PathVariable Long id,
            @Valid @RequestBody SummaryGenerateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiSummaryService.generateMaterialSummary(userId, id, request));
    }

    /**
     * 获取指定资料最近一次摘要。
     *
     * @param id 资料 ID
     * @return 最近一次摘要结果
     */
    @GetMapping("/material/{id}/latest-summary")
    public ApiResponse<SummaryResultVO> latestSummary(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiSummaryService.getLatestMaterialSummary(userId, id));
    }

    /**
     * 获取指定资料的摘要历史。
     *
     * @param id 资料 ID
     * @return 摘要历史列表
     */
    @GetMapping("/material/{id}/summary-history")
    public ApiResponse<List<SummaryHistoryVO>> summaryHistory(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiSummaryService.listMaterialSummaries(userId, id));
    }

    /**
     * 获取当前用户所有资料的摘要历史。
     *
     * @return 摘要历史列表
     */
    @GetMapping("/summary-history")
    public ApiResponse<List<SummaryHistoryVO>> allSummaryHistory() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiSummaryService.listAllSummaries(userId));
    }

    /**
     * 为指定资料生成题集。
     *
     * @param id 资料 ID
     * @param request 出题参数
     * @return 生成后的题集详情
     */
    @PostMapping("/material/{id}/question-set")
    public ApiResponse<QuestionSetDetailVO> generateQuestionSet(
            @PathVariable Long id,
            @Valid @RequestBody QuestionGenerateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiQuestionService.generateQuestionSet(userId, id, request));
    }
}
