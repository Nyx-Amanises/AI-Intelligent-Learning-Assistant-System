package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.practice.PracticeStartRequest;
import com.aiassistant.learning.dto.practice.PracticeSubmitRequest;
import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.aiassistant.learning.vo.practice.PracticeSessionPageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @PostMapping("/start")
    public ApiResponse<PracticeDetailVO> start(@Valid @RequestBody PracticeStartRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(practiceService.startPractice(userId, request));
    }

    @PostMapping("/submit")
    public ApiResponse<PracticeDetailVO> submit(@Valid @RequestBody PracticeSubmitRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(practiceService.submitPractice(userId, request));
    }

    @GetMapping("/page")
    public ApiResponse<PageVO<PracticeSessionPageVO>> page(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(practiceService.pagePracticeSessions(userId, current, size));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<PracticeDetailVO> detail(@PathVariable Long sessionId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(practiceService.getPracticeDetail(userId, sessionId));
    }
}
