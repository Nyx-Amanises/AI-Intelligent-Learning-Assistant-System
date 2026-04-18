package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.aiassistant.learning.vo.question.QuestionSetPageVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/question-set")
public class QuestionSetController {

    private final QuestionSetService questionSetService;

    public QuestionSetController(QuestionSetService questionSetService) {
        this.questionSetService = questionSetService;
    }

    @GetMapping("/page")
    public ApiResponse<PageVO<QuestionSetPageVO>> page(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer difficultyLevel
    ) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(questionSetService.pageQuestionSets(
                userId,
                current,
                size,
                keyword,
                status,
                difficultyLevel
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionSetDetailVO> detail(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(questionSetService.getQuestionSetDetail(userId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        questionSetService.deleteQuestionSet(userId, id);
        return ApiResponse.success("删除成功", null);
    }
}
