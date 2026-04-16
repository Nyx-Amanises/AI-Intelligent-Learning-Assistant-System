package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.practice.PracticeAnswerRequest;
import com.aiassistant.learning.dto.practice.PracticeStartRequest;
import com.aiassistant.learning.dto.practice.PracticeSubmitRequest;
import com.aiassistant.learning.entity.PracticeAnswer;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.practice.PracticeAnswerVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.aiassistant.learning.vo.practice.PracticeSessionPageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PracticeServiceImpl implements PracticeService {

    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;
    private final QuestionSetMapper questionSetMapper;
    private final QuestionItemMapper questionItemMapper;

    public PracticeServiceImpl(
            PracticeSessionMapper practiceSessionMapper,
            PracticeAnswerMapper practiceAnswerMapper,
            QuestionSetMapper questionSetMapper,
            QuestionItemMapper questionItemMapper
    ) {
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceAnswerMapper = practiceAnswerMapper;
        this.questionSetMapper = questionSetMapper;
        this.questionItemMapper = questionItemMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PracticeDetailVO startPractice(Long userId, PracticeStartRequest request) {
        QuestionSet questionSet = getOwnedQuestionSet(userId, request.getQuestionSetId());
        List<QuestionItem> questions = listQuestionItems(questionSet.getId());
        if (questions.isEmpty()) {
            throw new BusinessException("当前题集没有题目，无法开始练习");
        }

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setQuestionSetId(questionSet.getId());
        session.setMaterialId(questionSet.getMaterialId());
        session.setSessionName(questionSet.getTitle() + " - 练习");
        session.setStartTime(LocalDateTime.now());
        session.setTotalQuestions(questions.size());
        session.setCorrectCount(0);
        session.setTotalScore(questions.stream().mapToInt(QuestionItem::getScore).sum());
        session.setObtainedScore(0);
        session.setAccuracyRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setSessionStatus("IN_PROGRESS");
        practiceSessionMapper.insert(session);

        return buildPracticeDetail(session, questions, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PracticeDetailVO submitPractice(Long userId, PracticeSubmitRequest request) {
        PracticeSession session = practiceSessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getId, request.getSessionId())
                .eq(PracticeSession::getUserId, userId)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(404, "练习记录不存在");
        }

        List<QuestionItem> questions = listQuestionItems(session.getQuestionSetId());
        Map<Long, PracticeAnswerRequest> answerMap = request.getAnswers().stream()
                .collect(Collectors.toMap(PracticeAnswerRequest::getQuestionId, Function.identity(), (left, right) -> right));

        practiceAnswerMapper.delete(new LambdaQueryWrapper<PracticeAnswer>()
                .eq(PracticeAnswer::getSessionId, session.getId()));

        int correctCount = 0;
        int obtainedScore = 0;
        for (QuestionItem question : questions) {
            PracticeAnswerRequest answerRequest = answerMap.get(question.getId());
            String userAnswer = answerRequest == null ? null : answerRequest.getUserAnswer();
            boolean correct = isCorrect(question, userAnswer);
            if (correct) {
                correctCount++;
                obtainedScore += question.getScore();
            }

            PracticeAnswer answer = new PracticeAnswer();
            answer.setSessionId(session.getId());
            answer.setQuestionId(question.getId());
            answer.setUserAnswer(userAnswer);
            answer.setIsCorrect(correct ? 1 : 0);
            answer.setObtainedScore(correct ? question.getScore() : 0);
            answer.setAnswerTime(LocalDateTime.now());
            answer.setMarkedWrong(correct ? 0 : 1);
            practiceAnswerMapper.insert(answer);
        }

        session.setSubmitTime(LocalDateTime.now());
        session.setDurationSeconds((int) ChronoUnit.SECONDS.between(session.getStartTime(), session.getSubmitTime()));
        session.setCorrectCount(correctCount);
        session.setObtainedScore(obtainedScore);
        session.setAccuracyRate(calculateAccuracy(correctCount, questions.size()));
        session.setSessionStatus("SUBMITTED");
        practiceSessionMapper.updateById(session);

        List<PracticeAnswer> answers = practiceAnswerMapper.selectList(new LambdaQueryWrapper<PracticeAnswer>()
                .eq(PracticeAnswer::getSessionId, session.getId())
                .orderByAsc(PracticeAnswer::getId));
        return buildPracticeDetail(session, questions, answers);
    }

    @Override
    public PageVO<PracticeSessionPageVO> pagePracticeSessions(Long userId, Long current, Long size) {
        Page<PracticeSession> page = practiceSessionMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<PracticeSession>()
                        .eq(PracticeSession::getUserId, userId)
                        .orderByDesc(PracticeSession::getCreatedAt)
        );

        List<PracticeSessionPageVO> records = page.getRecords().stream()
                .map(item -> PracticeSessionPageVO.builder()
                        .id(item.getId())
                        .sessionName(item.getSessionName())
                        .totalQuestions(item.getTotalQuestions())
                        .correctCount(item.getCorrectCount())
                        .obtainedScore(item.getObtainedScore())
                        .accuracyRate(item.getAccuracyRate())
                        .sessionStatus(item.getSessionStatus())
                        .submitTime(item.getSubmitTime())
                        .build())
                .toList();

        return PageVO.<PracticeSessionPageVO>builder()
                .current(page.getCurrent())
                .size(page.getSize())
                .total(page.getTotal())
                .pages(page.getPages())
                .records(records)
                .build();
    }

    @Override
    public PracticeDetailVO getPracticeDetail(Long userId, Long sessionId) {
        PracticeSession session = practiceSessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getId, sessionId)
                .eq(PracticeSession::getUserId, userId)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(404, "练习记录不存在");
        }

        List<QuestionItem> questions = listQuestionItems(session.getQuestionSetId());
        List<PracticeAnswer> answers = practiceAnswerMapper.selectList(new LambdaQueryWrapper<PracticeAnswer>()
                .eq(PracticeAnswer::getSessionId, sessionId)
                .orderByAsc(PracticeAnswer::getId));
        return buildPracticeDetail(session, questions, answers);
    }

    private QuestionSet getOwnedQuestionSet(Long userId, Long questionSetId) {
        QuestionSet questionSet = questionSetMapper.selectOne(new LambdaQueryWrapper<QuestionSet>()
                .eq(QuestionSet::getId, questionSetId)
                .eq(QuestionSet::getUserId, userId)
                .last("limit 1"));
        if (questionSet == null) {
            throw new BusinessException(404, "题集不存在");
        }
        return questionSet;
    }

    private List<QuestionItem> listQuestionItems(Long questionSetId) {
        return questionItemMapper.selectList(new LambdaQueryWrapper<QuestionItem>()
                .eq(QuestionItem::getQuestionSetId, questionSetId)
                .orderByAsc(QuestionItem::getSortNo));
    }

    private boolean isCorrect(QuestionItem question, String userAnswer) {
        if (!StringUtils.hasText(userAnswer) || !StringUtils.hasText(question.getCorrectAnswer())) {
            return false;
        }
        return question.getCorrectAnswer().trim().equalsIgnoreCase(userAnswer.trim());
    }

    private BigDecimal calculateAccuracy(int correctCount, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(correctCount * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    private PracticeDetailVO buildPracticeDetail(
            PracticeSession session,
            List<QuestionItem> questions,
            List<PracticeAnswer> answers
    ) {
        Map<Long, PracticeAnswer> answerMap = answers.stream()
                .collect(Collectors.toMap(PracticeAnswer::getQuestionId, Function.identity(), (left, right) -> right));

        List<PracticeAnswerVO> answerVOS = questions.stream()
                .map(question -> {
                    PracticeAnswer answer = answerMap.get(question.getId());
                    return PracticeAnswerVO.builder()
                            .questionId(question.getId())
                            .stemText(question.getStemText())
                            .correctAnswer(question.getCorrectAnswer())
                            .userAnswer(answer == null ? null : answer.getUserAnswer())
                            .isCorrect(answer == null ? null : answer.getIsCorrect())
                            .obtainedScore(answer == null ? null : answer.getObtainedScore())
                            .answerAnalysis(question.getAnswerAnalysis())
                            .build();
                })
                .toList();

        return PracticeDetailVO.builder()
                .sessionId(session.getId())
                .questionSetId(session.getQuestionSetId())
                .sessionName(session.getSessionName())
                .totalQuestions(session.getTotalQuestions())
                .correctCount(session.getCorrectCount())
                .totalScore(session.getTotalScore())
                .obtainedScore(session.getObtainedScore())
                .accuracyRate(session.getAccuracyRate())
                .sessionStatus(session.getSessionStatus())
                .startTime(session.getStartTime())
                .submitTime(session.getSubmitTime())
                .answers(answerVOS)
                .build();
    }
}
