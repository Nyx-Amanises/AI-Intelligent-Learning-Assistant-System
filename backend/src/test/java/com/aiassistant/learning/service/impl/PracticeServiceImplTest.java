package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.aiassistant.learning.vo.practice.PracticeAnswerVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.aiassistant.learning.vo.practice.PracticeReviewStatusVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PracticeServiceImpl 单元测试：开始练习、提交判分、简答题 AI/规则评分、会话维护。
 */
class PracticeServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long QUESTION_SET_ID = 91L;
    private static final long MATERIAL_ID = 42L;

    private final PracticeSessionMapper sessions = mock(PracticeSessionMapper.class);
    private final PracticeAnswerMapper answers = mock(PracticeAnswerMapper.class);
    private final QuestionSetMapper questionSets = mock(QuestionSetMapper.class);
    private final QuestionItemMapper questionItems = mock(QuestionItemMapper.class);
    private final AiConfigService aiConfigService = mock(AiConfigService.class);
    private final AiChatService aiChatService = mock(AiChatService.class);
    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final PracticeServiceImpl service = new PracticeServiceImpl(
            sessions,
            answers,
            questionSets,
            questionItems,
            aiConfigService,
            aiChatService,
            retrievalService,
            new ObjectMapper(),
            mock(PracticeService.class),
            new TransactionTemplate(mock(PlatformTransactionManager.class))
    );

    /** 模拟数据库中已保存的答案行。 */
    private final List<PracticeAnswer> storedAnswers = new ArrayList<>();
    private final AtomicLong answerIdSequence = new AtomicLong(1);

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "practice-service-test");
        TableInfoHelper.initTableInfo(assistant, PracticeSession.class);
        TableInfoHelper.initTableInfo(assistant, PracticeAnswer.class);
        TableInfoHelper.initTableInfo(assistant, QuestionSet.class);
        TableInfoHelper.initTableInfo(assistant, QuestionItem.class);
    }

    @BeforeEach
    void setUpMappers() {
        when(answers.insert(any(PracticeAnswer.class))).thenAnswer(invocation -> {
            PracticeAnswer answer = invocation.getArgument(0);
            answer.setId(answerIdSequence.getAndIncrement());
            storedAnswers.add(answer);
            return 1;
        });
        when(answers.selectList(any())).thenAnswer(invocation -> new ArrayList<>(storedAnswers));
        when(sessions.insert(any(PracticeSession.class))).thenAnswer(invocation -> {
            PracticeSession session = invocation.getArgument(0);
            session.setId(501L);
            return 1;
        });
        when(retrievalService.retrieveMaterialSegments(any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void startPracticeCreatesAnInProgressSessionWithScoreTotals() {
        when(questionSets.selectOne(any())).thenReturn(questionSet("PUBLISHED"));
        when(questionItems.selectList(any())).thenReturn(List.of(single(1L, "A", 5), judge(2L, "TRUE", 3)));
        PracticeStartRequest request = new PracticeStartRequest();
        request.setQuestionSetId(QUESTION_SET_ID);

        PracticeDetailVO detail = service.startPractice(USER_ID, request);

        ArgumentCaptor<PracticeSession> saved = ArgumentCaptor.forClass(PracticeSession.class);
        verify(sessions).insert(saved.capture());
        assertEquals("IN_PROGRESS", saved.getValue().getSessionStatus());
        assertEquals(2, saved.getValue().getTotalQuestions());
        assertEquals(8, saved.getValue().getTotalScore());
        assertEquals(MATERIAL_ID, saved.getValue().getMaterialId());
        assertEquals(501L, detail.getSessionId());
        assertEquals(MATERIAL_ID, detail.getMaterialId());
        assertNull(detail.getQuestionIds(), "整套练习不应被标识为专项重做");
        assertEquals(2, detail.getAnswers().size());
        assertNull(detail.getAnswers().get(0).getUserAnswer());
    }

    @Test
    void startPracticeRejectsAQuestionSetWithoutQuestions() {
        when(questionSets.selectOne(any())).thenReturn(questionSet("PUBLISHED"));
        when(questionItems.selectList(any())).thenReturn(List.of());
        PracticeStartRequest request = new PracticeStartRequest();
        request.setQuestionSetId(QUESTION_SET_ID);

        assertThrows(BusinessException.class, () -> service.startPractice(USER_ID, request));

        verify(sessions, never()).insert(any(PracticeSession.class));
    }

    @Test
    void submitPracticeScoresObjectiveQuestionsAndParksShortAnswersForAi() {
        PracticeSession session = inProgressSession();
        when(sessions.selectOne(any())).thenReturn(session);
        when(questionItems.selectList(any())).thenReturn(List.of(
                single(1L, "A", 5),
                judge(2L, "TRUE", 3),
                shortAnswer(3L, "光合作用把光能转化为化学能", 10)
        ));

        PracticeDetailVO detail = service.submitPractice(USER_ID, submitRequest(
                answer(1L, "a"),
                answer(2L, "FALSE"),
                answer(3L, "把光能变成化学能")
        ));

        assertEquals("SUBMITTED", session.getSessionStatus());
        assertEquals(1, session.getCorrectCount());
        assertEquals(5, session.getObtainedScore());
        assertEquals(new BigDecimal("33.33"), session.getAccuracyRate());
        verify(answers).delete(any());
        verify(answers, times(3)).insert(any(PracticeAnswer.class));

        PracticeAnswerVO singleChoice = detail.getAnswers().get(0);
        assertEquals(1, singleChoice.getIsCorrect());
        assertEquals("RULE", singleChoice.getReviewMode());
        PracticeAnswerVO judgement = detail.getAnswers().get(1);
        assertEquals(0, judgement.getIsCorrect());
        assertEquals(0, judgement.getObtainedScore());
        PracticeAnswerVO pending = detail.getAnswers().get(2);
        assertEquals("AI_PENDING", pending.getReviewMode());
        assertEquals(0, pending.getObtainedScore());
        verifyNoInteractions(aiChatService);
    }

    @Test
    void reviewPendingShortAnswersFallsBackToRulesWhenAiIsInMockMode() {
        PracticeSession session = submittedSessionWithPendingShortAnswer("光合作用把光能转化为化学能", "光合作用把光能转化为化学能");
        when(aiConfigService.getResolvedConfig()).thenReturn(config(true, true, "test-key"));

        service.reviewPendingShortAnswersNow(session.getId());

        PracticeAnswer reviewed = storedAnswers.get(0);
        assertEquals("RULE", reviewed.getReviewMode());
        assertEquals(10, reviewed.getObtainedScore());
        assertEquals(1, reviewed.getIsCorrect());
        assertEquals(0, reviewed.getMarkedWrong());
        verify(answers).updateById(reviewed);
        verify(sessions).updateById(session);
        assertEquals(10, session.getObtainedScore());
        assertEquals(1, session.getCorrectCount());
        assertEquals(new BigDecimal("100.00"), session.getAccuracyRate());
        verifyNoInteractions(aiChatService);
    }

    @Test
    void reviewPendingShortAnswersUsesTheModelVerdictWhenAiIsAvailable() {
        PracticeSession session = submittedSessionWithPendingShortAnswer("参考答案", "学生答案");
        when(aiConfigService.getResolvedConfig()).thenReturn(config(true, false, "test-key"));
        when(aiChatService.chat(anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn("```json\n{\"score\": 7, \"correct\": true, \"comment\": \"要点基本齐全\"}\n```");

        service.reviewPendingShortAnswersNow(session.getId());

        PracticeAnswer reviewed = storedAnswers.get(0);
        assertEquals("AI", reviewed.getReviewMode());
        assertEquals(7, reviewed.getObtainedScore());
        assertEquals(1, reviewed.getIsCorrect());
        assertEquals("要点基本齐全", reviewed.getReviewComment());
        assertEquals(7, session.getObtainedScore());
    }

    @Test
    void reviewPendingShortAnswersFallsBackToRulesWhenTheModelCallFails() {
        PracticeSession session = submittedSessionWithPendingShortAnswer("参考答案", "完全无关的内容");
        when(aiConfigService.getResolvedConfig()).thenReturn(config(true, false, "test-key"));
        when(aiChatService.chat(anyString(), anyString(), anyString(), anyDouble()))
                .thenThrow(new BusinessException(500, "调用 AI 接口失败"));

        service.reviewPendingShortAnswersNow(session.getId());

        PracticeAnswer reviewed = storedAnswers.get(0);
        assertEquals("RULE", reviewed.getReviewMode());
        assertEquals(0, reviewed.getObtainedScore());
        assertEquals(0, reviewed.getIsCorrect());
        assertEquals(1, reviewed.getMarkedWrong());
        assertTrue(reviewed.getReviewComment().contains("AI 判分失败"));
    }

    @Test
    void reviewPendingShortAnswersDoesNothingWhenNothingIsPending() {
        PracticeSession session = inProgressSession();
        session.setSessionStatus("SUBMITTED");
        when(sessions.selectById(session.getId())).thenReturn(session);
        when(questionItems.selectList(any())).thenReturn(List.of(single(1L, "A", 5)));
        PracticeAnswer ruleAnswer = new PracticeAnswer();
        ruleAnswer.setQuestionId(1L);
        ruleAnswer.setReviewMode("RULE");
        storedAnswers.add(ruleAnswer);

        service.reviewPendingShortAnswersNow(session.getId());

        verify(answers, never()).updateById(any(PracticeAnswer.class));
        verify(sessions, never()).updateById(any(PracticeSession.class));
        verifyNoInteractions(aiConfigService, aiChatService);
    }

    @Test
    void waitForAiReviewReportsCompletionWhenNoAnswerIsPending() {
        when(sessions.selectOne(any())).thenReturn(inProgressSession());
        when(answers.selectCount(any())).thenReturn(0L);

        PracticeReviewStatusVO status = service.waitForAiReview(USER_ID, 501L, 1000L);

        assertTrue(status.getCompleted());
        assertFalse(status.getPending());
    }

    @Test
    void renamePracticeSessionValidatesTheName() {
        PracticeSession session = inProgressSession();
        when(sessions.selectOne(any())).thenReturn(session);

        assertThrows(BusinessException.class, () -> service.renamePracticeSession(USER_ID, 501L, "   "));
        assertThrows(BusinessException.class, () -> service.renamePracticeSession(USER_ID, 501L, "x".repeat(201)));
        service.renamePracticeSession(USER_ID, 501L, "  期中复习  ");

        ArgumentCaptor<LambdaUpdateWrapper<PracticeSession>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(sessions).update(any(), update.capture());
        assertTrue(update.getValue().getSqlSet().contains("session_name="));
        assertFalse(update.getValue().getSqlSet().contains("session_status"));
        assertTrue(update.getValue().getParamNameValuePairs().containsValue("期中复习"));
        verify(sessions, never()).updateById(any(PracticeSession.class));
    }

    @Test
    void deletePracticeSessionRemovesAnswersBeforeTheSession() {
        when(sessions.selectOne(any())).thenReturn(inProgressSession());

        service.deletePracticeSession(USER_ID, 501L);

        verify(answers).delete(any());
        verify(sessions).deleteById(501L);
    }

    @Test
    void sessionsOfOtherUsersAreReportedAsMissing() {
        when(sessions.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getPracticeDetail(USER_ID, 501L));
        assertThrows(BusinessException.class, () -> service.deletePracticeSession(USER_ID, 501L));
        assertThrows(BusinessException.class, () -> service.waitForAiReview(USER_ID, 501L, 1000L));
        verify(answers, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A,C", "C,A", "ca", "c、a", "A C", "A,A,C"})
    void multipleChoiceScoresTheSameCompleteSetRegardlessOfOrder(String userAnswer) {
        PracticeSession session = inProgressSession();
        when(sessions.selectOne(any())).thenReturn(session);
        when(questionItems.selectList(any())).thenReturn(List.of(question(1L, "MULTIPLE", "A,C", 5)));

        PracticeDetailVO detail = service.submitPractice(USER_ID, submitRequest(answer(1L, userAnswer)));

        assertEquals(1, session.getCorrectCount());
        assertEquals(5, session.getObtainedScore());
        assertEquals("RULE", detail.getAnswers().get(0).getReviewMode());
        assertEquals(0, storedAnswers.get(0).getMarkedWrong());
        verifyNoInteractions(aiChatService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "A", "A,B,C", "B,D", "A,C,E", "A,C?", "答案为 A,C", "A,C,"})
    void incompleteExtraOrMalformedMultipleAnswersReceiveNoPartialCredit(String userAnswer) {
        PracticeSession session = inProgressSession();
        when(sessions.selectOne(any())).thenReturn(session);
        when(questionItems.selectList(any())).thenReturn(List.of(question(1L, "MULTIPLE", "A,C", 5)));

        service.submitPractice(USER_ID, submitRequest(answer(1L, userAnswer)));

        assertEquals(0, session.getCorrectCount());
        assertEquals(0, session.getObtainedScore());
        assertEquals(1, storedAnswers.get(0).getMarkedWrong());
    }

    @Test
    void legacyMultiTypeUsesSetBasedScoring() {
        when(sessions.selectOne(any())).thenReturn(inProgressSession());
        when(questionItems.selectList(any())).thenReturn(List.of(question(1L, "MULTI", "AC", 5)));

        PracticeDetailVO detail = service.submitPractice(USER_ID, submitRequest(answer(1L, "C,A")));

        assertEquals(1, detail.getAnswers().get(0).getIsCorrect());
        assertEquals(5, detail.getAnswers().get(0).getObtainedScore());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "A,E", "bad answer", ""})
    void malformedMultipleReferencesCannotScoreAsCorrect(String reference) {
        when(sessions.selectOne(any())).thenReturn(inProgressSession());
        when(questionItems.selectList(any())).thenReturn(List.of(question(1L, "MULTIPLE", reference, 5)));

        PracticeDetailVO detail = service.submitPractice(USER_ID, submitRequest(answer(1L, reference)));

        assertEquals(0, detail.getAnswers().get(0).getIsCorrect());
        assertEquals(0, detail.getAnswers().get(0).getObtainedScore());
    }

    @Test
    void selectedPracticeCreatesANewSessionAndKeepsItsScopeOnRefreshAndSubmission() {
        when(questionSets.selectOne(any())).thenReturn(questionSet("PUBLISHED"));
        when(questionItems.selectList(any())).thenReturn(List.of(single(1L, "A", 5), single(2L, "B", 8)));
        PracticeStartRequest request = new PracticeStartRequest();
        request.setQuestionSetId(QUESTION_SET_ID);
        request.setQuestionIds(List.of(2L));

        PracticeDetailVO started = service.startPractice(USER_ID, request);

        ArgumentCaptor<PracticeSession> saved = ArgumentCaptor.forClass(PracticeSession.class);
        verify(sessions).insert(saved.capture());
        PracticeSession session = saved.getValue();
        assertEquals("[2]", session.getQuestionIdsJson());
        assertEquals("光合作用 - 专项复习", session.getSessionName());
        assertEquals(List.of(2L), started.getQuestionIds());
        assertEquals(1, started.getAnswers().size());
        assertNull(started.getAnswers().get(0).getUserAnswer());
        assertEquals(8, started.getTotalScore());
        verifyNoInteractions(answers);
        verify(sessions, never()).updateById(any(PracticeSession.class));

        when(sessions.selectOne(any())).thenReturn(session);
        PracticeDetailVO refreshed = service.getPracticeDetail(USER_ID, session.getId());
        assertEquals(List.of(2L), refreshed.getQuestionIds());
        assertEquals(1, refreshed.getAnswers().size());

        PracticeDetailVO submitted = service.submitPractice(USER_ID, submitRequest(answer(2L, "B")));
        assertEquals(1, submitted.getTotalQuestions());
        assertEquals(8, submitted.getTotalScore());
        assertEquals(8, submitted.getObtainedScore());
        assertEquals(new BigDecimal("100.00"), submitted.getAccuracyRate());
        assertEquals(List.of(2L), storedAnswers.stream().map(PracticeAnswer::getQuestionId).toList());
    }

    @Test
    void invalidOrForeignSelectionCannotCreateAPractice() {
        when(questionSets.selectOne(any())).thenReturn(questionSet("PUBLISHED"));
        when(questionItems.selectList(any())).thenReturn(List.of(single(1L, "A", 5)));
        List<List<Long>> invalidSelections = List.of(
                List.of(), List.of(0L), List.of(-1L), Arrays.asList((Long) null),
                List.of(1L, 1L), List.of(1L, 99L), java.util.Collections.nCopies(101, 1L));
        for (List<Long> ids : invalidSelections) {
            PracticeStartRequest request = new PracticeStartRequest();
            request.setQuestionSetId(QUESTION_SET_ID);
            request.setQuestionIds(ids);
            assertThrows(BusinessException.class, () -> service.startPractice(USER_ID, request));
        }
        verifyNoInteractions(sessions, answers);
    }

    @Test
    void anotherUsersQuestionSetCannotBeUsedEvenWithValidQuestionIds() {
        when(questionSets.selectOne(any())).thenReturn(null);
        PracticeStartRequest request = new PracticeStartRequest();
        request.setQuestionSetId(QUESTION_SET_ID);
        request.setQuestionIds(List.of(1L));

        assertThrows(BusinessException.class, () -> service.startPractice(USER_ID, request));

        verifyNoInteractions(questionItems, sessions, answers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "[1,1]", "[0]", "[\"1\"]", "[1.5]", "[999999999999999999999]", "{}"})
    void invalidSavedScopeCannotExpandToTheWholeQuestionSet(String scope) {
        PracticeSession session = inProgressSession();
        session.setQuestionIdsJson(scope);
        when(sessions.selectOne(any())).thenReturn(session);
        when(questionItems.selectList(any())).thenReturn(List.of(single(1L, "A", 5)));

        assertThrows(BusinessException.class, () -> service.getPracticeDetail(USER_ID, 501L));
        assertThrows(BusinessException.class, () -> service.submitPractice(USER_ID, submitRequest(answer(1L, "A"))));

        verifyNoInteractions(answers);
        verify(sessions, never()).updateById(any(PracticeSession.class));
    }

    @Test
    void submittingAnUnselectedQuestionDoesNotTouchPreviousAnswers() {
        PracticeSession session = inProgressSession();
        session.setQuestionIdsJson("[2]");
        when(sessions.selectOne(any())).thenReturn(session);
        when(questionItems.selectList(any())).thenReturn(List.of(single(1L, "A", 5), single(2L, "B", 5)));

        assertThrows(BusinessException.class, () -> service.submitPractice(USER_ID, submitRequest(answer(1L, "A"))));

        verifyNoInteractions(answers);
        verify(sessions, never()).updateById(any(PracticeSession.class));
    }

    @Test
    void aSecondSubmissionCannotReplaceTheOriginalAnswersAndSubmissionLocksTheSession() {
        PracticeSession session = inProgressSession();
        when(sessions.selectOne(any())).thenReturn(session);
        when(questionItems.selectList(any())).thenReturn(List.of(single(1L, "A", 5)));

        service.submitPractice(USER_ID, submitRequest(answer(1L, "A")));
        assertThrows(BusinessException.class, () -> service.submitPractice(USER_ID, submitRequest(answer(1L, "B"))));

        assertEquals("A", storedAnswers.get(0).getUserAnswer());
        assertEquals(5, session.getObtainedScore());
        verify(answers).delete(any());
        verify(answers).insert(any(PracticeAnswer.class));
        ArgumentCaptor<LambdaQueryWrapper<PracticeSession>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sessions, times(2)).selectOne(query.capture());
        assertTrue(query.getAllValues().stream().allMatch(wrapper -> wrapper.getSqlSegment().contains("for update")));
    }

    @Test
    void aiReviewAndSummaryOnlyUseQuestionsInTheSavedScope() {
        PracticeSession session = submittedSessionWithPendingShortAnswer("参考答案", "参考答案");
        session.setQuestionIdsJson("[3]");
        when(questionItems.selectList(any())).thenReturn(List.of(single(1L, "A", 99), shortAnswer(3L, "参考答案", 10)));
        PracticeAnswer unrelated = new PracticeAnswer();
        unrelated.setQuestionId(1L);
        unrelated.setIsCorrect(1);
        unrelated.setObtainedScore(99);
        unrelated.setReviewMode("RULE");
        storedAnswers.add(unrelated);
        when(aiConfigService.getResolvedConfig()).thenReturn(config(true, true, "test-key"));

        service.reviewPendingShortAnswersNow(session.getId());

        assertEquals(1, session.getTotalQuestions());
        assertEquals(10, session.getTotalScore());
        assertEquals(10, session.getObtainedScore());
        assertEquals(1, session.getCorrectCount());
        assertEquals(new BigDecimal("100.00"), session.getAccuracyRate());
        verify(answers, never()).updateById(unrelated);
    }

    @Test
    void practiceHistoryIncludesScopeAndKeepsLegacyFullPracticeAsNull() {
        PracticeSession selected = inProgressSession();
        selected.setQuestionIdsJson("[2,3]");
        PracticeSession legacy = inProgressSession();
        Page<PracticeSession> page = new Page<>(1, 10);
        page.setRecords(List.of(selected, legacy));
        when(sessions.selectPage(any(), any())).thenReturn(page);

        var result = service.pagePracticeSessions(USER_ID, 1L, 10L);

        assertEquals(List.of(2L, 3L), result.getRecords().get(0).getQuestionIds());
        assertNull(result.getRecords().get(1).getQuestionIds());
    }

    private PracticeSession submittedSessionWithPendingShortAnswer(String referenceAnswer, String userAnswer) {
        PracticeSession session = inProgressSession();
        session.setSessionStatus("SUBMITTED");
        session.setTotalQuestions(1);
        session.setTotalScore(10);
        when(sessions.selectById(session.getId())).thenReturn(session);
        when(questionItems.selectList(any())).thenReturn(List.of(shortAnswer(3L, referenceAnswer, 10)));
        PracticeAnswer pending = new PracticeAnswer();
        pending.setId(1L);
        pending.setSessionId(session.getId());
        pending.setQuestionId(3L);
        pending.setUserAnswer(userAnswer);
        pending.setReviewMode("AI_PENDING");
        pending.setObtainedScore(0);
        pending.setIsCorrect(0);
        storedAnswers.add(pending);
        return session;
    }

    private PracticeSession inProgressSession() {
        PracticeSession session = new PracticeSession();
        session.setId(501L);
        session.setUserId(USER_ID);
        session.setQuestionSetId(QUESTION_SET_ID);
        session.setMaterialId(MATERIAL_ID);
        session.setSessionName("练习");
        session.setSessionStatus("IN_PROGRESS");
        session.setStartTime(LocalDateTime.now().minusMinutes(5));
        session.setTotalQuestions(3);
        session.setTotalScore(18);
        session.setCorrectCount(0);
        session.setObtainedScore(0);
        return session;
    }

    private QuestionSet questionSet(String status) {
        QuestionSet questionSet = new QuestionSet();
        questionSet.setId(QUESTION_SET_ID);
        questionSet.setUserId(USER_ID);
        questionSet.setMaterialId(MATERIAL_ID);
        questionSet.setTitle("光合作用");
        questionSet.setStatus(status);
        return questionSet;
    }

    private QuestionItem single(Long id, String correctAnswer, int score) {
        return question(id, "SINGLE", correctAnswer, score);
    }

    private QuestionItem judge(Long id, String correctAnswer, int score) {
        return question(id, "JUDGE", correctAnswer, score);
    }

    private QuestionItem shortAnswer(Long id, String correctAnswer, int score) {
        return question(id, "SHORT_ANSWER", correctAnswer, score);
    }

    private QuestionItem question(Long id, String type, String correctAnswer, int score) {
        QuestionItem question = new QuestionItem();
        question.setId(id);
        question.setQuestionSetId(QUESTION_SET_ID);
        question.setQuestionType(type);
        question.setStemText("题目 " + id);
        question.setCorrectAnswer(correctAnswer);
        question.setScore(score);
        question.setSortNo(id.intValue());
        return question;
    }

    private PracticeSubmitRequest submitRequest(PracticeAnswerRequest... items) {
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setSessionId(501L);
        request.setAnswers(List.of(items));
        return request;
    }

    private PracticeAnswerRequest answer(Long questionId, String userAnswer) {
        PracticeAnswerRequest request = new PracticeAnswerRequest();
        request.setQuestionId(questionId);
        request.setUserAnswer(userAnswer);
        return request;
    }

    private AiConfigService.ResolvedAiConfig config(boolean enabled, boolean mockMode, String apiKey) {
        return new AiConfigService.ResolvedAiConfig(
                enabled, mockMode, "OPENAI_COMPATIBLE", "https://example.test", "/v1/chat/completions",
                "OPENAI_COMPATIBLE", null, null, apiKey, null, "test-model", null);
    }
}
