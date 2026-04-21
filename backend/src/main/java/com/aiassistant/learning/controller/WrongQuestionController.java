package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.wrongquestion.WrongQuestionPageQuery;
import com.aiassistant.learning.service.WrongQuestionService;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.wrongquestion.WrongQuestionVO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/wrong-questions")
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;

    public WrongQuestionController(WrongQuestionService wrongQuestionService) {
        this.wrongQuestionService = wrongQuestionService;
    }

    @GetMapping("/page")
    public ApiResponse<PageVO<WrongQuestionVO>> page(@Valid WrongQuestionPageQuery query) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(wrongQuestionService.pageWrongQuestions(userId, query));
    }

    @GetMapping("/{answerId}")
    public ApiResponse<WrongQuestionVO> detail(@PathVariable Long answerId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(wrongQuestionService.getWrongQuestion(userId, answerId));
    }

    @DeleteMapping("/{answerId}")
    public ApiResponse<Void> remove(@PathVariable Long answerId) {
        Long userId = UserContext.getCurrentUserId();
        wrongQuestionService.removeFromWrongBook(userId, answerId);
        return ApiResponse.success("已移出错题本", null);
    }
}
