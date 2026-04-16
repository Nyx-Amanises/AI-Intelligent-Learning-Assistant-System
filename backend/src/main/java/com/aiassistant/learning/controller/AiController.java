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
import com.aiassistant.learning.vo.ai.SummaryResultVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiQuestionService aiQuestionService;
    private final AiSummaryService aiSummaryService;
    private final AiConfigService aiConfigService;

    public AiController(
            AiQuestionService aiQuestionService,
            AiSummaryService aiSummaryService,
            AiConfigService aiConfigService
    ) {
        this.aiQuestionService = aiQuestionService;
        this.aiSummaryService = aiSummaryService;
        this.aiConfigService = aiConfigService;
    }

    @GetMapping("/config")
    public ApiResponse<AiConfigVO> getConfig() {
        return ApiResponse.success(aiConfigService.getConfig());
    }

    @PutMapping("/config")
    public ApiResponse<AiConfigVO> updateConfig(@RequestBody AiConfigUpdateRequest request) {
        return ApiResponse.success("AI 配置已更新", aiConfigService.updateConfig(request));
    }

    @PostMapping("/material/{id}/summary")
    public ApiResponse<SummaryResultVO> generateSummary(
            @PathVariable Long id,
            @Valid @RequestBody SummaryGenerateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiSummaryService.generateMaterialSummary(userId, id, request));
    }

    @GetMapping("/material/{id}/latest-summary")
    public ApiResponse<SummaryResultVO> latestSummary(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiSummaryService.getLatestMaterialSummary(userId, id));
    }

    @PostMapping("/material/{id}/question-set")
    public ApiResponse<QuestionSetDetailVO> generateQuestionSet(
            @PathVariable Long id,
            @Valid @RequestBody QuestionGenerateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiQuestionService.generateQuestionSet(userId, id, request));
    }
}
