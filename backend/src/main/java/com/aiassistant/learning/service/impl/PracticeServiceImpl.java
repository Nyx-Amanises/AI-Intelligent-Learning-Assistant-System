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
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.practice.PracticeAnswerVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.aiassistant.learning.vo.practice.PracticeReviewStatusVO;
import com.aiassistant.learning.vo.practice.PracticeSessionPageVO;
import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PracticeServiceImpl implements PracticeService {

    private static final String REVIEW_MODE_RULE = "RULE";
    private static final String REVIEW_MODE_AI = "AI";
    private static final String REVIEW_MODE_AI_PENDING = "AI_PENDING";
    private static final String QUESTION_TYPE_SHORT = "SHORT";
    private static final String QUESTION_TYPE_SHORT_ANSWER = "SHORT_ANSWER";
    private static final String SESSION_STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String SESSION_STATUS_SUBMITTED = "SUBMITTED";
    private static final int SHORT_ANSWER_SOURCE_LIMIT = 2;
    private static final int SHORT_ANSWER_SOURCE_CANDIDATE_LIMIT = 8;
    private static final Set<String> SHORT_ANSWER_LOW_SIGNAL_MARKERS = Set.of(
            "参考文档", "参考链接", "参考资料", "附录", "目录", "模板", "资源", "官网", "官方文档", "mdpress"
    );
    private static final Set<String> SHORT_ANSWER_KEYWORD_STOP_WORDS = Set.of(
            "根据", "资料", "材料", "内容", "简要", "说明", "概括", "写出", "列出", "任意", "其中",
            "一个", "两个", "三个", "四个", "答案", "学生", "题目", "示例", "即可", "作答", "回答"
    );

    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;
    private final QuestionSetMapper questionSetMapper;
    private final QuestionItemMapper questionItemMapper;
    private final AiConfigService aiConfigService;
    private final AiChatService aiChatService;
    private final RetrievalService retrievalService;
    private final ObjectMapper objectMapper;
    private final PracticeService selfPracticeService;

    public PracticeServiceImpl(
            PracticeSessionMapper practiceSessionMapper,
            PracticeAnswerMapper practiceAnswerMapper,
            QuestionSetMapper questionSetMapper,
            QuestionItemMapper questionItemMapper,
            AiConfigService aiConfigService,
            AiChatService aiChatService,
            RetrievalService retrievalService,
            ObjectMapper objectMapper,
            @Lazy PracticeService selfPracticeService
    ) {
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceAnswerMapper = practiceAnswerMapper;
        this.questionSetMapper = questionSetMapper;
        this.questionItemMapper = questionItemMapper;
        this.aiConfigService = aiConfigService;
        this.aiChatService = aiChatService;
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
        this.selfPracticeService = selfPracticeService;
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
        session.setSessionStatus(SESSION_STATUS_IN_PROGRESS);
        practiceSessionMapper.insert(session);

        return buildPracticeDetail(userId, session, questions, List.of());
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
            AnswerReview review = isShortAnswer(question.getQuestionType())
                    ? buildPendingShortAnswerReview(question, userAnswer)
                    : evaluateObjectiveAnswer(question, userAnswer);

            if (!REVIEW_MODE_AI_PENDING.equals(review.mode()) && review.correct()) {
                correctCount++;
            }
            if (!REVIEW_MODE_AI_PENDING.equals(review.mode())) {
                obtainedScore += review.score();
            }

            PracticeAnswer answer = new PracticeAnswer();
            answer.setSessionId(session.getId());
            answer.setQuestionId(question.getId());
            answer.setUserAnswer(userAnswer);
            answer.setIsCorrect(REVIEW_MODE_AI_PENDING.equals(review.mode()) ? 0 : (review.correct() ? 1 : 0));
            answer.setObtainedScore(REVIEW_MODE_AI_PENDING.equals(review.mode()) ? 0 : review.score());
            answer.setReviewMode(review.mode());
            answer.setReviewComment(review.comment());
            answer.setAnswerTime(LocalDateTime.now());
            answer.setMarkedWrong(REVIEW_MODE_AI_PENDING.equals(review.mode()) ? 0 : (review.correct() ? 0 : 1));
            practiceAnswerMapper.insert(answer);
        }

        session.setSubmitTime(LocalDateTime.now());
        session.setDurationSeconds((int) ChronoUnit.SECONDS.between(session.getStartTime(), session.getSubmitTime()));
        session.setCorrectCount(correctCount);
        session.setObtainedScore(obtainedScore);
        session.setAccuracyRate(calculateAccuracy(correctCount, questions.size()));
        session.setSessionStatus(SESSION_STATUS_SUBMITTED);
        practiceSessionMapper.updateById(session);

        List<PracticeAnswer> answers = practiceAnswerMapper.selectList(new LambdaQueryWrapper<PracticeAnswer>()
                .eq(PracticeAnswer::getSessionId, session.getId())
                .orderByAsc(PracticeAnswer::getId));
        return buildPracticeDetail(userId, session, questions, answers);
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
        return buildPracticeDetail(userId, session, questions, answers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renamePracticeSession(Long userId, Long sessionId, String sessionName) {
        PracticeSession session = practiceSessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getId, sessionId)
                .eq(PracticeSession::getUserId, userId)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(404, "练习记录不存在");
        }
        session.setSessionName(normalizeSessionName(sessionName));
        practiceSessionMapper.updateById(session);
    }

    @Override
    public PracticeReviewStatusVO waitForAiReview(Long userId, Long sessionId, Long timeoutMs) {
        PracticeSession session = practiceSessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getId, sessionId)
                .eq(PracticeSession::getUserId, userId)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(404, "练习记录不存在");
        }

        long waitTimeoutMs = Math.max(1000L, Math.min(timeoutMs == null ? 60000L : timeoutMs, 120000L));
        long deadline = System.currentTimeMillis() + waitTimeoutMs;
        boolean pending = hasPendingAiReview(sessionId);
        while (pending && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            pending = hasPendingAiReview(sessionId);
        }

        return PracticeReviewStatusVO.builder()
                .sessionId(sessionId)
                .completed(!pending)
                .pending(pending)
                .build();
    }

    @Override
    @Async
    public void reviewPendingShortAnswers(Long sessionId) {
        selfPracticeService.reviewPendingShortAnswersNow(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewPendingShortAnswersNow(Long sessionId) {
        PracticeSession session = practiceSessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }

        List<QuestionItem> questions = listQuestionItems(session.getQuestionSetId());
        Map<Long, QuestionItem> questionMap = questions.stream()
                .collect(Collectors.toMap(QuestionItem::getId, Function.identity()));

        List<PracticeAnswer> answers = practiceAnswerMapper.selectList(new LambdaQueryWrapper<PracticeAnswer>()
                .eq(PracticeAnswer::getSessionId, sessionId)
                .orderByAsc(PracticeAnswer::getId));

        boolean updated = false;
        for (PracticeAnswer answer : answers) {
            if (!REVIEW_MODE_AI_PENDING.equalsIgnoreCase(answer.getReviewMode())) {
                continue;
            }
            QuestionItem question = questionMap.get(answer.getQuestionId());
            if (question == null || !isShortAnswer(question.getQuestionType())) {
                continue;
            }
            AnswerReview review = evaluateShortAnswerWithAi(
                    session.getUserId(),
                    session.getMaterialId(),
                    question,
                    answer.getUserAnswer()
            );
            answer.setIsCorrect(review.correct() ? 1 : 0);
            answer.setObtainedScore(review.score());
            answer.setReviewMode(review.mode());
            answer.setReviewComment(review.comment());
            answer.setMarkedWrong(review.correct() ? 0 : 1);
            practiceAnswerMapper.updateById(answer);
            updated = true;
        }

        if (updated) {
            refreshSessionScore(sessionId);
        }
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

    private boolean hasPendingAiReview(Long sessionId) {
        return practiceAnswerMapper.selectCount(new LambdaQueryWrapper<PracticeAnswer>()
                .eq(PracticeAnswer::getSessionId, sessionId)
                .eq(PracticeAnswer::getReviewMode, REVIEW_MODE_AI_PENDING)) > 0;
    }

    private void refreshSessionScore(Long sessionId) {
        PracticeSession session = practiceSessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }

        List<PracticeAnswer> answers = practiceAnswerMapper.selectList(new LambdaQueryWrapper<PracticeAnswer>()
                .eq(PracticeAnswer::getSessionId, sessionId));

        int correctCount = (int) answers.stream()
                .filter(answer -> answer.getIsCorrect() != null && answer.getIsCorrect() == 1)
                .count();
        int obtainedScore = answers.stream()
                .map(PracticeAnswer::getObtainedScore)
                .filter(score -> score != null && score > 0)
                .mapToInt(Integer::intValue)
                .sum();

        session.setCorrectCount(correctCount);
        session.setObtainedScore(obtainedScore);
        session.setAccuracyRate(calculateAccuracy(correctCount, session.getTotalQuestions() == null ? 0 : session.getTotalQuestions()));
        practiceSessionMapper.updateById(session);
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

    private AnswerReview evaluateObjectiveAnswer(QuestionItem question, String userAnswer) {
        boolean correct = isCorrect(question, userAnswer);
        return new AnswerReview(
                correct,
                correct ? safeScore(question.getScore()) : 0,
                REVIEW_MODE_RULE,
                correct ? "规则判定：答案匹配" : "规则判定：答案不匹配",
                correct ? "系统已根据标准答案完成判定。" : "系统已根据标准答案完成判定，当前答案未命中参考答案。"
        );
    }

    private AnswerReview buildPendingShortAnswerReview(QuestionItem question, String userAnswer) {
        int fullScore = safeScore(question.getScore());
        if (!StringUtils.hasText(userAnswer)) {
            return new AnswerReview(
                    false,
                    0,
                    REVIEW_MODE_AI,
                    "AI 判分：0/" + fullScore,
                    "未作答，AI 无法完成评分。"
            );
        }
        return new AnswerReview(
                false,
                0,
                REVIEW_MODE_AI_PENDING,
                "AI 评分中",
                "客观题已完成判分，简答题正在由 AI 评分，请稍后刷新查看。"
        );
    }

    private boolean isCorrect(QuestionItem question, String userAnswer) {
        if (!StringUtils.hasText(userAnswer) || !StringUtils.hasText(question.getCorrectAnswer())) {
            return false;
        }
        return question.getCorrectAnswer().trim().equalsIgnoreCase(userAnswer.trim());
    }

    private AnswerReview evaluateShortAnswerWithAi(
            Long userId,
            Long materialId,
            QuestionItem question,
            String userAnswer
    ) {
        int fullScore = safeScore(question.getScore());
        if (!StringUtils.hasText(userAnswer)) {
            return new AnswerReview(
                    false,
                    0,
                    REVIEW_MODE_AI,
                    "AI 判分：0/" + fullScore,
                    "未作答，AI 无法识别到有效答题内容。"
            );
        }

        String referenceAnswer = trimToNull(question.getCorrectAnswer());
        if (!StringUtils.hasText(referenceAnswer)) {
            return new AnswerReview(
                    false,
                    0,
                    REVIEW_MODE_RULE,
                    "规则评分：0/" + fullScore,
                    "题目暂未配置参考答案，无法执行 AI 判分。"
            );
        }

        List<RetrievedSegmentVO> sourceSegments = resolvePracticeAnswerSourceSegments(
                userId,
                materialId,
                question
        );

        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        if (!Boolean.TRUE.equals(config.enabled())
                || Boolean.TRUE.equals(config.mockMode())
                || !StringUtils.hasText(config.apiKey())) {
            return buildRuleFallbackReview(question, userAnswer, referenceAnswer, fullScore,
                    "AI 判分暂时不可用，已按参考答案规则暂代评分。");
        }

        try {
            String modelName = StringUtils.hasText(config.defaultModel()) ? config.defaultModel() : "gpt-4o-mini";
            String content = aiChatService.chat(
                    buildShortAnswerSystemPrompt(),
                    buildShortAnswerUserPrompt(question, userAnswer, referenceAnswer, fullScore, sourceSegments),
                    modelName,
                    0.2
            );
            ShortAnswerAiReviewPayload payload = parseShortAnswerReview(content);
            int score = clampScore(payload.score(), fullScore);
            boolean correct = payload.correct() != null
                    ? payload.correct()
                    : score >= Math.max(1, Math.round(fullScore * 0.6f));
            String comment = StringUtils.hasText(payload.comment())
                    ? payload.comment().trim()
                    : "AI 已根据参考答案完成判分。";
            return new AnswerReview(
                    correct,
                    score,
                    REVIEW_MODE_AI,
                    "AI 判分：" + score + "/" + fullScore,
                    comment
            );
        } catch (Exception exception) {
            return buildRuleFallbackReview(
                    question,
                    userAnswer,
                    referenceAnswer,
                    fullScore,
                    "AI 判分失败，已按参考答案规则暂代评分。"
            );
        }
    }

    private AnswerReview buildRuleFallbackReview(
            QuestionItem question,
            String userAnswer,
            String referenceAnswer,
            int fullScore,
            String prefixComment
    ) {
        String normalizedUser = normalizeText(userAnswer);
        String normalizedReference = normalizeText(referenceAnswer);
        if (!StringUtils.hasText(normalizedUser) || !StringUtils.hasText(normalizedReference)) {
            return new AnswerReview(
                    false,
                    0,
                    REVIEW_MODE_RULE,
                    "规则评分：0/" + fullScore,
                    prefixComment + " 答案文本过短或缺少有效内容。"
            );
        }

        if (normalizedUser.equals(normalizedReference)) {
            return new AnswerReview(
                    true,
                    fullScore,
                    REVIEW_MODE_RULE,
                    "规则评分：" + fullScore + "/" + fullScore,
                    prefixComment + " 答案与参考答案高度一致。"
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
            feedback = "答案覆盖了大部分核心要点，表述已经接近参考答案。";
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
                REVIEW_MODE_RULE,
                "规则评分：" + awardedScore + "/" + fullScore,
                prefixComment + " " + feedback
        );
    }

    private String buildShortAnswerSystemPrompt() {
        return """
                你是一名严谨的中文主观题阅卷助手。
                请严格依据题目、资料摘录、参考答案、题目解析和学生答案给出分数。
                评分要求：
                1. score 必须是 0 到满分之间的整数。
                2. 可以给部分分，但不要超过满分。
                3. comment 使用中文，简洁说明命中的要点与不足，控制在 80 字以内。
                4. correct 表示是否达到及格线，默认按 60% 满分判断。
                5. 只返回 JSON，不要返回 Markdown，不要输出额外解释。
                返回格式：
                {
                  "score": 6,
                  "correct": true,
                  "comment": "答案覆盖了……，但还缺少……"
                }
                """;
    }

    private String buildShortAnswerUserPrompt(
            QuestionItem question,
            String userAnswer,
            String referenceAnswer,
            int fullScore,
            List<RetrievedSegmentVO> sourceSegments
    ) {
        String analysis = StringUtils.hasText(question.getAnswerAnalysis()) ? question.getAnswerAnalysis().trim() : "无";
        return """
                请为下面这道简答题评分。

                题目：
                %s

                满分：
                %d

                参考答案：
                %s

                题目解析：
                %s

                评分时请优先参考最贴近题目要点的资料摘录，不要泛化到目录、附录、参考链接。

                资料摘录：
                %s

                学生答案：
                %s
                """.formatted(
                question.getStemText(),
                fullScore,
                referenceAnswer,
                analysis,
                formatRagSegments(sourceSegments),
                userAnswer
        );
    }

    private List<RetrievedSegmentVO> resolvePracticeAnswerSourceSegments(
            Long userId,
            Long materialId,
            QuestionItem question
    ) {
        if (userId == null || materialId == null || question == null) {
            return List.of();
        }

        try {
            List<RetrievedSegment> segments = retrievalService.retrieveMaterialSegments(
                    userId,
                    materialId,
                    buildPracticeRetrievalQuery(question),
                    SHORT_ANSWER_SOURCE_CANDIDATE_LIMIT
            );
            return selectPracticeSourceSegments(question, AiQuestionServiceImpl.toRetrievedSegmentVOList(segments));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String buildPracticeRetrievalQuery(QuestionItem question) {
        StringBuilder builder = new StringBuilder();
        builder.append(trimForQuery(question.getStemText(), 80)).append(" ");
        if (StringUtils.hasText(question.getKnowledgePoint())) {
            builder.append(question.getKnowledgePoint().trim()).append(" ");
        }
        for (String keyword : buildPracticeFocusKeywords(question)) {
            builder.append(trimForQuery(keyword, 24)).append(" ");
        }
        if (StringUtils.hasText(question.getAnswerAnalysis())) {
            builder.append(trimForQuery(question.getAnswerAnalysis(), 60)).append(" ");
        }
        builder.append("核心知识点 关键要点 定义 特点 作用 原理");
        return builder.toString().trim();
    }

    private String formatRagSegments(List<RetrievedSegmentVO> sourceSegments) {
        if (sourceSegments == null || sourceSegments.isEmpty()) {
            return "暂无命中的资料摘录，可仅结合参考答案与题目解析评分。";
        }

        StringBuilder builder = new StringBuilder();
        for (RetrievedSegmentVO segment : sourceSegments) {
            builder.append("[片段#")
                    .append(segment.getSegmentNo() == null ? "--" : segment.getSegmentNo())
                    .append("]");
            if (segment.getPageNo() != null) {
                builder.append("[第 ").append(segment.getPageNo()).append(" 页]");
            }
            if (StringUtils.hasText(segment.getSectionTitle())) {
                builder.append("[")
                        .append(segment.getSectionTitle().trim())
                        .append("]");
            }
            builder.append(" ")
                    .append(trimSourceExcerpt(segment.getContentText()))
                    .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private List<RetrievedSegmentVO> selectPracticeSourceSegments(
            QuestionItem question,
            List<RetrievedSegmentVO> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<String> focusKeywords = buildPracticeFocusKeywords(question);
        List<RankedPracticeSource> rankedSources = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            RetrievedSegmentVO segment = candidates.get(index);
            rankedSources.add(new RankedPracticeSource(
                    segment,
                    scorePracticeSourceSegment(question, segment, focusKeywords),
                    index
            ));
        }

        List<RetrievedSegmentVO> selected = rankedSources.stream()
                .sorted(Comparator.comparingDouble(RankedPracticeSource::score).reversed()
                        .thenComparingInt(RankedPracticeSource::originalIndex))
                .map(RankedPracticeSource::segment)
                .limit(SHORT_ANSWER_SOURCE_LIMIT)
                .toList();
        return selected.isEmpty()
                ? candidates.stream().limit(SHORT_ANSWER_SOURCE_LIMIT).toList()
                : selected;
    }

    private List<String> buildPracticeFocusKeywords(QuestionItem question) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (question == null) {
            return List.of();
        }
        keywords.addAll(extractKeywords(trimForQuery(question.getStemText(), 80)));
        if (StringUtils.hasText(question.getKnowledgePoint())) {
            keywords.addAll(extractKeywords(question.getKnowledgePoint()));
        }
        if (StringUtils.hasText(question.getCorrectAnswer())) {
            keywords.addAll(extractKeywords(trimForQuery(question.getCorrectAnswer(), 120)));
        }
        if (StringUtils.hasText(question.getAnswerAnalysis())) {
            keywords.addAll(extractKeywords(trimForQuery(question.getAnswerAnalysis(), 60)));
        }
        return keywords.stream().limit(10).toList();
    }

    private double scorePracticeSourceSegment(
            QuestionItem question,
            RetrievedSegmentVO segment,
            List<String> focusKeywords
    ) {
        if (segment == null) {
            return 0D;
        }

        String title = trimToNull(segment.getSectionTitle());
        String content = trimToNull(segment.getContentText());
        String normalizedTitle = normalizeText(title);
        String normalizedContent = normalizeText(content);
        double semanticScore = segment.getScore() == null ? 0D : segment.getScore();
        double keywordScore = computePracticeKeywordCoverage(focusKeywords, normalizedTitle, normalizedContent);
        double score = semanticScore * 0.78D + keywordScore * 0.98D;

        String normalizedKnowledgePoint = normalizeText(question == null ? null : question.getKnowledgePoint());
        if (StringUtils.hasText(normalizedKnowledgePoint) && normalizedTitle.contains(normalizedKnowledgePoint)) {
            score += 0.22D;
        }
        if (StringUtils.hasText(normalizedKnowledgePoint) && normalizedContent.contains(normalizedKnowledgePoint)) {
            score += 0.12D;
        }
        if (containsLowSignalSourceMarker(title)) {
            score -= 0.32D;
        }
        if (containsLowSignalSourceMarker(content)) {
            score -= 0.16D;
        }

        int contentLength = content == null ? 0 : content.trim().length();
        if (contentLength >= 60 && contentLength <= 420) {
            score += 0.08D;
        } else if (contentLength > 900) {
            score -= 0.08D;
        }
        return score;
    }

    private double computePracticeKeywordCoverage(
            List<String> keywords,
            String normalizedTitle,
            String normalizedContent
    ) {
        if ((keywords == null || keywords.isEmpty())
                || (!StringUtils.hasText(normalizedTitle) && !StringUtils.hasText(normalizedContent))) {
            return 0D;
        }

        double totalWeight = 0D;
        double matchedWeight = 0D;
        for (String keyword : keywords) {
            String normalizedKeyword = normalizeText(keyword);
            if (!StringUtils.hasText(normalizedKeyword)) {
                continue;
            }
            double weight = Math.max(1D, Math.min(3.2D, normalizedKeyword.length() * 0.4D));
            totalWeight += weight;
            if (normalizedTitle.contains(normalizedKeyword)) {
                matchedWeight += weight * 1.15D;
                continue;
            }
            if (normalizedContent.contains(normalizedKeyword)) {
                matchedWeight += weight;
            }
        }
        return totalWeight <= 0 ? 0D : matchedWeight / totalWeight;
    }

    private boolean containsLowSignalSourceMarker(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.toLowerCase();
        for (String marker : SHORT_ANSWER_LOW_SIGNAL_MARKERS) {
            if (text.contains(marker) || normalized.contains(marker.toLowerCase())) {
                return true;
            }
        }
        return normalized.contains("http://") || normalized.contains("https://") || normalized.contains("www.");
    }

    private String trimSourceExcerpt(String content) {
        String normalized = trimToNull(content);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        String singleLine = normalized.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 260 ? singleLine : singleLine.substring(0, 260) + "...";
    }

    private String trimForQuery(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private ShortAnswerAiReviewPayload parseShortAnswerReview(String content) {
        try {
            JsonNode root = objectMapper.readTree(normalizeJsonContent(content));
            if (root == null || root.isMissingNode()) {
                throw new BusinessException("AI 判分结果为空");
            }
            Integer score = root.has("score") && root.get("score").canConvertToInt()
                    ? root.get("score").asInt()
                    : null;
            Boolean correct = root.has("correct") && !root.get("correct").isNull()
                    ? root.get("correct").asBoolean()
                    : null;
            String comment = trimToNull(root.path("comment").asText(null));
            if (score == null) {
                throw new BusinessException("AI 判分结果缺少 score");
            }
            return new ShortAnswerAiReviewPayload(score, correct, comment);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(500, "解析 AI 判分结果失败: " + exception.getMessage());
        }
    }

    private String normalizeJsonContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring(7).trim();
        } else if (normalized.startsWith("```")) {
            normalized = normalized.substring(3).trim();
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int clampScore(Integer score, int fullScore) {
        if (score == null) {
            return 0;
        }
        return Math.max(0, Math.min(fullScore, score));
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase().replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "");
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
        for (String token : text.split("[，。；：,.;:\\s()（）]+")) {
            String item = token.trim();
            if (item.length() >= 2 && !SHORT_ANSWER_KEYWORD_STOP_WORDS.contains(item)) {
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

    private String normalizeSessionName(String sessionName) {
        if (!StringUtils.hasText(sessionName)) {
            throw new BusinessException("练习名称不能为空");
        }
        String normalized = sessionName.trim();
        if (normalized.length() > 200) {
            throw new BusinessException("练习名称长度不能超过200个字符");
        }
        return normalized;
    }

    private PracticeDetailVO buildPracticeDetail(
            Long userId,
            PracticeSession session,
            List<QuestionItem> questions,
            List<PracticeAnswer> answers
    ) {
        Map<Long, PracticeAnswer> answerMap = answers.stream()
                .collect(Collectors.toMap(PracticeAnswer::getQuestionId, Function.identity(), (left, right) -> right));

        List<PracticeAnswerVO> answerVOS = questions.stream()
                .map(question -> {
                    PracticeAnswer answer = answerMap.get(question.getId());
                    AnswerReview review = buildReviewForDetail(question, answer);
                    List<RetrievedSegmentVO> sourceSegments = shouldResolvePracticeAnswerSources(session, question)
                            ? resolvePracticeAnswerSourceSegments(
                                    userId,
                                    session.getMaterialId(),
                                    question
                            )
                            : List.of();
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
                            .userAnswer(answer == null ? null : answer.getUserAnswer())
                            .isCorrect(answer == null ? null : answer.getIsCorrect())
                            .obtainedScore(answer == null ? null : answer.getObtainedScore())
                            .aiScore(review.score())
                            .reviewMode(review.mode())
                            .reviewLabel(review.label())
                            .reviewComment(review.comment())
                            .answerAnalysis(question.getAnswerAnalysis())
                            .sourceSegments(sourceSegments)
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

    private boolean shouldResolvePracticeAnswerSources(PracticeSession session, QuestionItem question) {
        return session != null
                && SESSION_STATUS_SUBMITTED.equalsIgnoreCase(session.getSessionStatus())
                && session.getMaterialId() != null
                && isShortAnswer(question.getQuestionType());
    }

    private AnswerReview buildReviewForDetail(QuestionItem question, PracticeAnswer answer) {
        if (answer == null) {
            return buildPendingDetailReview(question);
        }
        if (isShortAnswer(question.getQuestionType())) {
            return buildStoredShortAnswerReview(question, answer);
        }
        boolean correct = answer.getIsCorrect() != null && answer.getIsCorrect() == 1;
        return new AnswerReview(
                correct,
                answer.getObtainedScore() == null ? 0 : answer.getObtainedScore(),
                REVIEW_MODE_RULE,
                correct ? "规则判定：答案匹配" : "规则判定：答案不匹配",
                correct ? "系统已根据标准答案完成判定。" : "系统已根据标准答案完成判定，当前答案未命中参考答案。"
        );
    }

    private AnswerReview buildPendingDetailReview(QuestionItem question) {
        if (isShortAnswer(question.getQuestionType())) {
            return new AnswerReview(
                    false,
                    0,
                    REVIEW_MODE_AI,
                    "AI 判分：待提交",
                    "提交后将调用 AI 完成简答题判分。"
            );
        }
        return new AnswerReview(
                false,
                0,
                REVIEW_MODE_RULE,
                "规则判定：待提交",
                "提交后将根据标准答案完成判定。"
        );
    }

    private AnswerReview buildStoredShortAnswerReview(QuestionItem question, PracticeAnswer answer) {
        int fullScore = safeScore(question.getScore());
        String mode = StringUtils.hasText(answer.getReviewMode()) ? answer.getReviewMode() : REVIEW_MODE_RULE;
        if (REVIEW_MODE_AI_PENDING.equalsIgnoreCase(mode)) {
            return new AnswerReview(
                    false,
                    0,
                    REVIEW_MODE_AI_PENDING,
                    "AI 评分中",
                    StringUtils.hasText(answer.getReviewComment())
                            ? answer.getReviewComment()
                            : "客观题已完成判分，简答题正在由 AI 评分，请稍后刷新查看。"
            );
        }

        int score = answer.getObtainedScore() == null ? 0 : answer.getObtainedScore();
        boolean correct = answer.getIsCorrect() != null && answer.getIsCorrect() == 1;
        String labelPrefix = REVIEW_MODE_AI.equalsIgnoreCase(mode) ? "AI 判分：" : "规则评分：";
        String comment = StringUtils.hasText(answer.getReviewComment())
                ? answer.getReviewComment()
                : (REVIEW_MODE_AI.equalsIgnoreCase(mode) ? "AI 已完成本题判分。" : "已按规则完成本题评分。");
        return new AnswerReview(correct, score, mode, labelPrefix + score + "/" + fullScore, comment);
    }

    private boolean isShortAnswer(String questionType) {
        if (!StringUtils.hasText(questionType)) {
            return false;
        }
        return QUESTION_TYPE_SHORT.equalsIgnoreCase(questionType)
                || QUESTION_TYPE_SHORT_ANSWER.equalsIgnoreCase(questionType);
    }

    private record AnswerReview(
            boolean correct,
            int score,
            String mode,
            String label,
            String comment
    ) {
    }

    private record ShortAnswerAiReviewPayload(
            Integer score,
            Boolean correct,
            String comment
    ) {
    }

    private record RankedPracticeSource(
            RetrievedSegmentVO segment,
            double score,
            int originalIndex
    ) {
    }
}
