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

@Service
public class KnowledgeMasteryServiceImpl implements KnowledgeMasteryService {

    private static final String SESSION_STATUS_SUBMITTED = "SUBMITTED";
    private static final String REVIEW_MODE_AI_PENDING = "AI_PENDING";
    private static final String DEFAULT_KNOWLEDGE_POINT = "未标注知识点";

    private final PracticeAnswerMapper practiceAnswerMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final QuestionItemMapper questionItemMapper;
    private final StudyMaterialService studyMaterialService;

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

    private int countByLevel(List<KnowledgeMasteryItemVO> items, String level) {
        return (int) items.stream()
                .filter(item -> level.equalsIgnoreCase(item.getMasteryLevel()))
                .count();
    }

    private boolean matchesQuestionType(KnowledgeMasteryItemVO item, String questionType) {
        return !StringUtils.hasText(questionType)
                || item.getQuestionTypes() != null
                && item.getQuestionTypes().toUpperCase(Locale.ROOT).contains(questionType.trim().toUpperCase(Locale.ROOT));
    }

    private boolean matchesLevel(KnowledgeMasteryItemVO item, String level) {
        return !StringUtils.hasText(level)
                || level.trim().equalsIgnoreCase(item.getMasteryLevel());
    }

    private boolean matchesKeyword(KnowledgeMasteryItemVO item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(item.getKnowledgePoint(), normalizedKeyword)
                || containsIgnoreCase(item.getMaterialTitle(), normalizedKeyword)
                || containsIgnoreCase(item.getSuggestion(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String source, String normalizedKeyword) {
        return StringUtils.hasText(source) && source.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

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

        private KnowledgeAccumulator(String knowledgePoint, Long materialId, String materialTitle) {
            this.knowledgePoint = knowledgePoint;
            this.materialId = materialId;
            this.materialTitle = materialTitle;
        }

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
