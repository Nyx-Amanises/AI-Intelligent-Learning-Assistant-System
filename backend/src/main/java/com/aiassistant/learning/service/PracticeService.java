package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.practice.PracticeStartRequest;
import com.aiassistant.learning.dto.practice.PracticeSubmitRequest;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.aiassistant.learning.vo.practice.PracticeReviewStatusVO;
import com.aiassistant.learning.vo.practice.PracticeSessionPageVO;

/**
 * 练习业务接口。
 *
 * <p>一次练习由 PracticeSession 表示，每道题的作答结果由 PracticeAnswer 保存。
 * 客观题可以立即按标准答案判分，简答题可能需要异步调用 AI 判分。</p>
 */
public interface PracticeService {

    /**
     * 根据题集开始一次新的练习。
     *
     * @param userId 当前登录用户 ID
     * @param request 开始练习请求
     * @return 新建练习详情
     */
    PracticeDetailVO startPractice(Long userId, PracticeStartRequest request);

    /**
     * 提交练习答案并生成判分结果。
     *
     * @param userId 当前登录用户 ID
     * @param request 提交答案请求
     * @return 提交后的练习详情
     */
    PracticeDetailVO submitPractice(Long userId, PracticeSubmitRequest request);

    /**
     * 分页查询练习记录。
     *
     * @param userId 当前登录用户 ID
     * @param current 当前页码
     * @param size 每页条数
     * @return 练习记录分页结果
     */
    PageVO<PracticeSessionPageVO> pagePracticeSessions(Long userId, Long current, Long size);

    /**
     * 查询练习详情。
     *
     * @param userId 当前登录用户 ID
     * @param sessionId 练习会话 ID
     * @return 练习详情
     */
    PracticeDetailVO getPracticeDetail(Long userId, Long sessionId);

    /**
     * 修改练习名称。
     *
     * @param userId 当前登录用户 ID
     * @param sessionId 练习会话 ID
     * @param sessionName 新练习名称
     */
    void renamePracticeSession(Long userId, Long sessionId, String sessionName);

    /**
     * 等待 AI 简答题判分完成。
     *
     * @param userId 当前登录用户 ID
     * @param sessionId 练习会话 ID
     * @param timeoutMs 最长等待毫秒数
     * @return 判分状态
     */
    PracticeReviewStatusVO waitForAiReview(Long userId, Long sessionId, Long timeoutMs);

    /**
     * 异步触发待判分简答题的 AI 判分。
     *
     * @param sessionId 练习会话 ID
     */
    void reviewPendingShortAnswers(Long sessionId);

    /**
     * 立即执行待判分简答题的 AI 判分。
     *
     * @param sessionId 练习会话 ID
     */
    void reviewPendingShortAnswersNow(Long sessionId);

    /**
     * 删除练习记录及其答案。
     *
     * @param userId 当前登录用户 ID
     * @param sessionId 练习会话 ID
     */
    void deletePracticeSession(Long userId, Long sessionId);
}
