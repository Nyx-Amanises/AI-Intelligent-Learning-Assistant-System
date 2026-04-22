package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.dto.mastery.KnowledgeMasteryQuery;
import com.aiassistant.learning.entity.PracticeAnswer;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.service.KnowledgeMasteryService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryItemVO;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryOverviewVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 知识掌握度业务实现类。
 *
 * <p>它会读取用户已提交练习中的答案记录，按知识点聚合作答次数、正确次数、
 * 错误次数、得分率，并计算掌握等级和学习建议。</p>
 */
@Service
public class KnowledgeMasteryServiceImpl implements KnowledgeMasteryService {

    /**
     * 只统计已提交的练习。
     */
    private static final String SESSION_STATUS_SUBMITTED = "SUBMITTED";

    /**
     * 排除仍在等待 AI 判分的答案，避免未完成评分影响掌握度。
     */
    private static final String REVIEW_MODE_AI_PENDING = "AI_PENDING";

    /**
     * 题目没有标注知识点时使用的默认名称。
     */
    private static final String DEFAULT_KNOWLEDGE_POINT = "未标注知识点";

    private final PracticeAnswerMapper practiceAnswerMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final QuestionItemMapper questionItemMapper;
    private final StudyMaterialService studyMaterialService;

    /**
     * 构造方法注入依赖。
     *
     * @param practiceAnswerMapper 练习答案 Mapper
     * @param practiceSessionMapper 练习会话 Mapper
     * @param questionItemMapper 题目 Mapper
     * @param studyMaterialService 学习资料服务，用于读取资料标题
     */
    public KnowledgeMasteryServiceImpl(
            PracticeAnswerMapper practiceAnswerMapper,
            PracticeSessionMapper practiceSessionMapper,
            QuestionItemMapper questionItemMapper,
            StudyMaterialService studyMaterialService
    ) {
        this.practiceAnswerMapper = practiceAnswerMapper;
        this.practiceSessionMapper = practiceSessionMapper;
        this.questionItemMapper = questionItemMapper;
        this.studyMaterialService = studyMaterialService;
    }

    /**
     * 获取知识掌握度总览。
     *
     * <p>主要流程：查练习会话、查答案、排除待 AI 判分答案、加载题目和资料，
     * 按知识点聚合后再进行筛选、排序和分页。</p>
     *
     * @param userId 当前登录用户 ID
     * @param query 查询条件
     * @return 知识掌握度总览
     */
    @Override
    public KnowledgeMasteryOverviewVO overview(Long userId, KnowledgeMasteryQuery query) {
        List<PracticeSession> sessions = practiceSessionMapper.selectList(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getSessionStatus, SESSION_STATUS_SUBMITTED)
                .eq(query.getMaterialId() != null, PracticeSession::getMaterialId, query.getMaterialId()));
        if (sessions.isEmpty()) {
            return emptyOverview(query);
        }

