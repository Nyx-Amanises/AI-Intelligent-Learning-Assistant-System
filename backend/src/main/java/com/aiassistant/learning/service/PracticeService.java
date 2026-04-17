package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.practice.PracticeStartRequest;
import com.aiassistant.learning.dto.practice.PracticeSubmitRequest;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.aiassistant.learning.vo.practice.PracticeReviewStatusVO;
import com.aiassistant.learning.vo.practice.PracticeSessionPageVO;

public interface PracticeService {

    PracticeDetailVO startPractice(Long userId, PracticeStartRequest request);

    PracticeDetailVO submitPractice(Long userId, PracticeSubmitRequest request);

    PageVO<PracticeSessionPageVO> pagePracticeSessions(Long userId, Long current, Long size);

    PracticeDetailVO getPracticeDetail(Long userId, Long sessionId);

    PracticeReviewStatusVO waitForAiReview(Long userId, Long sessionId, Long timeoutMs);

    void reviewPendingShortAnswers(Long sessionId);

    void reviewPendingShortAnswersNow(Long sessionId);

    void deletePracticeSession(Long userId, Long sessionId);
}
