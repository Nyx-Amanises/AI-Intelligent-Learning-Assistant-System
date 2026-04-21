package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.wrongquestion.WrongQuestionPageQuery;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.wrongquestion.WrongQuestionVO;

public interface WrongQuestionService {

    PageVO<WrongQuestionVO> pageWrongQuestions(Long userId, WrongQuestionPageQuery query);

    WrongQuestionVO getWrongQuestion(Long userId, Long answerId);

    void removeFromWrongBook(Long userId, Long answerId);
}
