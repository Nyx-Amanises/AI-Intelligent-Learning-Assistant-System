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
import com.aiassistant.learning.dto.wrongquestion.WrongQuestionPageQuery;
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
import com.aiassistant.learning.vo.wrongquestion.WrongQuestionKnowledgePointVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

    @Test
    void knowledgeFilterMatchesCompleteLabelsRatherThanPrefixes() {
        installBook(List.of(submittedSession()), List.of(
                bookAnswer(1L, 31L, 21L, 1), bookAnswer(2L, 31L, 22L, 1), bookAnswer(3L, 31L, 23L, 1)), List.of(
                bookQuestion(21L, "SINGLE", "算法", "搜索方法"),
                bookQuestion(22L, "SINGLE", "算法复杂度", "复杂度分析"),
                bookQuestion(23L, "SINGLE", "算法、时间复杂度", "排序方法")));
        WrongQuestionPageQuery query = new WrongQuestionPageQuery();
        query.setKnowledgePoint(" 算法 ");

        var result = service.pageWrongQuestions(USER_ID, query);

        assertEquals(2L, result.getTotal());
        assertEquals(List.of(23L, 21L), result.getRecords().stream().map(item -> item.getQuestionId()).toList());
        assertEquals(Map.of("算法", 2L, "算法复杂度", 1L, "时间复杂度", 1L),
                knowledgeCounts(service.listKnowledgePoints(USER_ID, null, null)));
    }

    @Test
    void multipleLabelsAreSplitAndCountedOncePerWrongAnswerRecord() {
        installBook(List.of(submittedSession()), List.of(
                bookAnswer(1L, 31L, 21L, 1), bookAnswer(2L, 31L, 21L, 1), bookAnswer(3L, 31L, 22L, 1)), List.of(
                bookQuestion(21L, "SINGLE", " 算法,算法，图论、图论;动态规划；递归|搜索/排序\n查找\r\n算法", "题目"),
                bookQuestion(22L, "SINGLE", "算法", "题目")));

        Map<String, Long> counts = knowledgeCounts(service.listKnowledgePoints(USER_ID, null, null));

        assertEquals(Map.of("算法", 3L, "图论", 2L, "动态规划", 2L, "递归", 2L, "搜索", 2L, "排序", 2L, "查找", 2L), counts);
        WrongQuestionPageQuery query = new WrongQuestionPageQuery();
        query.setKnowledgePoint("图论");
        assertEquals(2L, service.pageWrongQuestions(USER_ID, query).getTotal());
    }

    @Test
    void knowledgeCandidatesIncludeWrongQuestionsBeyondTheCurrentPage() {
        List<PracticeAnswer> storedAnswers = new ArrayList<>();
        List<QuestionItem> storedQuestions = new ArrayList<>();
        for (long index = 1; index <= 12; index++) {
            storedAnswers.add(bookAnswer(index, 31L, 20L + index, 1));
            storedQuestions.add(bookQuestion(20L + index, "SINGLE", "知识点" + index, "题目"));
        }
        installBook(List.of(submittedSession()), storedAnswers, storedQuestions);
        WrongQuestionPageQuery query = new WrongQuestionPageQuery();
        query.setSize(5L);

        var firstPage = service.pageWrongQuestions(USER_ID, query);
        Map<String, Long> counts = knowledgeCounts(service.listKnowledgePoints(USER_ID, null, null));

        assertEquals(12L, firstPage.getTotal());
        assertEquals(3L, firstPage.getPages());
        assertEquals(5, firstPage.getRecords().size());
        assertEquals(12, counts.size());
        assertEquals(1L, counts.get("知识点1"));
        query.setCurrent(3L);
        assertEquals(List.of(22L, 21L), service.pageWrongQuestions(USER_ID, query).getRecords().stream()
                .map(item -> item.getQuestionId()).toList());
        assertEquals(counts, knowledgeCounts(service.listKnowledgePoints(USER_ID, null, null)));
    }

    @Test
    void candidatesAndPagesExcludeForeignUnsubmittedRemovedAndMissingQuestions() {
        installBook(List.of(
                submittedSession(), bookSession(32L, 99L, 41L, "SUBMITTED"),
                bookSession(33L, USER_ID, 41L, "IN_PROGRESS")), List.of(
                bookAnswer(1L, 31L, 21L, 1),
                bookAnswer(2L, 32L, 22L, 1),
                bookAnswer(3L, 33L, 23L, 1),
                bookAnswer(4L, 31L, 24L, 0),
                bookAnswer(5L, 31L, 999L, 1)), List.of(
                bookQuestion(21L, "SINGLE", "可见知识点", "正常错题"),
                bookQuestion(22L, "SINGLE", "他人知识点", "不应可见"),
                bookQuestion(23L, "SINGLE", "未提交知识点", "不应可见"),
                bookQuestion(24L, "SINGLE", "已移出知识点", "不应可见")));

        assertEquals(Map.of("可见知识点", 1L), knowledgeCounts(service.listKnowledgePoints(USER_ID, null, null)));
        var page = service.pageWrongQuestions(USER_ID, new WrongQuestionPageQuery());
        assertEquals(1L, page.getTotal());
        assertEquals(21L, page.getRecords().get(0).getQuestionId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MULTIPLE", "MULTI", " multiple "})
    void materialAndMultipleTypeFiltersApplyEquallyToCandidatesAndPages(String type) {
        installBook(List.of(submittedSession(), bookSession(32L, USER_ID, 42L, "SUBMITTED")), List.of(
                bookAnswer(1L, 31L, 21L, 1), bookAnswer(2L, 31L, 22L, 1),
                bookAnswer(3L, 31L, 23L, 1), bookAnswer(4L, 32L, 24L, 1)), List.of(
                bookQuestion(21L, "MULTIPLE", "多选知识点", "当前类型"),
                bookQuestion(22L, "MULTI", "历史多选知识点", "兼容类型"),
                bookQuestion(23L, "SINGLE", "单选知识点", "错误类型"),
                bookQuestion(24L, "MULTIPLE", "其他资料知识点", "错误资料")));
        WrongQuestionPageQuery query = new WrongQuestionPageQuery();
        query.setMaterialId(41L);
        query.setQuestionType(type);

        assertEquals(Map.of("多选知识点", 1L, "历史多选知识点", 1L),
                knowledgeCounts(service.listKnowledgePoints(USER_ID, 41L, type)));
        var page = service.pageWrongQuestions(USER_ID, query);
        assertEquals(List.of(22L, 21L), page.getRecords().stream().map(item -> item.getQuestionId()).toList());
        assertEquals(Map.of("其他资料知识点", 1L), knowledgeCounts(service.listKnowledgePoints(USER_ID, 42L, type)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SHORT_ANSWER", "SHORT", " short "})
    void historicalShortAnswerAliasWorksInBothDirections(String type) {
        installBook(List.of(submittedSession()), List.of(
                bookAnswer(1L, 31L, 21L, 1), bookAnswer(2L, 31L, 22L, 1), bookAnswer(3L, 31L, 23L, 1)), List.of(
                bookQuestion(21L, "SHORT", "旧简答", "题目"),
                bookQuestion(22L, "SHORT_ANSWER", "新简答", "题目"),
                bookQuestion(23L, "JUDGE", "判断", "题目")));
        WrongQuestionPageQuery query = new WrongQuestionPageQuery();
        query.setQuestionType(type);

        assertEquals(Map.of("旧简答", 1L, "新简答", 1L), knowledgeCounts(service.listKnowledgePoints(USER_ID, null, type)));
        assertEquals(2L, service.pageWrongQuestions(USER_ID, query).getTotal());
    }

    @Test
    void knowledgeAndKeywordFiltersAreAppliedTogetherBeforePagination() {
        installBook(List.of(submittedSession()), List.of(
                bookAnswer(1L, 31L, 21L, 1), bookAnswer(2L, 31L, 22L, 1), bookAnswer(3L, 31L, 23L, 1)), List.of(
                bookQuestion(21L, "SINGLE", "算法", "稳定排序方法"),
                bookQuestion(22L, "SINGLE", "算法", "图的深度搜索"),
                bookQuestion(23L, "SINGLE", "算法复杂度", "排序的时间开销")));
        WrongQuestionPageQuery query = new WrongQuestionPageQuery();
        query.setKnowledgePoint("算法");
        query.setKeyword(" 排序 ");
        query.setSize(1L);

        var page = service.pageWrongQuestions(USER_ID, query);

        assertEquals(1L, page.getTotal());
        assertEquals(21L, page.getRecords().get(0).getQuestionId());
        query.setKeyword("不匹配");
        assertEquals(0L, service.pageWrongQuestions(USER_ID, query).getTotal());
        assertEquals(Map.of("算法", 2L, "算法复杂度", 1L), knowledgeCounts(service.listKnowledgePoints(USER_ID, null, null)));
    }

    @Test
    void blankAndSeparatorOnlyLabelsShareAnExplicitUnlabeledFilter() {
        installBook(List.of(submittedSession()), List.of(
                bookAnswer(1L, 31L, 21L, 1), bookAnswer(2L, 31L, 22L, 1),
                bookAnswer(3L, 31L, 23L, 1), bookAnswer(4L, 31L, 24L, 1)), List.of(
                bookQuestion(21L, "SINGLE", null, "题目"), bookQuestion(22L, "SINGLE", "  ", "题目"),
                bookQuestion(23L, "SINGLE", " ,，、;；|/\r\n ", "题目"), bookQuestion(24L, "SINGLE", "算法", "题目")));
        WrongQuestionPageQuery query = new WrongQuestionPageQuery();
        query.setKnowledgePoint("未标注知识点");

        assertEquals(Map.of("未标注知识点", 3L, "算法", 1L), knowledgeCounts(service.listKnowledgePoints(USER_ID, null, " ")));
        assertEquals(3L, service.pageWrongQuestions(USER_ID, query).getTotal());
        query.setKnowledgePoint(" ");
        query.setKeyword(" ");
        assertEquals(4L, service.pageWrongQuestions(USER_ID, query).getTotal());
    }

    @Test
    void noOwnedSubmittedSessionsMeansNoCandidatesOrAnswerLookup() {
        installBook(List.of(bookSession(32L, 99L, 41L, "SUBMITTED")), List.of(), List.of());

        assertEquals(List.of(), service.listKnowledgePoints(USER_ID, null, null));
        assertEquals(0L, service.pageWrongQuestions(USER_ID, new WrongQuestionPageQuery()).getTotal());

        verifyNoInteractions(answers, questions, questionSets, materials);
    }

    /** 按实际 wrapper 中的值筛选内存数据，避免固定 mock 数据掩盖归属和错题状态条件缺失。 */
    private void installBook(List<PracticeSession> storedSessions, List<PracticeAnswer> storedAnswers, List<QuestionItem> storedQuestions) {
        when(sessions.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<PracticeSession> wrapper = invocation.getArgument(0);
            Object user = requiredEquality(wrapper, "user_id");
            Object status = requiredEquality(wrapper, "session_status");
            assertEquals(USER_ID, user);
            assertEquals("SUBMITTED", status);
            Object materialId = optionalEquality(wrapper, "material_id");
            return storedSessions.stream()
                    .filter(session -> Objects.equals(session.getUserId(), user))
                    .filter(session -> Objects.equals(session.getSessionStatus(), status))
                    .filter(session -> materialId == null || Objects.equals(session.getMaterialId(), materialId))
                    .toList();
        });
        when(answers.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<PracticeAnswer> wrapper = invocation.getArgument(0);
            Set<Object> ids = requiredInValues(wrapper, "session_id");
            Object marked = requiredEquality(wrapper, "marked_wrong");
            assertEquals(1, marked);
            return storedAnswers.stream().filter(answer -> ids.contains(answer.getSessionId()))
                    .filter(answer -> Objects.equals(answer.getMarkedWrong(), marked)).toList();
        });
        when(questions.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<QuestionItem> wrapper = invocation.getArgument(0);
            Set<Object> ids = requiredInValues(wrapper, "id");
            return storedQuestions.stream().filter(question -> ids.contains(question.getId())).toList();
        });
    }

    private Object requiredEquality(LambdaQueryWrapper<?> wrapper, String column) {
        Object value = optionalEquality(wrapper, column);
        assertTrue(value != null, "Missing scoped equality: " + column + " in " + wrapper.getSqlSegment());
        return value;
    }

    private Object optionalEquality(LambdaQueryWrapper<?> wrapper, String column) {
        Matcher matcher = Pattern.compile("\\b" + column + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}")
                .matcher(wrapper.getSqlSegment());
        return matcher.find() ? wrapper.getParamNameValuePairs().get(matcher.group(1)) : null;
    }

    private Set<Object> requiredInValues(LambdaQueryWrapper<?> wrapper, String column) {
        Matcher inClause = Pattern.compile("\\b" + column + "\\s+IN\\s*\\(([^)]*)\\)").matcher(wrapper.getSqlSegment());
        assertTrue(inClause.find(), "Missing scoped IN: " + column + " in " + wrapper.getSqlSegment());
        Matcher values = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}").matcher(inClause.group(1));
        Set<Object> result = new java.util.HashSet<>();
        while (values.find()) result.add(wrapper.getParamNameValuePairs().get(values.group(1)));
        assertTrue(!result.isEmpty());
        return result;
    }

    private Map<String, Long> knowledgeCounts(List<WrongQuestionKnowledgePointVO> points) {
        return points.stream().collect(Collectors.toMap(WrongQuestionKnowledgePointVO::knowledgePoint,
                WrongQuestionKnowledgePointVO::wrongCount));
    }

    private PracticeSession bookSession(long id, long user, long materialId, String status) {
        PracticeSession session = submittedSession();
        session.setId(id);
        session.setUserId(user);
        session.setMaterialId(materialId);
        session.setSessionStatus(status);
        return session;
    }

    private PracticeAnswer bookAnswer(long id, long sessionId, long questionId, int markedWrong) {
        PracticeAnswer answer = wrongAnswer();
        answer.setId(id);
        answer.setSessionId(sessionId);
        answer.setQuestionId(questionId);
        answer.setMarkedWrong(markedWrong);
        return answer;
    }

    private QuestionItem bookQuestion(long id, String type, String knowledgePoint, String stem) {
        QuestionItem question = new QuestionItem();
        question.setId(id);
        question.setQuestionType(type);
        question.setKnowledgePoint(knowledgePoint);
        question.setStemText(stem);
        return question;
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
