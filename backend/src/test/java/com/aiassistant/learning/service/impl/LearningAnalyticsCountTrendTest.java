package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import com.aiassistant.learning.entity.PracticeAnswer;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.analytics.LearningAnalyticsOverviewVO;
import com.aiassistant.learning.vo.analytics.PracticeCountPointVO;
import com.aiassistant.learning.vo.analytics.PracticeTrendPointVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LearningAnalyticsCountTrendTest {
    private final PracticeSessionMapper sessions = mock(PracticeSessionMapper.class);
    private final PracticeAnswerMapper answers = mock(PracticeAnswerMapper.class);
    private final QuestionItemMapper questions = mock(QuestionItemMapper.class);
    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final LearningAnalyticsServiceImpl service = new LearningAnalyticsServiceImpl(sessions, answers, questions, materials);

    @BeforeAll
    static void metadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "analytics-count-test");
        TableInfoHelper.initTableInfo(assistant, PracticeSession.class);
        TableInfoHelper.initTableInfo(assistant, PracticeAnswer.class);
        TableInfoHelper.initTableInfo(assistant, QuestionItem.class);
    }

    @Test
    void pendingOnlySessionsStillContributePracticeCountsAndStudySeconds() {
        LocalDate day = LocalDate.now().minusDays(2);
        configure(List.of(session(1, day.atTime(9, 0), 90), session(2, day.atTime(10, 0), 120)),
                List.of(answer(1, 1, "AI_PENDING"), answer(2, 2, "ai_pending")));

        LearningAnalyticsOverviewVO overview = service.overview(7L, new LearningAnalyticsQuery());

        assertEquals(2, overview.getTotalPracticeCount());
        assertEquals(210L, overview.getTotalStudySeconds());
        assertEquals(0, overview.getTotalQuestionAttempts());
        assertTrue(overview.getPracticeTrend().isEmpty());
        assertEquals(2L, overview.getPracticeCountTrend().points().get(0).count());
        assertTotalMatchesTrend(overview);
    }

    @Test
    void sessionsWithoutAnswersKeepCountsDurationAndUndatedRecords() {
        LocalDate day = LocalDate.now().minusDays(1);
        configure(List.of(session(1, day.atTime(9, 0), 60), session(2, null, 30)), List.of());

        LearningAnalyticsOverviewVO overview = service.overview(7L, new LearningAnalyticsQuery());

        assertEquals(2, overview.getTotalPracticeCount());
        assertEquals(90L, overview.getTotalStudySeconds());
        assertEquals(0, overview.getTotalQuestionAttempts());
        assertEquals(1L, overview.getPracticeCountTrend().undatedCount());
        assertEquals(1L, overview.getPracticeCountTrend().points().stream().mapToLong(PracticeCountPointVO::count).sum());
        assertTrue(overview.getPracticeTrend().isEmpty());
        assertTotalMatchesTrend(overview);
    }

    @Test
    void mixedPendingSessionsDoNotPretendToHaveFinalPerformance() {
        LocalDate day = LocalDate.now().minusDays(1);
        configure(List.of(session(1, day.atTime(9, 0), 60), session(2, day.atTime(10, 0), 60),
                        session(3, day.atTime(11, 0), 60)),
                List.of(answer(1, 1, "RULE"), answer(2, 1, "AI_PENDING"), answer(3, 2, "AI")));

        LearningAnalyticsOverviewVO overview = service.overview(7L, new LearningAnalyticsQuery());

        assertEquals(3, overview.getTotalPracticeCount());
        assertEquals(180L, overview.getTotalStudySeconds());
        assertEquals(2, overview.getTotalQuestionAttempts(), "已评分答案仍参与作答统计");
        assertEquals(List.of(2L), overview.getPracticeTrend().stream().map(PracticeTrendPointVO::getSessionId).toList());
        assertEquals(BigDecimal.valueOf(100), overview.getPracticeTrend().get(0).getAccuracyRate());
        assertTotalMatchesTrend(overview);
    }

    @Test
    void moreThanThirtySessionsAllCountWhileOnlyPerformanceIsLimited() {
        LocalDateTime start = LocalDate.now().minusDays(1).atStartOfDay();
        List<PracticeSession> storedSessions = new ArrayList<>();
        List<PracticeAnswer> storedAnswers = new ArrayList<>();
        for (int index = 1; index <= 40; index++) {
            storedSessions.add(session(index, start.plusMinutes(index), 10));
            storedAnswers.add(answer(index, index, "RULE"));
        }
        configure(storedSessions, storedAnswers);
        LearningAnalyticsQuery query = new LearningAnalyticsQuery();
        query.setTrendLimit(30);

        LearningAnalyticsOverviewVO overview = service.overview(7L, query);

        assertEquals(40, overview.getTotalPracticeCount());
        assertEquals(400L, overview.getTotalStudySeconds());
        assertEquals(40, overview.getTotalQuestionAttempts());
        assertEquals(30, overview.getPracticeTrend().size());
        assertEquals(11L, overview.getPracticeTrend().get(0).getSessionId());
        assertEquals(40L, overview.getPracticeTrend().get(29).getSessionId());
        assertEquals(40L, overview.getPracticeCountTrend().points().stream().mapToLong(PracticeCountPointVO::count).sum());
        assertTotalMatchesTrend(overview);
    }

    @Test
    void finalPerformanceFilterRunsBeforeRecentPointLimit() {
        LocalDateTime start = LocalDate.now().minusDays(1).atStartOfDay();
        List<PracticeSession> storedSessions = new ArrayList<>();
        List<PracticeAnswer> storedAnswers = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            storedSessions.add(session(index, start.plusMinutes(index), 10));
            storedAnswers.add(answer(index, index, index <= 7 ? "RULE" : "AI_PENDING"));
        }
        configure(storedSessions, storedAnswers);
        LearningAnalyticsQuery query = new LearningAnalyticsQuery();
        query.setTrendLimit(5);

        LearningAnalyticsOverviewVO overview = service.overview(7L, query);

        assertEquals(List.of(3L, 4L, 5L, 6L, 7L),
                overview.getPracticeTrend().stream().map(PracticeTrendPointVO::getSessionId).toList());
        assertEquals(12, overview.getTotalPracticeCount());
        assertTotalMatchesTrend(overview);
    }

    @Test
    void emptyCustomRangeContainsAllZeroDaysInsteadOfAnEmptyChart() {
        when(sessions.selectList(any())).thenReturn(List.of());
        LearningAnalyticsQuery query = new LearningAnalyticsQuery();
        query.setStartDate(LocalDate.of(2024, 2, 28));
        query.setEndDate(LocalDate.of(2024, 3, 1));

        LearningAnalyticsOverviewVO overview = service.overview(7L, query);

        assertEquals(0, overview.getTotalPracticeCount());
        assertEquals(0L, overview.getTotalStudySeconds());
        assertEquals("DAY", overview.getPracticeCountTrend().granularity());
        assertEquals(List.of(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 1)),
                overview.getPracticeCountTrend().points().stream().map(PracticeCountPointVO::startDate).toList());
        assertTrue(overview.getPracticeCountTrend().points().stream().allMatch(point -> point.count() == 0));
        assertTotalMatchesTrend(overview);
        verifyNoInteractions(answers, questions, materials);
    }

    private void configure(List<PracticeSession> storedSessions, List<PracticeAnswer> storedAnswers) {
        when(sessions.selectList(any())).thenReturn(storedSessions);
        when(answers.selectList(any())).thenReturn(storedAnswers);
        QuestionItem question = new QuestionItem();
        question.setId(21L);
        question.setQuestionType("SINGLE");
        question.setScore(5);
        when(questions.selectList(any())).thenReturn(List.of(question));
    }

    private PracticeSession session(long id, LocalDateTime submitted, int seconds) {
        PracticeSession session = new PracticeSession();
        session.setId(id);
        session.setUserId(7L);
        session.setSessionStatus("SUBMITTED");
        session.setSubmitTime(submitted);
        session.setDurationSeconds(seconds);
        session.setTotalQuestions(1);
        session.setCorrectCount(1);
        session.setTotalScore(5);
        session.setObtainedScore(5);
        session.setAccuracyRate(BigDecimal.valueOf(100));
        return session;
    }

    private PracticeAnswer answer(long id, long sessionId, String reviewMode) {
        PracticeAnswer answer = new PracticeAnswer();
        answer.setId(id);
        answer.setSessionId(sessionId);
        answer.setQuestionId(21L);
        answer.setReviewMode(reviewMode);
        answer.setIsCorrect(1);
        answer.setObtainedScore(5);
        return answer;
    }

    private void assertTotalMatchesTrend(LearningAnalyticsOverviewVO overview) {
        assertEquals(overview.getTotalPracticeCount().longValue(), overview.getPracticeCountTrend().undatedCount()
                + overview.getPracticeCountTrend().points().stream().mapToLong(PracticeCountPointVO::count).sum());
    }
}
