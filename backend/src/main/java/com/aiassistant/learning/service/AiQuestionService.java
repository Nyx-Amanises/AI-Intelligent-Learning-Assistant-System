package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;

/**
 * AI 题目生成服务接口。
 */
public interface AiQuestionService {

    /**
     * 创建生成中的题集记录，不调用 AI，供异步任务在提交时使用。
     */
    QuestionSet createGeneratingQuestionSet(Long userId, Long materialId, QuestionGenerateRequest request);

    /**
     * 根据学习资料内容生成一套题目，并保存为题集。
     */
    QuestionSetDetailVO generateQuestionSet(Long userId, Long materialId, QuestionGenerateRequest request);

    /**
     * 为已创建的题集生成题目，完成后更新同一条记录。
     */
    QuestionSetDetailVO generateQuestionSet(Long userId, Long materialId, QuestionGenerateRequest request, Long questionSetId);
}
