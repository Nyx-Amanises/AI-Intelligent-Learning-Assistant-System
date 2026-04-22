package com.aiassistant.learning.service;

import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.aiassistant.learning.vo.question.QuestionSetPageVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 题集业务接口。
 *
 * <p>题集是题目的集合，通常和某份学习资料关联。
 * 用户可以浏览题集、查看题目详情，并基于题集开始练习。</p>
 */
public interface QuestionSetService extends IService<QuestionSet> {

    /**
     * 分页查询题集。
     *
     * @param userId 当前登录用户 ID
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 标题关键词
     * @param status 题集状态
     * @param difficultyLevel 难度等级
     * @return 题集分页结果
     */
    PageVO<QuestionSetPageVO> pageQuestionSets(
            Long userId,
            Long current,
            Long size,
            String keyword,
            String status,
            Integer difficultyLevel
    );

    /**
     * 给 AI 助手使用的题集浏览接口。
     *
     * @param userId 当前登录用户 ID
     * @param keyword 标题关键词
     * @param status 题集状态
     * @param difficultyLevel 难度等级
     * @param materialId 关联资料 ID
     * @param limit 最多返回条数
     * @return 简化分页题集结果
     */
    PageVO<QuestionSetPageVO> browseAssistantQuestionSets(
            Long userId,
            String keyword,
            String status,
            Integer difficultyLevel,
            Long materialId,
            int limit
    );

    /**
     * 查询题集详情。
     *
     * @param userId 当前登录用户 ID
     * @param questionSetId 题集 ID
     * @return 题集详情
     */
    QuestionSetDetailVO getQuestionSetDetail(Long userId, Long questionSetId);

    /**
     * 删除题集以及相关题目、练习记录。
     *
     * @param userId 当前登录用户 ID
     * @param questionSetId 题集 ID
     */
    void deleteQuestionSet(Long userId, Long questionSetId);
}
