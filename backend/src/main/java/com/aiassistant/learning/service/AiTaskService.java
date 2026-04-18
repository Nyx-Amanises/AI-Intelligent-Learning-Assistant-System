package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.AiTaskCreateRequest;
import com.aiassistant.learning.dto.ai.EmbeddingTaskRequest;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.aiassistant.learning.vo.ai.AiTaskPageVO;
import com.aiassistant.learning.vo.page.PageVO;

public interface AiTaskService {

    AiTaskDetailVO createTask(Long userId, AiTaskCreateRequest request);

    AiTaskDetailVO submitSummaryTask(Long userId, Long materialId, SummaryGenerateRequest request);

    AiTaskDetailVO submitQuestionGenerateTask(Long userId, Long materialId, QuestionGenerateRequest request);

    AiTaskDetailVO submitPracticeReviewTask(Long userId, Long sessionId);

    AiTaskDetailVO submitEmbeddingTask(Long userId, Long materialId, EmbeddingTaskRequest request);

    PageVO<AiTaskPageVO> pageTasks(Long userId, Long current, Long size, String taskType, String status);

    AiTaskDetailVO getTaskDetail(Long userId, Long taskId);

    AiTaskDetailVO waitForTask(Long userId, Long taskId, Long timeoutMs);

    AiTaskDetailVO dispatchTask(Long userId, Long taskId);

    AiTaskDetailVO retryTask(Long userId, Long taskId);

    void deleteTask(Long userId, Long taskId);

    void executeTask(Long taskId);
}
