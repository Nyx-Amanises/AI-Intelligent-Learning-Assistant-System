package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.mastery.KnowledgeMasteryQuery;
import com.aiassistant.learning.service.KnowledgeMasteryService;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryOverviewVO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/knowledge-mastery")
public class KnowledgeMasteryController {

    private final KnowledgeMasteryService knowledgeMasteryService;

    public KnowledgeMasteryController(KnowledgeMasteryService knowledgeMasteryService) {
        this.knowledgeMasteryService = knowledgeMasteryService;
    }

    @GetMapping("/overview")
    public ApiResponse<KnowledgeMasteryOverviewVO> overview(@Valid KnowledgeMasteryQuery query) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(knowledgeMasteryService.overview(userId, query));
    }
}
