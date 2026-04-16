package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;

public interface AiQuestionService {

    QuestionSetDetailVO generateQuestionSet(Long userId, Long materialId, QuestionGenerateRequest request);
}
