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

/**
 * 错题本接口。
 *
 * <p>错题本用于集中查看练习中做错的题目。这里的错题来源于练习答案表中
 * markedWrong = 1 的记录。</p>
 */
@Validated
@RestController
@RequestMapping("/api/wrong-questions")
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;

    /**
     * 构造方法注入错题本服务。
     *
     * @param wrongQuestionService 错题本业务服务
     */
    public WrongQuestionController(WrongQuestionService wrongQuestionService) {
        this.wrongQuestionService = wrongQuestionService;
    }

    /**
     * 分页查询当前用户的错题。
     *
     * @param query 分页和筛选条件
     * @return 错题分页结果
     */
    @GetMapping("/page")
    public ApiResponse<PageVO<WrongQuestionVO>> page(@Valid WrongQuestionPageQuery query) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(wrongQuestionService.pageWrongQuestions(userId, query));
    }

    /**
     * 查看单道错题详情。
     *
     * @param answerId 练习答案记录 ID
     * @return 错题详情
     */
    @GetMapping("/{answerId}")
    public ApiResponse<WrongQuestionVO> detail(@PathVariable Long answerId) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(wrongQuestionService.getWrongQuestion(userId, answerId));
    }

    /**
     * 将题目移出错题本。
     *
     * <p>这里不会删除作答记录，只是把 markedWrong 改回 0。</p>
     *
     * @param answerId 练习答案记录 ID
     * @return 移出成功提示
     */
    @DeleteMapping("/{answerId}")
    public ApiResponse<Void> remove(@PathVariable Long answerId) {
        Long userId = UserContext.getCurrentUserId();
        wrongQuestionService.removeFromWrongBook(userId, answerId);
        return ApiResponse.success("已移出错题本", null);
    }
}