        Map<Long, PracticeSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(PracticeSession::getId, Function.identity()));
        List<PracticeAnswer> answers = practiceAnswerMapper.selectList(new LambdaQueryWrapper<PracticeAnswer>()
                .in(PracticeAnswer::getSessionId, sessionMap.keySet()));
        answers = answers.stream()
                .filter(answer -> !REVIEW_MODE_AI_PENDING.equalsIgnoreCase(nullToEmpty(answer.getReviewMode())))
                .toList();
        if (answers.isEmpty()) {
            return emptyOverview(query);
        }

        Map<Long, QuestionItem> questionMap = loadQuestionMap(answers);
        Map<Long, StudyMaterial> materialMap = loadMaterialMap(sessions);
        List<KnowledgeMasteryItemVO> items = aggregate(answers, sessionMap, questionMap, materialMap).stream()
                .map(KnowledgeAccumulator::toVO)
                .filter(item -> matchesQuestionType(item, query.getQuestionType()))
                .filter(item -> matchesLevel(item, query.getMasteryLevel()))
                .filter(item -> matchesKeyword(item, query.getKeyword()))
                .sorted(Comparator
                        .comparing(KnowledgeMasteryItemVO::getMasteryPercent)
                        .thenComparing(KnowledgeMasteryItemVO::getWrongCount, Comparator.reverseOrder())
                        .thenComparing(KnowledgeMasteryItemVO::getAttemptCount, Comparator.reverseOrder()))
                .toList();

        return buildOverview(query, items);
    }

    /**
     * 批量加载答案对应的题目信息。
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
     * 批量加载练习会话关联的资料信息。
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
     * 按知识点聚合作答数据。
     *
     * <p>同一道题如果标注了多个知识点，会同时计入多个知识点统计。</p>
     *
     * @param answers 答案列表
     * @param sessionMap 练习会话 Map
     * @param questionMap 题目 Map
     * @param materialMap 资料 Map
     * @return 聚合器列表
     */
    private List<KnowledgeAccumulator> aggregate(
            List<PracticeAnswer> answers,
            Map<Long, PracticeSession> sessionMap,
            Map<Long, QuestionItem> questionMap,
            Map<Long, StudyMaterial> materialMap
    ) {
        Map<String, KnowledgeAccumulator> accumulatorMap = new HashMap<>();
        for (PracticeAnswer answer : answers) {
            PracticeSession session = sessionMap.get(answer.getSessionId());
            QuestionItem question = questionMap.get(answer.getQuestionId());
            if (session == null || question == null) {
                continue;
            }
            StudyMaterial material = session.getMaterialId() == null ? null : materialMap.get(session.getMaterialId());
            List<String> knowledgePoints = splitKnowledgePoints(question.getKnowledgePoint());
            for (String point : knowledgePoints) {
                String key = (session.getMaterialId() == null ? "0" : session.getMaterialId()) + "::" + point;
                KnowledgeAccumulator accumulator = accumulatorMap.computeIfAbsent(
                        key,
                        ignored -> new KnowledgeAccumulator(point, session.getMaterialId(), material == null ? null : material.getTitle())
                );
                accumulator.add(answer, question, session);
            }
        }
        return new ArrayList<>(accumulatorMap.values());
    }

    /**
     * 拆分题目上的知识点字符串。
     *
     * <p>兼容逗号、顿号、分号、竖线、斜杠等分隔符。</p>
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
     * 根据聚合后的知识点列表构建总览返回对象。
     *
     * @param query 查询条件
     * @param items 已筛选并排序的知识点列表
     * @return 总览对象
     */
    private KnowledgeMasteryOverviewVO buildOverview(KnowledgeMasteryQuery query, List<KnowledgeMasteryItemVO> items) {
        long total = items.size();
        long current = query.getCurrent();
        long size = query.getSize();
        int fromIndex = (int) Math.min(total, Math.max(0, (current - 1) * size));
        int toIndex = (int) Math.min(total, fromIndex + size);
        List<KnowledgeMasteryItemVO> records = items.subList(fromIndex, toIndex);
        PageVO<KnowledgeMasteryItemVO> page = PageVO.<KnowledgeMasteryItemVO>builder()
                .current(current)
                .size(size)
                .total(total)
                .pages(total == 0 ? 0L : (long) Math.ceil((double) total / size))
                .records(records)
                .build();

        int totalAttempts = items.stream().mapToInt(KnowledgeMasteryItemVO::getAttemptCount).sum();
        int wrongAttempts = items.stream().mapToInt(KnowledgeMasteryItemVO::getWrongCount).sum();
        int averageMastery = items.isEmpty()
                ? 0
                : (int) Math.round(items.stream().mapToInt(KnowledgeMasteryItemVO::getMasteryPercent).average().orElse(0D));

        return KnowledgeMasteryOverviewVO.builder()
                .totalKnowledgePoints(items.size())
                .totalAttempts(totalAttempts)
                .wrongAttempts(wrongAttempts)
                .masteredCount(countByLevel(items, "MASTERED"))
                .goodCount(countByLevel(items, "GOOD"))
                .weakCount(countByLevel(items, "WEAK"))
                .riskCount(countByLevel(items, "RISK"))
                .averageMasteryPercent(averageMastery)
                .weakestPoints(items.stream().limit(5).toList())
                .page(page)
                .build();
    }

    /**
     * 统计指定掌握等级的知识点数量。
     *
     * @param items 知识点列表
     * @param level 掌握等级
     * @return 数量
     */
    private int countByLevel(List<KnowledgeMasteryItemVO> items, String level) {
        return (int) items.stream()
                .filter(item -> level.equalsIgnoreCase(item.getMasteryLevel()))
                .count();
    }

    /**
     * 判断知识点是否匹配题型筛选。
     *
     * @param item 知识点掌握度对象
     * @param questionType 题型筛选条件
     * @return true 表示匹配
     */
    private boolean matchesQuestionType(KnowledgeMasteryItemVO item, String questionType) {
        return !StringUtils.hasText(questionType)
                || item.getQuestionTypes() != null
                && item.getQuestionTypes().toUpperCase(Locale.ROOT).contains(questionType.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 判断知识点是否匹配掌握等级筛选。
     *
     * @param item 知识点掌握度对象
     * @param level 掌握等级筛选条件
     * @return true 表示匹配
     */
    private boolean matchesLevel(KnowledgeMasteryItemVO item, String level) {
        return !StringUtils.hasText(level)
                || level.trim().equalsIgnoreCase(item.getMasteryLevel());
    }

    /**
     * 判断知识点是否匹配关键词筛选。
     *
     * @param item 知识点掌握度对象
     * @param keyword 关键词
     * @return true 表示匹配
     */
    private boolean matchesKeyword(KnowledgeMasteryItemVO item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(item.getKnowledgePoint(), normalizedKeyword)
                || containsIgnoreCase(item.getMaterialTitle(), normalizedKeyword)
                || containsIgnoreCase(item.getSuggestion(), normalizedKeyword);
    }

    /**
     * 忽略大小写判断文本是否包含关键词。
     *
     * @param source 原始文本
     * @param normalizedKeyword 已转小写的关键词
     * @return true 表示包含
     */
    private boolean containsIgnoreCase(String source, String normalizedKeyword) {
        return StringUtils.hasText(source) && source.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    /**
     * 创建空的知识掌握度总览。
     *
     * @param query 查询条件
     * @return 空总览对象
     */
    private KnowledgeMasteryOverviewVO emptyOverview(KnowledgeMasteryQuery query) {
        PageVO<KnowledgeMasteryItemVO> page = PageVO.<KnowledgeMasteryItemVO>builder()
                .current(query.getCurrent())
                .size(query.getSize())
                .total(0L)
                .pages(0L)
                .records(List.of())
                .build();
        return KnowledgeMasteryOverviewVO.builder()
                .totalKnowledgePoints(0)
                .totalAttempts(0)
                .wrongAttempts(0)
                .masteredCount(0)
                .goodCount(0)
                .weakCount(0)
                .riskCount(0)
                .averageMasteryPercent(0)
                .weakestPoints(List.of())
                .page(page)
                .build();
    }

    /**
     * null 转为空字符串，方便做忽略大小写比较。
     *
     * @param value 原始字符串
     * @return 非 null 字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 知识点聚合器。
     *
     * <p>先把同一资料下同一知识点的作答数据累加起来，
     * 最后再转换为 KnowledgeMasteryItemVO。</p>
     */
    private static final class KnowledgeAccumulator {

        private final String knowledgePoint;
        private final Long materialId;
        private final String materialTitle;
        private final Set<Long> questionIds = new HashSet<>();
        private final Set<String> questionTypes = new HashSet<>();
        private int attemptCount;
        private int correctCount;
        private int wrongCount;
        private int totalScore;
        private int obtainedScore;
        private LocalDateTime lastPracticeTime;

        /**
         * 创建一个知识点聚合器。
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
         * 累加一道题的一次作答。
         *
         * @param answer 练习答案
         * @param question 题目
         * @param session 练习会话
         */
        private void add(PracticeAnswer answer, QuestionItem question, PracticeSession session) {
            attemptCount++;
            if (question.getId() != null) {
                questionIds.add(question.getId());
            }
            if (StringUtils.hasText(question.getQuestionType())) {
                questionTypes.add(question.getQuestionType());
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
            LocalDateTime candidateTime = answer.getAnswerTime() != null ? answer.getAnswerTime() : session.getSubmitTime();
            if (candidateTime != null && (lastPracticeTime == null || candidateTime.isAfter(lastPracticeTime))) {
                lastPracticeTime = candidateTime;
            }
        }

        /**
         * 转换为前端展示对象。
         *
         * @return 知识点掌握度 VO
         */
        private KnowledgeMasteryItemVO toVO() {
            BigDecimal accuracyRate = percent(correctCount, attemptCount);
            BigDecimal scoreRate = percent(obtainedScore, totalScore);
            int masteryPercent = totalScore > 0
                    ? scoreRate.setScale(0, RoundingMode.HALF_UP).intValue()
                    : accuracyRate.setScale(0, RoundingMode.HALF_UP).intValue();
            MasteryLevel level = resolveLevel(masteryPercent);
            return KnowledgeMasteryItemVO.builder()
                    .knowledgePoint(knowledgePoint)
                    .materialId(materialId)
                    .materialTitle(materialTitle)
                    .attemptCount(attemptCount)
                    .uniqueQuestionCount(questionIds.size())
                    .correctCount(correctCount)
                    .wrongCount(wrongCount)
                    .totalScore(totalScore)
                    .obtainedScore(obtainedScore)
                    .accuracyRate(accuracyRate)
                    .scoreRate(scoreRate)
                    .masteryPercent(masteryPercent)
                    .masteryLevel(level.name())
                    .masteryLabel(level.label)
                    .suggestion(suggestion(level, attemptCount, wrongCount))
                    .questionTypes(questionTypes.stream().sorted().collect(Collectors.joining(",")))
                    .lastPracticeTime(lastPracticeTime)
                    .build();
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
         * 根据掌握度百分比判断掌握等级。
         *
         * @param masteryPercent 掌握度百分比
         * @return 掌握等级
         */
        private static MasteryLevel resolveLevel(int masteryPercent) {
            if (masteryPercent >= 85) {
                return MasteryLevel.MASTERED;
            }
            if (masteryPercent >= 70) {
                return MasteryLevel.GOOD;
            }
            if (masteryPercent >= 50) {
                return MasteryLevel.WEAK;
            }
            return MasteryLevel.RISK;
        }

        /**
         * 生成学习建议。
         *
         * @param level 掌握等级
         * @param attemptCount 作答次数
         * @param wrongCount 错误次数
         * @return 中文学习建议
         */
        private static String suggestion(MasteryLevel level, int attemptCount, int wrongCount) {
            if (attemptCount < 2) {
                return "样本还偏少，建议再做几道同知识点题目，让掌握度更稳定。";
            }
            return switch (level) {
                case MASTERED -> "掌握度较高，可以提高题目难度或进入下一知识点。";
                case GOOD -> "基本掌握，建议间隔复习一次，避免后续遗忘。";
                case WEAK -> wrongCount > 0
                        ? "建议先查看错题解析，再生成同类题巩固。"
                        : "建议补做几道同类题，确认是否真的掌握。";
                case RISK -> "这是当前薄弱点，建议回看资料核心段落，并重新生成针对性练习。";
            };
        }
    }

    /**
     * 知识点掌握等级。
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
    }
}
