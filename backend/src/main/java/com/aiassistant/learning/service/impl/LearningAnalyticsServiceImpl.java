package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import com.aiassistant.learning.entity.PracticeAnswer;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.service.LearningAnalyticsService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.analytics.LearningAnalyticsOverviewVO;
import com.aiassistant.learning.vo.analytics.MasteryDistributionVO;
import com.aiassistant.learning.vo.analytics.MaterialPerformanceVO;
import com.aiassistant.learning.vo.analytics.PracticeTrendPointVO;
import com.aiassistant.learning.vo.analytics.QuestionTypePerformanceVO;
import com.aiassistant.learning.vo.analytics.WeakKnowledgePointVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LearningAnalyticsServiceImpl implements LearningAnalyticsService {

    private static final String SESSION_STATUS_SUBMITTED = "SUBMITTED";
    private static final String REVIEW_MODE_AI_PENDING = "AI_PENDING";
    private static final String DEFAULT_KNOWLEDGE_POINT = "未标注知识点";

    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;
    private final QuestionItemMapper questionItemMapper;
    private final StudyMaterialService studyMaterialService;

    public LearningAnalyticsServiceImpl(
            PracticeSessionMapper practiceSessionMapper,
            PracticeAnswerMapper practiceAnswerMapper,
            QuestionItemMapper questionItemMapper,
            StudyMaterialService studyMaterialService
    ) {
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceAnswerMapper = practiceAnswerMapper;
        this.questionItemMapper = questionItemMapper;
        this.studyMaterialService = studyMaterialService;
    }

    @Override
    public LearningAnalyticsOverviewVO overview(Long userId, LearningAnalyticsQuery query) {
        List<PracticeSession> sessions = practiceSessionMapper.selectList(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getSessionStatus, SESSION_STATUS_SUBMITTED)
                .eq(query.getMaterialId() != null, PracticeSession::getMaterialId, query.getMaterialId())
                .orderByAsc(PracticeSession::getSubmitTime));
        if (sessions.isEmpty()) {
            return emptyOverview();
        }

        Map<Long, PracticeSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(PracticeSession::getId, Function.identity()));
        List<PracticeAnswer> answers = practiceAnswerMapper.selectList(new LambdaQueryWrapper<PracticeAnswer>()
                .in(PracticeAnswer::getSessionId, sessionMap.keySet()));
        answers = answers.stream()
                .filter(answer -> !REVIEW_MODE_AI_PENDING.equalsIgnoreCase(nullToEmpty(answer.getReviewMode())))
                .toList();
        if (answers.isEmpty()) {
            return emptyOverview();
        }

        Map<Long, QuestionItem> questionMap = loadQuestionMap(answers);
        Map<Long, StudyMaterial> materialMap = loadMaterialMap(sessions);
        List<PracticeTrendPointVO> practiceTrend = buildPracticeTrend(sessions, answers, materialMap, query.getTrendLimit());
        Map<String, StatAccumulator> questionTypeStats = new HashMap<>();
        Map<Long, StatAccumulator> materialStats = new HashMap<>();
        Map<String, KnowledgeAccumulator> knowledgeStats = new HashMap<>();
        StatAccumulator totalStats = new StatAccumulator();

        for (PracticeAnswer answer : answers) {
            PracticeSession session = sessionMap.get(answer.getSessionId());
            QuestionItem question = questionMap.get(answer.getQuestionId());
            if (session == null || question == null) {
                continue;
            }

            totalStats.add(answer, question, session.getId());
            questionTypeStats.computeIfAbsent(nullToDefault(question.getQuestionType(), "UNKNOWN"), ignored -> new StatAccumulator())
                    .add(answer, question, session.getId());
            materialStats.computeIfAbsent(session.getMaterialId(), ignored -> new StatAccumulator())
                    .add(answer, question, session.getId());

            StudyMaterial material = session.getMaterialId() == null ? null : materialMap.get(session.getMaterialId());
            for (String point : splitKnowledgePoints(question.getKnowledgePoint())) {
                String key = (session.getMaterialId() == null ? "0" : session.getMaterialId()) + "::" + point;
                knowledgeStats.computeIfAbsent(
                                key,
                                ignored -> new KnowledgeAccumulator(point, session.getMaterialId(), material == null ? null : material.getTitle())
                        )
                        .add(answer, question, session);
            }
        }

        List<KnowledgeAccumulator> knowledgeAccumulators = new ArrayList<>(knowledgeStats.values());
        List<MasteryDistributionVO> distribution = buildMasteryDistribution(knowledgeAccumulators);
        List<WeakKnowledgePointVO> weakPoints = knowledgeAccumulators.stream()
                .map(KnowledgeAccumulator::toWeakVO)
                .sorted(Comparator
                        .comparing(WeakKnowledgePointVO::getMasteryPercent)
                        .thenComparing(WeakKnowledgePointVO::getWrongCount, Comparator.reverseOrder())
                        .thenComparing(WeakKnowledgePointVO::getAttemptCount, Comparator.reverseOrder()))
                .limit(8)
                .toList();

        return LearningAnalyticsOverviewVO.builder()
                .totalPracticeCount(sessions.size())
                .totalQuestionAttempts(totalStats.attemptCount)
                .wrongAttemptCount(totalStats.wrongCount)
                .totalKnowledgePoints(knowledgeAccumulators.size())
                .weakKnowledgePointCount((int) knowledgeAccumulators.stream().filter(item -> item.masteryPercent() < 70).count())
                .averageAccuracyRate(totalStats.accuracyRate())
                .averageScoreRate(totalStats.scoreRate())
                .masteryDistribution(distribution)
                .questionTypePerformance(buildQuestionTypePerformance(questionTypeStats))
                .materialPerformance(buildMaterialPerformance(materialStats, materialMap))
                .practiceTrend(practiceTrend)
                .weakKnowledgePoints(weakPoints)
                .build();
    }

    private Map<Long, QuestionItem> loadQuestionMap(List<PracticeAnswer> answers) {
        Set<Long> questionIds = answers.stream()
                .map(PracticeAnswer::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return questionItemMapper.selectList(new LambdaQueryWrapper<QuestionItem>()
                        .in(QuestionItem::getId, questionIds))
                .stream()
                .collect(Collectors.toMap(QuestionItem::getId, Function.identity()));
    }

    private Map<Long, StudyMaterial> loadMaterialMap(List<PracticeSession> sessions) {
        Set<Long> materialIds = sessions.stream()
                .map(PracticeSession::getMaterialId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (materialIds.isEmpty()) {
            return Map.of();
        }
        return studyMaterialService.listByIds(materialIds).stream()
                .collect(Collectors.toMap(StudyMaterial::getId, Function.identity()));
    }

    private List<PracticeTrendPointVO> buildPracticeTrend(
            List<PracticeSession> sessions,
            List<PracticeAnswer> answers,
            Map<Long, StudyMaterial> materialMap,
            Integer trendLimit
    ) {
        Map<Long, List<PracticeAnswer>> answersBySession = answers.stream()
                .collect(Collectors.groupingBy(PracticeAnswer::getSessionId));
        int limit = trendLimit == null ? 12 : trendLimit;
        return sessions.stream()
                .sorted(Comparator.comparing(PracticeSession::getSubmitTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .skip(Math.max(0, sessions.size() - limit))
                .map(session -> {
                    List<PracticeAnswer> sessionAnswers = answersBySession.getOrDefault(session.getId(), List.of());
                    int wrongCount = (int) sessionAnswers.stream()
                            .filter(answer -> answer.getMarkedWrong() != null && answer.getMarkedWrong() == 1
                                    || answer.getIsCorrect() != null && answer.getIsCorrect() == 0)
                            .count();
                    StudyMaterial material = session.getMaterialId() == null ? null : materialMap.get(session.getMaterialId());
                    return PracticeTrendPointVO.builder()
                            .sessionId(session.getId())
                            .sessionName(session.getSessionName())
                            .materialId(session.getMaterialId())
                            .materialTitle(material == null ? null : material.getTitle())
                            .totalQuestions(safeInt(session.getTotalQuestions()))
                            .correctCount(safeInt(session.getCorrectCount()))
                            .wrongCount(wrongCount)
                            .totalScore(safeInt(session.getTotalScore()))
                            .obtainedScore(safeInt(session.getObtainedScore()))
                            .accuracyRate(session.getAccuracyRate() == null ? BigDecimal.ZERO : session.getAccuracyRate())
                            .scoreRate(percent(safeInt(session.getObtainedScore()), safeInt(session.getTotalScore())))
                            .submitTime(session.getSubmitTime())
                            .build();
                })
                .toList();
    }

    private List<QuestionTypePerformanceVO> buildQuestionTypePerformance(Map<String, StatAccumulator> stats) {
        return stats.entrySet().stream()
                .map(entry -> QuestionTypePerformanceVO.builder()
                        .questionType(entry.getKey())
                        .questionTypeLabel(questionTypeLabel(entry.getKey()))
                        .attemptCount(entry.getValue().attemptCount)
                        .correctCount(entry.getValue().correctCount)
                        .wrongCount(entry.getValue().wrongCount)
                        .accuracyRate(entry.getValue().accuracyRate())
                        .scoreRate(entry.getValue().scoreRate())
                        .build())
                .sorted(Comparator.comparing(QuestionTypePerformanceVO::getAttemptCount).reversed())
                .toList();
    }

    private List<MaterialPerformanceVO> buildMaterialPerformance(
            Map<Long, StatAccumulator> stats,
            Map<Long, StudyMaterial> materialMap
    ) {
        return stats.entrySet().stream()
                .map(entry -> {
                    StudyMaterial material = entry.getKey() == null ? null : materialMap.get(entry.getKey());
                    StatAccumulator stat = entry.getValue();
                    return MaterialPerformanceVO.builder()
                            .materialId(entry.getKey())
                            .materialTitle(material == null ? "未关联资料" : material.getTitle())
                            .practiceCount(stat.sessionIds.size())
                            .attemptCount(stat.attemptCount)
                            .wrongCount(stat.wrongCount)
                            .accuracyRate(stat.accuracyRate())
                            .scoreRate(stat.scoreRate())
                            .build();
                })
                .sorted(Comparator.comparing(MaterialPerformanceVO::getAttemptCount).reversed())
                .limit(8)
                .toList();
    }

    private List<MasteryDistributionVO> buildMasteryDistribution(List<KnowledgeAccumulator> knowledgeAccumulators) {
        Map<MasteryLevel, Integer> counter = new HashMap<>();
        for (MasteryLevel level : MasteryLevel.values()) {
            counter.put(level, 0);
        }
        for (KnowledgeAccumulator accumulator : knowledgeAccumulators) {
            MasteryLevel level = MasteryLevel.resolve(accumulator.masteryPercent());
            counter.put(level, counter.get(level) + 1);
        }
        int total = knowledgeAccumulators.size();
        List<MasteryDistributionVO> result = new ArrayList<>();
        for (MasteryLevel level : MasteryLevel.values()) {
            int count = counter.get(level);
            result.add(MasteryDistributionVO.builder()
                    .level(level.name())
                    .label(level.label)
                    .count(count)
                    .percent(percent(count, total))
                    .build());
        }
        return result;
    }

    private List<String> splitKnowledgePoints(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of(DEFAULT_KNOWLEDGE_POINT);
        }
        String normalized = raw.trim()
                .replace('\n', '，')
                .replace('\r', '，')
                .replace('、', '，')
                .replace('；', '，')
                .replace(';', '，')
                .replace('|', '，')
                .replace('/', '，');
        List<String> points = new ArrayList<>();
        for (String part : normalized.split("，")) {
            String point = part.trim();
            if (StringUtils.hasText(point) && point.length() <= 80) {
                points.add(point);
            }
        }
        if (points.isEmpty()) {
            return List.of(raw.trim());
        }
        return points.stream().distinct().toList();
    }

    private LearningAnalyticsOverviewVO emptyOverview() {
        return LearningAnalyticsOverviewVO.builder()
                .totalPracticeCount(0)
                .totalQuestionAttempts(0)
                .wrongAttemptCount(0)
                .totalKnowledgePoints(0)
                .weakKnowledgePointCount(0)
                .averageAccuracyRate(BigDecimal.ZERO)
                .averageScoreRate(BigDecimal.ZERO)
                .masteryDistribution(buildMasteryDistribution(List.of()))
                .questionTypePerformance(List.of())
                .materialPerformance(List.of())
                .practiceTrend(List.of())
                .weakKnowledgePoints(List.of())
                .build();
    }

    private String questionTypeLabel(String questionType) {
        return switch (nullToDefault(questionType, "UNKNOWN").toUpperCase()) {
            case "SINGLE" -> "单选题";
            case "JUDGE" -> "判断题";
            case "SHORT", "SHORT_ANSWER" -> "简答题";
            default -> "其他题型";
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private static final class StatAccumulator {
        private final Set<Long> sessionIds = new HashSet<>();
        private int attemptCount;
        private int correctCount;
        private int wrongCount;
        private int totalScore;
        private int obtainedScore;

        private void add(PracticeAnswer answer, QuestionItem question, Long sessionId) {
            attemptCount++;
            if (sessionId != null) {
                sessionIds.add(sessionId);
            }
            if (answer.getIsCorrect() != null && answer.getIsCorrect() == 1) {
                correctCount++;
            }
            if (answer.getMarkedWrong() != null && answer.getMarkedWrong() == 1
                    || answer.getIsCorrect() != null && answer.getIsCorrect() == 0) {
                wrongCount++;
            }
            totalScore += safeInt(question.getScore());
            obtainedScore += safeInt(answer.getObtainedScore());
        }

        private BigDecimal accuracyRate() {
            return percent(correctCount, attemptCount);
        }

        private BigDecimal scoreRate() {
            return percent(obtainedScore, totalScore);
        }
    }

    private static final class KnowledgeAccumulator {
        private final String knowledgePoint;
        private final Long materialId;
        private final String materialTitle;
        private int attemptCount;
        private int wrongCount;
        private int totalScore;
        private int obtainedScore;
        private LocalDateTime lastPracticeTime;

        private KnowledgeAccumulator(String knowledgePoint, Long materialId, String materialTitle) {
            this.knowledgePoint = knowledgePoint;
            this.materialId = materialId;
            this.materialTitle = materialTitle;
        }

        private void add(PracticeAnswer answer, QuestionItem question, PracticeSession session) {
            attemptCount++;
            if (answer.getMarkedWrong() != null && answer.getMarkedWrong() == 1
                    || answer.getIsCorrect() != null && answer.getIsCorrect() == 0) {
                wrongCount++;
            }
            totalScore += safeInt(question.getScore());
            obtainedScore += safeInt(answer.getObtainedScore());
            LocalDateTime candidateTime = answer.getAnswerTime() != null ? answer.getAnswerTime() : session.getSubmitTime();
            if (candidateTime != null && (lastPracticeTime == null || candidateTime.isAfter(lastPracticeTime))) {
                lastPracticeTime = candidateTime;
            }
        }

        private int masteryPercent() {
            BigDecimal rate = totalScore > 0 ? percent(obtainedScore, totalScore) : percent(attemptCount - wrongCount, attemptCount);
            return rate.setScale(0, RoundingMode.HALF_UP).intValue();
        }

        private WeakKnowledgePointVO toWeakVO() {
            return WeakKnowledgePointVO.builder()
                    .knowledgePoint(knowledgePoint)
                    .materialId(materialId)
                    .materialTitle(materialTitle)
                    .attemptCount(attemptCount)
                    .wrongCount(wrongCount)
                    .masteryPercent(masteryPercent())
                    .scoreRate(totalScore > 0 ? percent(obtainedScore, totalScore) : percent(attemptCount - wrongCount, attemptCount))
                    .lastPracticeTime(lastPracticeTime)
                    .build();
        }
    }

    private enum MasteryLevel {
        MASTERED("已掌握"),
        GOOD("基本掌握"),
        WEAK("待巩固"),
        RISK("薄弱");

        private final String label;

        MasteryLevel(String label) {
            this.label = label;
        }

        private static MasteryLevel resolve(int masteryPercent) {
            if (masteryPercent >= 85) {
                return MASTERED;
            }
            if (masteryPercent >= 70) {
                return GOOD;
            }
            if (masteryPercent >= 50) {
                return WEAK;
            }
            return RISK;
        }
    }
}
