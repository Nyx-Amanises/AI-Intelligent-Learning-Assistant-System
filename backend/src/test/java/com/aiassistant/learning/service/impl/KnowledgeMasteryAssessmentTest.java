package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import com.aiassistant.learning.dto.mastery.KnowledgeMasteryQuery;
import com.aiassistant.learning.entity.PracticeAnswer;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.analytics.LearningAnalyticsOverviewVO;
import com.aiassistant.learning.vo.analytics.MasteryDistributionVO;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryItemVO;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryOverviewVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KnowledgeMasteryAssessmentTest {
    private final PracticeSessionMapper sessions = mock(PracticeSessionMapper.class);
    private final PracticeAnswerMapper answers = mock(PracticeAnswerMapper.class);
    private final QuestionItemMapper questions = mock(QuestionItemMapper.class);
    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final KnowledgeMasteryServiceImpl mastery = new KnowledgeMasteryServiceImpl(answers, sessions, questions, materials);
    private final LearningAnalyticsServiceImpl analytics = new LearningAnalyticsServiceImpl(sessions, answers, questions, materials);
    private final List<PracticeSession> storedSessions = new ArrayList<>();
    private final List<PracticeAnswer> storedAnswers = new ArrayList<>();
    private final List<QuestionItem> storedQuestions = new ArrayList<>();
    private long nextId = 1;

    @BeforeAll
    static void metadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "mastery-assessment-test");
        TableInfoHelper.initTableInfo(assistant, PracticeSession.class);
        TableInfoHelper.initTableInfo(assistant, PracticeAnswer.class);
        TableInfoHelper.initTableInfo(assistant, QuestionItem.class);
    }

    @ParameterizedTest
    @CsvSource({"1,0", "1,100", "2,0", "2,100"})
    void oneOrTwoDifferentQuestionsRemainUnassessedRegardlessOfScore(int count, int score) {
        addPoint("算法", 42L, count, score);
        install();

        var result = both();
        KnowledgeMasteryItemVO item = result.mastery().getPage().getRecords().get(0);
        assertEquals("INSUFFICIENT", item.getMasteryLevel());
        assertEquals("待评估", item.getMasteryLabel());
        assertFalse(item.getAssessmentEligible());
        assertEquals(count, item.getUniqueQuestionCount());
        assertEquals(3, item.getRequiredQuestionCount());
        assertEquals(score, item.getMasteryPercent());
        assertEquals(count, item.getAttemptCount());
        assertEquals(score == 0 ? count : 0, item.getWrongCount());
        assertEquals(count * score, item.getObtainedScore());
        assertTrue(item.getAssessmentReason().contains("重复同题不增加样本量"));
        assertEquals(1, result.mastery().getInsufficientCount());
        assertEquals(0, result.mastery().getAssessedKnowledgePointCount());
        assertNull(result.mastery().getAverageMasteryPercent());
        assertTrue(result.mastery().getWeakestPoints().isEmpty());
        assertTrue(result.analytics().getWeakKnowledgePoints().isEmpty());
        assertEquals(1, result.analytics().getInsufficientKnowledgePointCount());
        assertEquals(0, result.analytics().getWeakKnowledgePointCount());
        assertDistribution(result.analytics(), Map.of("INSUFFICIENT", 1));
    }

    @Test
    void repeatingTheSameQuestionTenTimesDoesNotCreateTenSamples() {
        addPoint("算法", 42L, 1, 0);
        PracticeAnswer original = storedAnswers.get(0);
        for (int index = 1; index < 10; index++) addAnswer(original.getSessionId(), original.getQuestionId(), 0, 0, "RULE");
        install();

        var result = both();
        var item = result.mastery().getPage().getRecords().get(0);
        assertEquals(10, item.getAttemptCount());
        assertEquals(10, item.getWrongCount());
        assertEquals(1, item.getUniqueQuestionCount());
        assertEquals("INSUFFICIENT", item.getMasteryLevel());
        assertEquals(10, result.analytics().getTotalQuestionAttempts());
        assertEquals(0, result.analytics().getWeakKnowledgePointCount());
    }

    @ParameterizedTest
    @CsvSource({"49,RISK", "50,WEAK", "69,WEAK", "70,GOOD", "84,GOOD", "85,MASTERED"})
    void threeDifferentGradedQuestionsUseTheSameLevelBoundariesInBothViews(int score, String level) {
        addPoint("算法", 42L, 3, score);
        install();

        var result = both();
        var item = result.mastery().getPage().getRecords().get(0);
        assertEquals(level, item.getMasteryLevel());
        assertTrue(item.getAssessmentEligible());
        assertEquals(3, item.getUniqueQuestionCount());
        assertEquals(score, item.getMasteryPercent());
        assertEquals(score, result.mastery().getAverageMasteryPercent());
        assertEquals(1, result.mastery().getAssessedKnowledgePointCount());
        assertEquals(0, result.mastery().getInsufficientCount());
        assertDistribution(result.analytics(), Map.of(level, 1));
        if (score < 70) {
            assertEquals(1, result.mastery().getWeakestPoints().size());
            var weak = result.analytics().getWeakKnowledgePoints().get(0);
            assertEquals(3, weak.getUniqueQuestionCount());
            assertEquals(3, weak.getRequiredQuestionCount());
            assertEquals(level, weak.getMasteryLevel());
            assertEquals(item.getMasteryLabel(), weak.getMasteryLabel());
            assertEquals(item.getAssessmentReason(), weak.getAssessmentReason());
            assertTrue(weak.getAssessmentEligible());
            assertEquals(1, result.analytics().getWeakKnowledgePointCount());
        } else {
            assertTrue(result.mastery().getWeakestPoints().isEmpty());
            assertTrue(result.analytics().getWeakKnowledgePoints().isEmpty());
            assertEquals(0, result.analytics().getWeakKnowledgePointCount());
        }
    }

    @Test
    void unlabelledMixedQuestionsNeverFormARealKnowledgePointAssessment() {
        addPoint(null, 42L, 4, 0);
        install();

        var result = both();
        var item = result.mastery().getPage().getRecords().get(0);
        assertEquals("未标注知识点", item.getKnowledgePoint());
        assertEquals(4, item.getUniqueQuestionCount());
        assertFalse(item.getAssessmentEligible());
        assertEquals("INSUFFICIENT", item.getMasteryLevel());
        assertTrue(item.getAssessmentReason().contains("先补充标注"));
        assertTrue(result.analytics().getWeakKnowledgePoints().isEmpty());
        assertNull(result.mastery().getAverageMasteryPercent());
    }

    @Test
    void averageUsesOnlyEligiblePointsAndFiveWayDistributionStillAddsUp() {
        addPoint("少样本", 42L, 1, 0);
        addPoint("算法", 42L, 3, 50);
        addPoint("图论", 42L, 3, 90);
        install();

        var result = both();
        assertEquals(70, result.mastery().getAverageMasteryPercent());
        assertEquals(2, result.mastery().getAssessedKnowledgePointCount());
        assertEquals(1, result.mastery().getInsufficientCount());
        assertEquals(7, result.mastery().getTotalAttempts());
        assertEquals(1, result.mastery().getWeakCount());
        assertEquals(0, result.mastery().getRiskCount());
        assertEquals(1, result.mastery().getMasteredCount());
        assertDistribution(result.analytics(), Map.of("INSUFFICIENT", 1, "WEAK", 1, "MASTERED", 1));
    }

    @Test
    void weakListsFilterInsufficientAndGoodPointsBeforeApplyingLimits() {
        for (int index = 0; index < 12; index++) addPoint("待评估" + index, 42L, 1, 0);
        for (int index = 0; index < 10; index++) addPoint("可评估" + index, 42L, 3, 50 + index);
        addPoint("已掌握", 42L, 3, 100);
        install();

        var result = both();
        assertEquals(5, result.mastery().getWeakestPoints().size());
        assertEquals(8, result.analytics().getWeakKnowledgePoints().size());
        assertEquals(List.of(50, 51, 52, 53, 54), result.mastery().getWeakestPoints().stream().map(item -> item.getMasteryPercent()).toList());
        assertEquals(List.of(50, 51, 52, 53, 54, 55, 56, 57), result.analytics().getWeakKnowledgePoints().stream().map(item -> item.getMasteryPercent()).toList());
        assertEquals(10, result.analytics().getWeakKnowledgePointCount());
        assertEquals(12, result.analytics().getInsufficientKnowledgePointCount());
    }

    @Test
    void sameLabelAcrossMaterialsDoesNotPoolSampleCounts() {
        addPoint("算法", 42L, 2, 0);
        addPoint("算法", 43L, 1, 0);
        install();

        var result = both();
        assertEquals(2, result.mastery().getTotalKnowledgePoints());
        assertEquals(2, result.mastery().getInsufficientCount());
        assertEquals(0, result.mastery().getAssessedKnowledgePointCount());
        assertEquals(2, result.analytics().getInsufficientKnowledgePointCount());
        assertTrue(result.analytics().getWeakKnowledgePoints().isEmpty());
    }

    @Test
    void insufficientAndMaterialFiltersKeepTheAssessmentDenominatorHonest() {
        addPoint("少样本", 42L, 1, 0);
        addPoint("算法", 42L, 3, 50);
        addPoint("其他资料", 43L, 3, 0);
        install();
        KnowledgeMasteryQuery query = new KnowledgeMasteryQuery();
        query.setMaterialId(42L);
        query.setMasteryLevel("INSUFFICIENT");

        var result = mastery.overview(7L, query);

        assertEquals(1L, result.getPage().getTotal());
        assertEquals("少样本", result.getPage().getRecords().get(0).getKnowledgePoint());
        assertEquals(1, result.getInsufficientCount());
        assertEquals(0, result.getAssessedKnowledgePointCount());
        assertNull(result.getAverageMasteryPercent());
        assertTrue(result.getWeakestPoints().isEmpty());
        LearningAnalyticsQuery analyticsQuery = new LearningAnalyticsQuery();
        analyticsQuery.setMaterialId(42L);
        var scopedAnalytics = analytics.overview(7L, analyticsQuery);
        assertEquals(2, scopedAnalytics.getTotalKnowledgePoints());
        assertEquals(1, scopedAnalytics.getInsufficientKnowledgePointCount());
        assertEquals(1, scopedAnalytics.getWeakKnowledgePointCount());
    }

    @Test
    void pendingAndUngradedHistoricalAnswersCannotSupplyTheThirdQuestion() {
        addPoint("算法", 42L, 4, 0);
        storedAnswers.get(2).setReviewMode("AI_PENDING");
        storedAnswers.get(3).setIsCorrect(null);
        install();

        var result = both();
        var item = result.mastery().getPage().getRecords().get(0);
        assertEquals(2, item.getUniqueQuestionCount());
        assertEquals(3, item.getAttemptCount(), "排除pending但保留历史作答统计");
        assertEquals("INSUFFICIENT", item.getMasteryLevel());
        assertEquals(3, result.analytics().getTotalQuestionAttempts());
        assertEquals(1, result.analytics().getInsufficientKnowledgePointCount());
    }

    @Test
    void zeroScoreFallbackUsesCorrectAnswersEvenWhenManuallyMarkedWrong() {
        addPoint("算法", 42L, 3, 100);
        storedQuestions.forEach(question -> question.setScore(0));
        storedAnswers.forEach(answer -> { answer.setObtainedScore(0); answer.setMarkedWrong(1); });
        install();

        var result = both();
        var item = result.mastery().getPage().getRecords().get(0);
        assertEquals(100, item.getMasteryPercent());
        assertEquals(3, item.getCorrectCount());
        assertEquals(3, item.getWrongCount());
        assertEquals("MASTERED", item.getMasteryLevel());
        assertEquals(new BigDecimal("100.0"), item.getScoreRate());
        assertDistribution(result.analytics(), Map.of("MASTERED", 1));
        assertTrue(result.analytics().getWeakKnowledgePoints().isEmpty());
    }

    @Test
    void historicalUngradedAnswersCannotDiluteAnEligiblePointsScore() {
        addPoint("算法", 42L, 10, 100);
        for (int index = 3; index < 10; index++) {
            storedAnswers.get(index).setIsCorrect(null);
            storedAnswers.get(index).setObtainedScore(0);
        }
        addAnswer(storedAnswers.get(0).getSessionId(), storedAnswers.get(0).getQuestionId(), 0, null, "RULE");
        install();

        var result = both();
        var item = result.mastery().getPage().getRecords().get(0);
        assertEquals(11, item.getAttemptCount(), "历史记录仍参与原始作答次数");
        assertEquals(3, item.getUniqueQuestionCount());
        assertEquals(1100, item.getTotalScore(), "原始分值总计保持可追溯");
        assertEquals(300, item.getObtainedScore());
        assertEquals(100, item.getMasteryPercent());
        assertEquals(new BigDecimal("100.0"), item.getScoreRate());
        assertEquals("MASTERED", item.getMasteryLevel());
        assertEquals(100, result.mastery().getAverageMasteryPercent());
        assertDistribution(result.analytics(), Map.of("MASTERED", 1));
        assertTrue(result.analytics().getWeakKnowledgePoints().isEmpty());
    }

    @Test
    void zeroScoreFallbackShowsTheSameRateForWeakItemsInBothViews() {
        addPoint("算法", 42L, 3, 100);
        storedQuestions.forEach(question -> question.setScore(0));
        storedAnswers.forEach(answer -> { answer.setObtainedScore(0); answer.setMarkedWrong(1); });
        storedAnswers.get(1).setIsCorrect(0);
        storedAnswers.get(2).setIsCorrect(0);
        install();

        var result = both();
        var item = result.mastery().getPage().getRecords().get(0);
        var weak = result.analytics().getWeakKnowledgePoints().get(0);
        assertEquals(33, item.getMasteryPercent());
        assertEquals(new BigDecimal("33.3"), item.getScoreRate());
        assertEquals(item.getScoreRate(), weak.getScoreRate());
        assertEquals(item.getMasteryPercent(), weak.getMasteryPercent());
        assertEquals("RISK", weak.getMasteryLevel());
    }

    @Test
    void invalidHistoricalVerdictsDoNotCountAsGradedSamples() {
        addPoint("算法", 42L, 3, 100);
        storedAnswers.get(2).setIsCorrect(2);
        install();

        var result = both();
        assertEquals(2, result.mastery().getPage().getRecords().get(0).getUniqueQuestionCount());
        assertEquals("INSUFFICIENT", result.mastery().getPage().getRecords().get(0).getMasteryLevel());
        assertEquals(1, result.analytics().getInsufficientKnowledgePointCount());
    }

    @Test
    void emptyViewsHaveNoAverageAndAllFiveDistributionClassesAtZero() {
        install();
        var result = both();
        assertNull(result.mastery().getAverageMasteryPercent());
        assertEquals(0, result.mastery().getAssessedKnowledgePointCount());
        assertEquals(0, result.mastery().getInsufficientCount());
        assertDistribution(result.analytics(), Map.of());
    }

    private void addPoint(String point, long materialId, int count, int score) {
        PracticeSession session = new PracticeSession();
        session.setId(nextId++);
        session.setUserId(7L);
        session.setMaterialId(materialId);
        session.setSessionStatus("SUBMITTED");
        session.setSubmitTime(LocalDateTime.now().minusDays(1));
        storedSessions.add(session);
        for (int index = 0; index < count; index++) {
            QuestionItem question = new QuestionItem();
            question.setId(nextId++);
            question.setQuestionType("SINGLE");
            question.setKnowledgePoint(point);
            question.setScore(100);
            storedQuestions.add(question);
            addAnswer(session.getId(), question.getId(), score, score >= 60 ? 1 : 0, "RULE");
        }
    }

    private void addAnswer(long sessionId, long questionId, int score, Integer correct, String mode) {
        PracticeAnswer answer = new PracticeAnswer();
        answer.setId(nextId++);
        answer.setSessionId(sessionId);
        answer.setQuestionId(questionId);
        answer.setObtainedScore(score);
        answer.setIsCorrect(correct);
        answer.setMarkedWrong(correct != null && correct == 0 ? 1 : 0);
        answer.setReviewMode(mode);
        storedAnswers.add(answer);
    }

    private void install() {
        when(sessions.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<PracticeSession> wrapper = invocation.getArgument(0);
            assertEquals(7L, equality(wrapper, "user_id"));
            assertEquals("SUBMITTED", equality(wrapper, "session_status"));
            Object materialId = equality(wrapper, "material_id");
            return storedSessions.stream().filter(session -> materialId == null || materialId.equals(session.getMaterialId())).toList();
        });
        when(answers.selectList(any())).thenReturn(storedAnswers);
        when(questions.selectList(any())).thenReturn(storedQuestions);
    }

    private Object equality(LambdaQueryWrapper<?> wrapper, String field) {
        Matcher matcher = Pattern.compile("\\b" + field + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}").matcher(wrapper.getSqlSegment());
        return matcher.find() ? wrapper.getParamNameValuePairs().get(matcher.group(1)) : null;
    }

    private Results both() {
        KnowledgeMasteryQuery query = new KnowledgeMasteryQuery();
        query.setSize(50L);
        return new Results(mastery.overview(7L, query), analytics.overview(7L, new LearningAnalyticsQuery()));
    }

    private void assertDistribution(LearningAnalyticsOverviewVO overview, Map<String, Integer> expected) {
        Map<String, Integer> counts = overview.getMasteryDistribution().stream()
                .collect(Collectors.toMap(MasteryDistributionVO::getLevel, MasteryDistributionVO::getCount));
        assertEquals(5, counts.size());
        for (String level : List.of("MASTERED", "GOOD", "WEAK", "RISK", "INSUFFICIENT")) {
            assertEquals(expected.getOrDefault(level, 0), counts.get(level), level);
        }
        assertEquals(overview.getTotalKnowledgePoints(), counts.values().stream().mapToInt(Integer::intValue).sum());
    }

    private record Results(KnowledgeMasteryOverviewVO mastery, LearningAnalyticsOverviewVO analytics) { }
}
