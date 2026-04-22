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

/**
 * 学习分析业务实现类。
 *
 * <p>它从已提交练习和答案记录中汇总多维度指标：
 * 整体正确率、得分率、掌握度分布、题型表现、资料表现、练习趋势和薄弱知识点。</p>
 */
@Service
public class LearningAnalyticsServiceImpl implements LearningAnalyticsService {

    /**
     * 只统计已提交的练习。
     */
    private static final String SESSION_STATUS_SUBMITTED = "SUBMITTED";

    /**
     * 排除等待 AI 判分的答案，避免统计未完成结果。
     */
    private static final String REVIEW_MODE_AI_PENDING = "AI_PENDING";

    /**
     * 题目没有知识点标注时使用的默认名称。
     */
    private static final String DEFAULT_KNOWLEDGE_POINT = "未标注知识点";

    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;
    private final QuestionItemMapper questionItemMapper;
    private final StudyMaterialService studyMaterialService;

    /**
     * 构造方法注入依赖。
     *
     * @param practiceSessionMapper 练习会话 Mapper
     * @param practiceAnswerMapper 练习答案 Mapper
     * @param questionItemMapper 题目 Mapper
     * @param studyMaterialService 学习资料服务
     */
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

    /**
     * 获取学习分析总览。
     *
     * <p>主要流程：查询已提交练习，加载答案并排除待 AI 判分答案，
     * 再分别构建趋势、题型、资料、知识点等维度的统计结果。</p>
     *
     * @param userId 当前登录用户 ID
     * @param query 查询条件
     * @return 学习分析总览
     */
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

    /**
     * 批量加载答案对应的题目。
     *
     * @param answers 答案列表
     * @return key 为题目 ID 的题目 Map
     */
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

    /**
     * 批量加载练习会话关联的资料。
     *
     * @param sessions 练习会话列表
     * @return key 为资料 ID 的资料 Map
     */
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

    /**
     * 构建练习趋势数据。
     *
     * @param sessions 练习会话列表
     * @param answers 答案列表
     * @param materialMap 资料 Map
     * @param trendLimit 趋势点数量上限
     * @return 趋势点列表
     */
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

    /**
     * 构建题型表现统计。
     *
     * @param stats key 为题型编码的统计聚合器
     * @return 题型表现列表
     */
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

    /**
     * 构建资料表现统计。
     *
     * @param stats key 为资料 ID 的统计聚合器
     * @param materialMap 资料 Map
     * @return 资料表现列表
     */
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

    /**
     * 构建掌握度等级分布。
     *
     * @param knowledgeAccumulators 知识点聚合器列表
     * @return 掌握度分布列表
     */
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

    /**
     * 拆分题目上的知识点字符串。
     *
     * @param raw 原始知识点字符串
     * @return 知识点列表
     */
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

    /**
     * 创建空的学习分析总览。
     *
     * @return 空总览对象
     */
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

    /**
     * 将题型编码转换成中文标签。
     *
     * @param questionType 题型编码
     * @return 中文题型名称
     */
    private String questionTypeLabel(String questionType) {
        return switch (nullToDefault(questionType, "UNKNOWN").toUpperCase()) {
            case "SINGLE" -> "单选题";
            case "JUDGE" -> "判断题";
            case "SHORT", "SHORT_ANSWER" -> "简答题";
            default -> "其他题型";
        };
    }

    /**
     * null 转为空字符串。
     *
     * @param value 原始字符串
     * @return 非 null 字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 空字符串使用默认值。
     *
     * @param value 原始字符串
     * @param defaultValue 默认值
     * @return 规范化后的字符串
     */
    private String nullToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 安全转换整数，空值按 0 处理。
     *
     * @param value 原始值
     * @return 非空整数
     */
    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 计算百分比。
     *
     * @param numerator 分子
     * @param denominator 分母
     * @return 百分比，保留 1 位小数
     */
    private static BigDecimal percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    /**
     * 通用统计聚合器。
     *
     * <p>用于题型维度、资料维度和总览维度的作答统计。</p>
     */
    private static final class StatAccumulator {
        private final Set<Long> sessionIds = new HashSet<>();
        private int attemptCount;
        private int correctCount;
        private int wrongCount;
        private int totalScore;
        private int obtainedScore;

        /**
         * 累加一次作答。
         *
         * @param answer 练习答案
         * @param question 题目
         * @param sessionId 练习会话 ID
         */
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

        /**
         * 计算正确率。
         *
         * @return 正确率百分比
         */
        private BigDecimal accuracyRate() {
            return percent(correctCount, attemptCount);
        }

        /**
         * 计算得分率。
         *
         * @return 得分率百分比
         */
        private BigDecimal scoreRate() {
            return percent(obtainedScore, totalScore);
        }
    }

    /**
     * 知识点统计聚合器。
     *
     * <p>用于找出薄弱知识点和计算掌握度分布。</p>
     */
    private static final class KnowledgeAccumulator {
        private final String knowledgePoint;
        private final Long materialId;
        private final String materialTitle;
        private int attemptCount;
        private int wrongCount;
        private int totalScore;
        private int obtainedScore;
        private LocalDateTime lastPracticeTime;

        /**
         * 创建知识点聚合器。
         *
         * @param knowledgePoint 知识点名称
         * @param materialId 资料 ID
         * @param materialTitle 资料标题
         */
        private KnowledgeAccumulator(String knowledgePoint, Long materialId, String materialTitle) {
            this.knowledgePoint = knowledgePoint;
            this.materialId = materialId;
            this.materialTitle = materialTitle;
        }

        /**
         * 累加一次作答。
         *
         * @param answer 练习答案
         * @param question 题目
         * @param session 练习会话
         */
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

        /**
         * 计算掌握度百分比。
         *
         * @return 掌握度百分比
         */
        private int masteryPercent() {
            BigDecimal rate = totalScore > 0 ? percent(obtainedScore, totalScore) : percent(attemptCount - wrongCount, attemptCount);
            return rate.setScale(0, RoundingMode.HALF_UP).intValue();
        }

        /**
         * 转换成薄弱知识点展示对象。
         *
         * @return 薄弱知识点 VO
         */
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

    /**
     * 掌握度等级。
     */
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
