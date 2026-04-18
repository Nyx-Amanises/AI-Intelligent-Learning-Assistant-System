package com.aiassistant.learning.service;

import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.aiassistant.learning.vo.question.QuestionSetPageVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface QuestionSetService extends IService<QuestionSet> {

    PageVO<QuestionSetPageVO> pageQuestionSets(
            Long userId,
            Long current,
            Long size,
            String keyword,
            String status,
            Integer difficultyLevel
    );

    PageVO<QuestionSetPageVO> browseAssistantQuestionSets(
            Long userId,
            String keyword,
            String status,
            Integer difficultyLevel,
            Long materialId,
            int limit
    );

    QuestionSetDetailVO getQuestionSetDetail(Long userId, Long questionSetId);

    void deleteQuestionSet(Long userId, Long questionSetId);
}
