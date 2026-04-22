package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.AiTaskCreateRequest;
import com.aiassistant.learning.dto.ai.EmbeddingTaskRequest;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.aiassistant.learning.vo.ai.AiTaskPageVO;
import com.aiassistant.learning.vo.page.PageVO;

/**
 * AI 异步任务服务接口。
 *
 * <p>总结生成、题目生成、向量化、主观题批改等操作都可能耗时较长，
 * 所以系统先创建任务记录，再由任务执行器去真正处理。</p>
 */
public interface AiTaskService {

    /**
     * 根据通用任务创建请求创建一条任务。
     *
     * @param userId 当前登录用户 ID
     * @param request 前端提交的任务类型、标题、参数等信息
     * @return 创建后的任务详情
     */
    AiTaskDetailVO createTask(Long userId, AiTaskCreateRequest request);

    /**
     * 创建学习资料总结任务。
     */
    AiTaskDetailVO submitSummaryTask(Long userId, Long materialId, SummaryGenerateRequest request);

    /**
     * 创建 AI 生成题目任务。
     */
    AiTaskDetailVO submitQuestionGenerateTask(Long userId, Long materialId, QuestionGenerateRequest request);

    /**
     * 创建主观题自动批改任务。
     */
    AiTaskDetailVO submitPracticeReviewTask(Long userId, Long sessionId);

    /**
     * 创建资料向量化任务，用于后续语义检索和 RAG 问答。
     */
    AiTaskDetailVO submitEmbeddingTask(Long userId, Long materialId, EmbeddingTaskRequest request);

    /**
     * 分页查询当前用户的 AI 任务列表。
     */
    PageVO<AiTaskPageVO> pageTasks(Long userId, Long current, Long size, String taskType, String status);

    /**
     * 查询单个任务详情。
     */
    AiTaskDetailVO getTaskDetail(Long userId, Long taskId);

    /**
     * 等待任务完成一小段时间，适合前端提交任务后做短轮询。
     */
    AiTaskDetailVO waitForTask(Long userId, Long taskId, Long timeoutMs);

    /**
     * 手动派发一个等待中的任务。
     */
    AiTaskDetailVO dispatchTask(Long userId, Long taskId);

    /**
     * 将失败任务重新放回等待队列。
     */
    AiTaskDetailVO retryTask(Long userId, Long taskId);

    /**
     * 删除当前用户自己的任务记录。
     */
    void deleteTask(Long userId, Long taskId);

    /**
     * 后台线程真正执行任务时调用。
     *
     * @param taskId 要执行的任务 ID
     */
    void executeTask(Long taskId);
}
