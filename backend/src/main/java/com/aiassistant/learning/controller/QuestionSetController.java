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

/**
 * 题集管理接口。
 *
 * <p>题集通常由 AI 根据学习资料生成，里面包含多道题目。
 * 这里负责题集列表、详情和删除操作。</p>
 */
@RestController
@RequestMapping("/api/question-set")
public class QuestionSetController {

    private final QuestionSetService questionSetService;

    /**
     * 构造方法注入题集服务。
     *
     * @param questionSetService 题集业务服务
     */
    public QuestionSetController(QuestionSetService questionSetService) {
        this.questionSetService = questionSetService;
    }

    /**
     * 分页查询当前用户的题集。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 标题关键词，可为空
     * @param status 题集状态，可为空
     * @param difficultyLevel 难度等级，可为空
     * @return 题集分页结果
     */
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

    /**
     * 查询题集详情。
     *
     * @param id 题集 ID
     * @return 题集详情，包含题目列表和可能的来源片段
     */
    @GetMapping("/{id}")
    public ApiResponse<QuestionSetDetailVO> detail(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(questionSetService.getQuestionSetDetail(userId, id));
    }

    /**
     * 删除题集。
     *
     * <p>删除题集时会同步删除题目以及相关练习记录。</p>
     *
     * @param id 题集 ID
     * @return 删除成功提示
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        questionSetService.deleteQuestionSet(userId, id);
        return ApiResponse.success("删除成功", null);
    }
}
