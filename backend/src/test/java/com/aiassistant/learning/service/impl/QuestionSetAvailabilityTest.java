package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.practice.PracticeStartRequest;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.service.RetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuestionSetAvailabilityTest {

    @ParameterizedTest
    @ValueSource(strings = {"GENERATING", "FAILED", "CANCELLED", "DRAFT"})
    void unpublishedQuestionSetsCannotStartAPractice(String status) {
        QuestionSetMapper questionSets = mock(QuestionSetMapper.class);
        PracticeSessionMapper sessions = mock(PracticeSessionMapper.class);
        QuestionItemMapper questions = mock(QuestionItemMapper.class);
        PracticeServiceImpl service = new PracticeServiceImpl(
                sessions, mock(PracticeAnswerMapper.class), questionSets, questions, mock(AiConfigService.class),
                mock(AiChatService.class), mock(RetrievalService.class), new ObjectMapper(), mock(PracticeService.class));
        QuestionSet questionSet = new QuestionSet();
        questionSet.setId(91L);
        questionSet.setStatus(status);
        when(questionSets.selectOne(any())).thenReturn(questionSet);
        PracticeStartRequest request = new PracticeStartRequest();
        request.setQuestionSetId(91L);

        assertThrows(BusinessException.class, () -> service.startPractice(7L, request));

        verifyNoInteractions(sessions, questions);
    }

    @Test
    void generatingQuestionSetsCannotBeDeletedWhileAiIsRunning() {
        PracticeSessionMapper sessions = mock(PracticeSessionMapper.class);
        PracticeAnswerMapper answers = mock(PracticeAnswerMapper.class);
        QuestionItemMapper questions = mock(QuestionItemMapper.class);
        QuestionSetServiceImpl service = spy(new QuestionSetServiceImpl(
                questions, sessions, answers, mock(RetrievalService.class)));
        QuestionSet questionSet = new QuestionSet();
        questionSet.setId(91L);
        questionSet.setStatus("GENERATING");
        doReturn(questionSet).when(service).getOne(any());

        assertThrows(BusinessException.class, () -> service.deleteQuestionSet(7L, 91L));

        verifyNoInteractions(sessions, answers, questions);
        verify(service, never()).removeById(91L);
    }
}
