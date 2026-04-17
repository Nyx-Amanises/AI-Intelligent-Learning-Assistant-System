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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PracticeServiceImpl implements PracticeService {

    private static final String REVIEW_MODE_RULE = "RULE";

    private static final String REVIEW_MODE_AI = "AI";

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
            AnswerReview review = evaluateAnswer(question, userAnswer);
            if (review.correct()) {
                correctCount++;
            }
            obtainedScore += review.score();

            PracticeAnswer answer = new PracticeAnswer();
            answer.setSessionId(session.getId());
            answer.setQuestionId(question.getId());
            answer.setUserAnswer(userAnswer);
            answer.setIsCorrect(review.correct() ? 1 : 0);
            answer.setObtainedScore(review.score());
            answer.setAnswerTime(LocalDateTime.now());
            answer.setMarkedWrong(review.correct() ? 0 : 1);
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
                        .questionSetId(item.getQuestionSetId())
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePracticeSession(Long userId, Long sessionId) {
        PracticeSession session = practiceSessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getId, sessionId)
                .eq(PracticeSession::getUserId, userId)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(404, "练习记录不存在");
        }

        practiceAnswerMapper.delete(new LambdaQueryWrapper<PracticeAnswer>()
                .eq(PracticeAnswer::getSessionId, sessionId));
        practiceSessionMapper.deleteById(sessionId);
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

    private AnswerReview evaluateAnswer(QuestionItem question, String userAnswer) {
        if ("SHORT".equalsIgnoreCase(question.getQuestionType())) {
            return evaluateShortAnswer(question, userAnswer);
        }
        boolean correct = isCorrect(question, userAnswer);
        return new AnswerReview(
                correct,
                correct ? safeScore(question.getScore()) : 0,
                REVIEW_MODE_RULE,
                correct ? "规则判定：答案匹配" : "规则判定：答案不匹配",
                correct ? "系统根据标准答案完成自动判定。" : "系统按标准答案精确匹配，当前答案未命中参考答案。"
        );
    }

    private boolean isCorrect(QuestionItem question, String userAnswer) {
        if (!StringUtils.hasText(userAnswer) || !StringUtils.hasText(question.getCorrectAnswer())) {
            return false;
        }
        return question.getCorrectAnswer().trim().equalsIgnoreCase(userAnswer.trim());
    }

    private AnswerReview evaluateShortAnswer(QuestionItem question, String userAnswer) {
        int fullScore = safeScore(question.getScore());
        if (!StringUtils.hasText(userAnswer)) {
            return new AnswerReview(
                    false,
                    0,
                    REVIEW_MODE_AI,
                    "AI 判分：0/" + fullScore,
                    "未作答，AI 无法识别关键知识点。"
            );
        }

        String referenceAnswer = question.getCorrectAnswer();
        if (!StringUtils.hasText(referenceAnswer)) {
            return new AnswerReview(
                    false,
                    0,
                    REVIEW_MODE_AI,
                    "AI 判分：0/" + fullScore,
                    "题目暂未配置参考答案，当前仅展示学生作答。"
            );
        }

        String normalizedUser = normalizeText(userAnswer);
        String normalizedReference = normalizeText(referenceAnswer);
        if (!StringUtils.hasText(normalizedUser) || !StringUtils.hasText(normalizedReference)) {
            return new AnswerReview(
                    false,
                    0,
                    REVIEW_MODE_AI,
                    "AI 判分：0/" + fullScore,
                    "答案文本过短或缺少有效内容，无法完成可信判分。"
            );
        }

        if (normalizedUser.equals(normalizedReference)) {
            return new AnswerReview(
                    true,
                    fullScore,
                    REVIEW_MODE_AI,
                    "AI 判分：" + fullScore + "/" + fullScore,
                    "答案与参考答案高度一致，核心知识点覆盖完整。"
            );
        }

        double coverage = calculateCoverage(normalizedReference, normalizedUser);
        double lengthRatio = calculateLengthRatio(normalizedReference, normalizedUser);
        double keywordCoverage = calculateKeywordCoverage(referenceAnswer, userAnswer);
        double similarity = Math.min(1.0, coverage * 0.45 + lengthRatio * 0.2 + keywordCoverage * 0.35);

        if (normalizedUser.contains(normalizedReference) || normalizedReference.contains(normalizedUser)) {
            similarity = Math.max(similarity, 0.82);
        }

        int awardedScore;
        String feedback;
        if (similarity >= 0.85) {
            awardedScore = fullScore;
            feedback = "答案覆盖了大部分核心要点，表述已接近人工参考答案。";
        } else if (similarity >= 0.65) {
            awardedScore = Math.max(1, Math.round(fullScore * 0.7f));
            feedback = "答案命中了主要知识点，但仍有个别关键表述不完整。";
        } else if (similarity >= 0.4) {
            awardedScore = Math.max(1, Math.round(fullScore * 0.4f));
            feedback = "答案只覆盖了部分知识点，建议对照参考答案补全关键概念。";
        } else {
            awardedScore = 0;
            feedback = "答案与参考答案重合较少，核心知识点覆盖不足。";
        }

        boolean correct = awardedScore >= Math.max(1, Math.round(fullScore * 0.6f));
        return new AnswerReview(
                correct,
                awardedScore,
                REVIEW_MODE_AI,
                "AI 判分：" + awardedScore + "/" + fullScore,
                feedback
        );
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "")
                .replace("，", "")
                .replace("。", "")
                .replace("；", "")
                .replace("：", "");
    }

    private double calculateCoverage(String reference, String userAnswer) {
        if (!StringUtils.hasText(reference) || !StringUtils.hasText(userAnswer)) {
            return 0;
        }
        int matched = 0;
        for (int i = 0; i < reference.length(); i++) {
            if (userAnswer.indexOf(reference.charAt(i)) >= 0) {
                matched++;
            }
        }
        return Math.min(1.0, matched * 1.0 / Math.max(1, reference.length()));
    }

    private double calculateLengthRatio(String reference, String userAnswer) {
        if (!StringUtils.hasText(reference) || !StringUtils.hasText(userAnswer)) {
            return 0;
        }
        int longer = Math.max(reference.length(), userAnswer.length());
        int shorter = Math.min(reference.length(), userAnswer.length());
        return longer == 0 ? 0 : shorter * 1.0 / longer;
    }

    private double calculateKeywordCoverage(String referenceAnswer, String userAnswer) {
        Set<String> keywords = extractKeywords(referenceAnswer);
        if (keywords.isEmpty() || !StringUtils.hasText(userAnswer)) {
            return 0;
        }
        int matched = 0;
        for (String keyword : keywords) {
            if (userAnswer.contains(keyword)) {
                matched++;
            }
        }
        return matched * 1.0 / keywords.size();
    }

    private Set<String> extractKeywords(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : text.split("[，。；：、,.;:\\s()（）]+")) {
            String item = token.trim();
            if (item.length() >= 2) {
                keywords.add(item);
            }
        }
        if (keywords.isEmpty()) {
            keywords.add(text.trim());
        }
        return keywords;
    }

    private int safeScore(Integer score) {
        return score == null || score <= 0 ? 1 : score;
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
                    String userAnswer = answer == null ? null : answer.getUserAnswer();
                    AnswerReview review = evaluateAnswer(question, userAnswer);
                    return PracticeAnswerVO.builder()
                            .questionId(question.getId())
                            .questionType(question.getQuestionType())
                            .stemText(question.getStemText())
                            .optionA(question.getOptionA())
                            .optionB(question.getOptionB())
                            .optionC(question.getOptionC())
                            .optionD(question.getOptionD())
                            .correctAnswer(question.getCorrectAnswer())
                            .referenceAnswer(question.getCorrectAnswer())
                            .userAnswer(userAnswer)
                            .isCorrect(answer == null ? null : answer.getIsCorrect())
                            .obtainedScore(answer == null ? null : answer.getObtainedScore())
                            .aiScore(review.score())
                            .reviewMode(review.mode())
                            .reviewLabel(review.label())
                            .reviewComment(review.comment())
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

    private record AnswerReview(
            boolean correct,
            int score,
            String mode,
            String label,
            String comment
    ) {
    }
}
