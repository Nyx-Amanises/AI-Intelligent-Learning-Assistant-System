package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.service.AiSummaryService;
import com.aiassistant.learning.vo.ai.SummaryResultVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiSummaryService aiSummaryService;

    public AiController(AiSummaryService aiSummaryService) {
        this.aiSummaryService = aiSummaryService;
    }

    @PostMapping("/material/{id}/summary")
    public ApiResponse<SummaryResultVO> generateSummary(
            @PathVariable Long id,
            @Valid @RequestBody SummaryGenerateRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(aiSummaryService.generateMaterialSummary(userId, id, request));
    }
}
