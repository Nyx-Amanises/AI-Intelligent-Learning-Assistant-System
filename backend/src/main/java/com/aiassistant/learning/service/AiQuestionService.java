package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;

/**
 * AI 题目生成服务接口。
 */
public interface AiQuestionService {

    /**
     * 根据学习资料内容生成一套题目，并保存为题集。
     */
    QuestionSetDetailVO generateQuestionSet(Long userId, Long materialId, QuestionGenerateRequest request);
}
