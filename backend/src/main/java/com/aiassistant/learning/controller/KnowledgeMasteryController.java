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

/**
 * 知识掌握度接口。
 *
 * <p>根据用户已提交的练习记录，按知识点汇总正确率、得分率和薄弱程度，
 * 帮助用户知道哪些知识点已经掌握，哪些还需要复习。</p>
 */
@Validated
@RestController
@RequestMapping("/api/knowledge-mastery")
public class KnowledgeMasteryController {

    private final KnowledgeMasteryService knowledgeMasteryService;

    /**
     * 构造方法注入知识掌握度服务。
     *
     * @param knowledgeMasteryService 知识掌握度业务服务
     */
    public KnowledgeMasteryController(KnowledgeMasteryService knowledgeMasteryService) {
        this.knowledgeMasteryService = knowledgeMasteryService;
    }

    /**
     * 获取知识掌握度总览。
     *
     * @param query 筛选和分页条件
     * @return 知识掌握度总览
     */
    @GetMapping("/overview")
    public ApiResponse<KnowledgeMasteryOverviewVO> overview(@Valid KnowledgeMasteryQuery query) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(knowledgeMasteryService.overview(userId, query));
    }
}
