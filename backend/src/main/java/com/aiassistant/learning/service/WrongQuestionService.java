package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.wrongquestion.WrongQuestionPageQuery;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.aiassistant.learning.vo.wrongquestion.WrongQuestionVO;
import com.aiassistant.learning.vo.wrongquestion.WrongQuestionKnowledgePointVO;
import java.util.List;

/**
 * 错题本业务接口。
 *
 * <p>错题本不单独创建题目副本，而是复用练习答案记录中的 markedWrong 标记。</p>
 */
public interface WrongQuestionService {

    /**
     * 分页查询错题。
     *
     * @param userId 当前登录用户 ID
     * @param query 分页和筛选条件
     * @return 错题分页结果
     */
    PageVO<WrongQuestionVO> pageWrongQuestions(Long userId, WrongQuestionPageQuery query);

    /** 从当前用户的全部可见错题中提供知识点候选，不受分页和关键词影响。 */
    List<WrongQuestionKnowledgePointVO> listKnowledgePoints(Long userId, Long materialId, String questionType);

    /**
     * 查询单道错题详情。
     *
     * @param userId 当前登录用户 ID
     * @param answerId 练习答案记录 ID
     * @return 错题详情
     */
    WrongQuestionVO getWrongQuestion(Long userId, Long answerId);

    /** 为当前错题新建单题练习，保留原作答和错题标记。 */
    PracticeDetailVO redoWrongQuestion(Long userId, Long answerId);

    /**
     * 将错题移出错题本。
     *
     * @param userId 当前登录用户 ID
     * @param answerId 练习答案记录 ID
     */
    void removeFromWrongBook(Long userId, Long answerId);
}
