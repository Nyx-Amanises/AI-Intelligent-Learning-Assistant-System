package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.practice.PracticeStartRequest;
import com.aiassistant.learning.entity.PracticeAnswer;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class WrongQuestionServiceImplTest {
    private static final long USER_ID = 7L;
    private final PracticeAnswerMapper answers = mock(PracticeAnswerMapper.class);
    private final PracticeSessionMapper sessions = mock(PracticeSessionMapper.class);
    private final QuestionItemMapper questions = mock(QuestionItemMapper.class);
    private final QuestionSetMapper questionSets = mock(QuestionSetMapper.class);
    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final PracticeService practice = mock(PracticeService.class);
    private final WrongQuestionServiceImpl service = new WrongQuestionServiceImpl(
            answers, sessions, questions, questionSets, materials, practice);

    @BeforeAll
    static void initializeMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "wrong-question-test");
        TableInfoHelper.initTableInfo(assistant, PracticeSession.class);
        TableInfoHelper.initTableInfo(assistant, PracticeAnswer.class);
        TableInfoHelper.initTableInfo(assistant, QuestionItem.class);
        TableInfoHelper.initTableInfo(assistant, QuestionSet.class);
    }

    @Test
    void redoCreatesOnlyTheSelectedQuestionAndPreservesOriginalAnswer() {
        PracticeAnswer original = wrongAnswer();
        PracticeSession session = submittedSession();
        when(answers.selectById(11L)).thenReturn(original);
        when(sessions.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<PracticeSession> wrapper = invocation.getArgument(0);
            assertTrue(wrapper.getSqlSegment().contains("user_id"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(USER_ID));
            return session;
        });
        PracticeDetailVO newPractice = PracticeDetailVO.builder().sessionId(501L).build();
        when(practice.startPractice(eq(USER_ID), any())).thenReturn(newPractice);

        assertSame(newPractice, service.redoWrongQuestion(USER_ID, 11L));

        ArgumentCaptor<PracticeStartRequest> request = ArgumentCaptor.forClass(PracticeStartRequest.class);
        verify(practice).startPractice(eq(USER_ID), request.capture());
        assertEquals(91L, request.getValue().getQuestionSetId());
        assertEquals(List.of(21L), request.getValue().getQuestionIds());
        assertEquals(1, original.getMarkedWrong());
        assertEquals("B", original.getUserAnswer());
        verify(answers, never()).updateById(any(PracticeAnswer.class));
        verify(sessions, never()).updateById(any(PracticeSession.class));
    }

    @Test
    void redoRejectsMissingAnswer() {
        assertThrows(BusinessException.class, () -> service.redoWrongQuestion(USER_ID, 11L));
        verifyNoInteractions(sessions, practice);
    }

    @Test
    void redoRejectsAnswerRemovedFromWrongBook() {
        PracticeAnswer original = wrongAnswer();
        original.setMarkedWrong(0);
        when(answers.selectById(11L)).thenReturn(original);
        assertThrows(BusinessException.class, () -> service.redoWrongQuestion(USER_ID, 11L));
        verifyNoInteractions(sessions, practice);
    }

    @Test
    void redoRejectsAnotherUsersSession() {
        when(answers.selectById(11L)).thenReturn(wrongAnswer());
        when(sessions.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.redoWrongQuestion(USER_ID, 11L));
        verifyNoInteractions(practice);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "CANCELLED"})
    void redoRejectsUnsubmittedSessions(String status) {
        PracticeSession session = submittedSession();
        session.setSessionStatus(status);
        when(answers.selectById(11L)).thenReturn(wrongAnswer());
        when(sessions.selectOne(any())).thenReturn(session);
        assertThrows(BusinessException.class, () -> service.redoWrongQuestion(USER_ID, 11L));
        verifyNoInteractions(practice);
    }

    @Test
    void redoPropagatesUnavailableQuestionSetWithoutChangingOriginalAnswer() {
        PracticeAnswer original = wrongAnswer();
        when(answers.selectById(11L)).thenReturn(original);
        when(sessions.selectOne(any())).thenReturn(submittedSession());
        when(practice.startPractice(eq(USER_ID), any())).thenThrow(new BusinessException("当前题集不可练习"));
        assertThrows(BusinessException.class, () -> service.redoWrongQuestion(USER_ID, 11L));
        assertEquals(1, original.getMarkedWrong());
        verify(answers, never()).updateById(any(PracticeAnswer.class));
    }

    private PracticeAnswer wrongAnswer() {
        PracticeAnswer answer = new PracticeAnswer();
        answer.setId(11L);
        answer.setSessionId(31L);
        answer.setQuestionId(21L);
        answer.setMarkedWrong(1);
        answer.setUserAnswer("B");
        return answer;
    }

    private PracticeSession submittedSession() {
        PracticeSession session = new PracticeSession();
        session.setId(31L);
        session.setUserId(USER_ID);
        session.setQuestionSetId(91L);
        session.setMaterialId(41L);
        session.setSessionStatus("SUBMITTED");
        return session;
    }
}
