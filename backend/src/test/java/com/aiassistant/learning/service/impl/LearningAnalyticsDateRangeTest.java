package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import com.aiassistant.learning.entity.PracticeAnswer;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.service.StudyMaterialService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;

class LearningAnalyticsDateRangeTest {
    private final PracticeSessionMapper sessions = mock(PracticeSessionMapper.class);
    private final PracticeAnswerMapper answers = mock(PracticeAnswerMapper.class);
    private final QuestionItemMapper questions = mock(QuestionItemMapper.class);
    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final LearningAnalyticsServiceImpl service = new LearningAnalyticsServiceImpl(sessions, answers, questions, materials);

    @BeforeAll
    static void metadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "analytics-date-test");
        TableInfoHelper.initTableInfo(assistant, PracticeSession.class);
        TableInfoHelper.initTableInfo(assistant, PracticeAnswer.class);
        TableInfoHelper.initTableInfo(assistant, QuestionItem.class);
    }

    @Test
    void dateBoundsIncludeEndDaySecondsAndCombineWithUserStatusAndMaterial() {
        LocalDate day = LocalDate.of(2024, 2, 29);
        List<PracticeSession> data = List.of(
                session(1, 7, 42, "SUBMITTED", day.atStartOfDay()),
                session(2, 7, 42, "SUBMITTED", day.atTime(23, 59, 59, 999999999)),
                session(3, 7, 42, "SUBMITTED", day.minusDays(1).atTime(23, 59, 59)),
                session(4, 7, 42, "SUBMITTED", day.plusDays(1).atStartOfDay()),
                session(5, 99, 42, "SUBMITTED", day.atTime(12, 0)),
                session(6, 7, 42, "IN_PROGRESS", day.atTime(12, 0)),
                session(7, 7, 99, "SUBMITTED", day.atTime(12, 0)));
        when(sessions.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<PracticeSession> wrapper = invocation.getArgument(0);
            Object user = boundValue(wrapper, "user_id", "=");
            Object status = boundValue(wrapper, "session_status", "=");
            Object material = boundValue(wrapper, "material_id", "=");
            LocalDateTime start = (LocalDateTime) boundValue(wrapper, "submit_time", ">=");
            LocalDateTime end = (LocalDateTime) boundValue(wrapper, "submit_time", "<");
            assertEquals(7L, user);
            assertEquals("SUBMITTED", status);
            assertEquals(42L, material);
            assertEquals(day.atStartOfDay(), start);
            assertEquals(day.plusDays(1).atStartOfDay(), end);
            return data.stream().filter(item -> item.getUserId().equals(user))
                    .filter(item -> item.getSessionStatus().equals(status))
                    .filter(item -> item.getMaterialId().equals(material))
                    .filter(item -> !item.getSubmitTime().isBefore(start) && item.getSubmitTime().isBefore(end)).toList();
        });
        when(answers.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<PracticeAnswer> wrapper = invocation.getArgument(0);
            assertTrue(wrapper.getSqlSegment().contains("session_id IN"));
            assertEquals(Set.of(1L, 2L), Set.copyOf(wrapper.getParamNameValuePairs().values()));
            return List.of(answer(1L), answer(2L));
        });
        QuestionItem question = new QuestionItem();
        question.setId(21L);
        question.setQuestionType("SINGLE");
        question.setScore(5);
        when(questions.selectList(any())).thenReturn(List.of(question));
        LearningAnalyticsQuery query = query(day, day);
        query.setMaterialId(42L);

        var overview = service.overview(7L, query);

        assertEquals(2, overview.getTotalPracticeCount());
        assertEquals(Set.of(1L, 2L), overview.getPracticeTrend().stream().map(item -> item.getSessionId()).collect(Collectors.toSet()));
    }

    @Test
    void recentDaysUseCalendarBoundsAndAllTimeOmitsBothBounds() {
        when(sessions.selectList(any())).thenReturn(List.of());
        LearningAnalyticsQuery recent = new LearningAnalyticsQuery();
        recent.setDays(7);
        LocalDate before = LocalDate.now();
        service.overview(7L, recent);
        LocalDate after = LocalDate.now();
        ArgumentCaptor<LambdaQueryWrapper<PracticeSession>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sessions).selectList(captor.capture());
        LocalDateTime start = (LocalDateTime) boundValue(captor.getValue(), "submit_time", ">=");
        LocalDateTime end = (LocalDateTime) boundValue(captor.getValue(), "submit_time", "<");
        assertTrue(start.equals(before.minusDays(6).atStartOfDay()) || start.equals(after.minusDays(6).atStartOfDay()));
        assertEquals(start.plusDays(7), end);
        assertNull(boundValue(captor.getValue(), "material_id", "="));

        service.overview(7L, new LearningAnalyticsQuery());
        verify(sessions, org.mockito.Mockito.times(2)).selectList(captor.capture());
        LambdaQueryWrapper<PracticeSession> all = captor.getValue();
        assertNull(boundValue(all, "submit_time", ">="));
        assertNull(boundValue(all, "submit_time", "<"));
        assertFalse(all.getSqlSegment().contains("submit_time >="));
    }

    @Test
    void invalidRangesAreRejectedBeforeAnyDatabaseAccess() {
        LocalDate today = LocalDate.now();
        LearningAnalyticsQuery conflict = query(today.minusDays(1), today);
        conflict.setDays(7);
        LearningAnalyticsQuery badDays = new LearningAnalyticsQuery();
        badDays.setDays(0);
        for (LearningAnalyticsQuery query : List.of(query(today, null), query(null, today),
                query(today, today.minusDays(1)), query(today, today.plusDays(2)),
                query(LocalDate.of(999, 12, 31), today), conflict, badDays)) {
            assertEquals(400, assertThrows(BusinessException.class, () -> service.overview(7L, query)).getCode());
        }
        verifyNoInteractions(sessions, answers, questions, materials);
    }

    private Object boundValue(LambdaQueryWrapper<?> wrapper, String column, String operator) {
        Matcher matcher = Pattern.compile("\\b" + column + "\\s*" + Pattern.quote(operator)
                + "\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}").matcher(wrapper.getSqlSegment());
        return matcher.find() ? wrapper.getParamNameValuePairs().get(matcher.group(1)) : null;
    }

    private LearningAnalyticsQuery query(LocalDate start, LocalDate end) {
        LearningAnalyticsQuery query = new LearningAnalyticsQuery();
        query.setStartDate(start);
        query.setEndDate(end);
        return query;
    }

    private PracticeSession session(long id, long user, long material, String status, LocalDateTime submitted) {
        PracticeSession session = new PracticeSession();
        session.setId(id);
        session.setUserId(user);
        session.setMaterialId(material);
        session.setSessionStatus(status);
        session.setSubmitTime(submitted);
        return session;
    }

    private PracticeAnswer answer(long sessionId) {
        PracticeAnswer answer = new PracticeAnswer();
        answer.setId(sessionId);
        answer.setSessionId(sessionId);
        answer.setQuestionId(21L);
        answer.setReviewMode("RULE");
        return answer;
    }
}
