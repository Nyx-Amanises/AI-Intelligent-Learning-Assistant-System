package com.aiassistant.learning.service.assistant.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.PracticeService;
import com.aiassistant.learning.service.assistant.AssistantStructuredIntent;
import com.aiassistant.learning.service.assistant.AssistantTool.ToolContext;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.practice.PracticeAnswerVO;
import com.aiassistant.learning.vo.practice.PracticeDetailVO;
import com.aiassistant.learning.vo.practice.PracticeSessionPageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PracticeAssistantToolsTest {
    private final PracticeService service = mock(PracticeService.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final PracticeDetailAssistantTool detailTool = new PracticeDetailAssistantTool(service, json);
    private final PracticeListAssistantTool listTool = new PracticeListAssistantTool(service, json);

    @Test
    void explicitIdOverridesOldContextAndSwitchesOnlyAfterOwnedLookup() throws Exception {
        AssistantSession session = boundSession();
        when(service.getPracticeDetail(7L, 25L)).thenReturn(detail(25L, "SUBMITTED", List.of(answer(1, "RULE", 0))));
        var result = detailTool.execute(context(session, "查练习 #25 的错题", AssistantStructuredIntent.builder().practiceSessionId(30L).build()));
        assertEquals("SUCCESS", result.status());
        verify(service).getPracticeDetail(7L, 25L);
        assertEquals(25L, session.getCurrentPracticeSessionId());
        assertEquals(25L, session.getCurrentContextId());
        assertEquals(90L, session.getCurrentMaterialId());
        assertEquals(80L, session.getCurrentQuestionSetId());
        assertEquals(25, json.readTree(result.toolResultJson()).path("sessionId").asInt());
    }

    @Test
    void structuredSelectionAndCurrentContextUseTheAuthenticatedUser() {
        when(service.getPracticeDetail(eq(7L), anyLong())).thenAnswer(call -> detail(call.getArgument(1), "SUBMITTED", List.of()));
        detailTool.execute(context(boundSession(), "第2条", AssistantStructuredIntent.builder().practiceSessionId(25L).build()));
        detailTool.execute(context(boundSession(), "这题为什么错", AssistantStructuredIntent.empty()));
        verify(service).getPracticeDetail(7L, 25L);
        verify(service).getPracticeDetail(7L, 10L);
    }

    @Test
    void invalidExplicitIdsNeverFallBackToOldPracticeAndDoNotCallService() {
        for (String text : List.of("练习 #0", "练习 #-2", "练习 #1.5", "练习 #1e3", "练习 #abc", "练习 #99999999999999999999999")) {
            var result = detailTool.execute(context(boundSession(), text, AssistantStructuredIntent.empty()));
            assertEquals("FAILED", result.status(), text);
            assertTrue(result.errorMessage().contains("正整数"), text);
        }
        var missing = detailTool.execute(context(null, "查这次练习", AssistantStructuredIntent.empty()));
        assertEquals("FAILED", missing.status());
        verifyNoInteractions(service);
    }

    @Test
    void ownershipFailureDoesNotExposeAnotherRecordOrChangeTheContext() {
        AssistantSession session = boundSession();
        when(service.getPracticeDetail(7L, 99L)).thenThrow(new BusinessException(404, "练习记录不存在"));
        var result = detailTool.execute(context(session, "查看练习 #99", AssistantStructuredIntent.empty()));
        assertEquals("FAILED", result.status());
        assertEquals("练习记录不存在", result.errorMessage());
        assertEquals(10L, session.getCurrentPracticeSessionId());
        assertFalse(String.valueOf(result.toolResultJson()).contains("answers"));
    }

    @Test
    void pendingGradingIsNotDescribedAsWrongAndAnswersReachTheModelSummary() {
        String summary = PracticeDetailAssistantTool.buildSummary(detail(25L, "SUBMITTED",
                List.of(answer(1, "AI_PENDING", 0), answer(2, "RULE", 0))), "第1题为什么错");
        assertTrue(summary.contains("等待 AI 评分，当前分数和正确率为暂时结果"));
        assertTrue(summary.contains("等待 AI 评分（非错题）"));
        assertTrue(summary.contains("用户答案：AC"));
        assertTrue(summary.contains("参考答案：AB"));
        assertTrue(summary.contains("答案解析：A 和 B 正确"));
        assertTrue(summary.contains("A：选项 A"));
        assertTrue(summary.contains("判分说明：规则比较"));
    }

    @Test
    void unsubmittedPracticeDoesNotClaimZeroFinalScore() {
        String summary = PracticeDetailAssistantTool.buildSummary(detail(25L, "IN_PROGRESS", List.of(answer(1, "RULE", null))), "解释这道题");
        assertTrue(summary.contains("尚未提交，没有最终得分或正确率"));
        assertFalse(summary.contains("正确率：0"));
        assertFalse(summary.contains("判分状态：错误"));
    }

    @Test
    void summaryBoundsBothQuestionsAndCharactersAndPrioritizesTheRequestedQuestion() {
        List<PracticeAnswerVO> answers = IntStream.rangeClosed(1, 15).mapToObj(id -> {
            PracticeAnswerVO answer = answer(id, "RULE", 0);
            answer.setStemText("题目" + id + "：" + "很长的题干".repeat(1000));
            answer.setAnswerAnalysis("解析".repeat(1000));
            return answer;
        }).toList();
        String summary = PracticeDetailAssistantTool.buildSummary(detail(25L, "SUBMITTED", answers), "第15题为什么错");
        assertTrue(summary.length() <= 8000);
        assertTrue(summary.contains("第 15 题（题目 ID 15"));
        assertTrue(summary.split("（题目 ID", -1).length - 1 <= 8);
        assertFalse(summary.contains("第 14 题（题目 ID 14"));
    }

    @Test
    void listUsesOwnedSubmittedPageAndKeepsPotentiallyPendingRecords() throws Exception {
        var record = PracticeSessionPageVO.builder().id(25L).sessionName("待评分练习").sessionStatus("SUBMITTED")
                .totalQuestions(5).obtainedScore(0).accuracyRate(BigDecimal.ZERO).build();
        var page = PageVO.<PracticeSessionPageVO>builder().current(2L).size(10L).total(11L).pages(2L).records(List.of(record)).build();
        when(service.pagePracticeSessions(7L, 2L, 10L)).thenReturn(page);
        var result = listTool.execute(context(null, "查看练习记录第2页", AssistantStructuredIntent.builder().practicePage(3).build()));
        assertEquals("SUCCESS", result.status());
        verify(service).pagePracticeSessions(7L, 2L, 10L);
        assertTrue(result.summaryText().contains("包含等待 AI 评分"));
        assertTrue(result.summaryText().contains("练习 #25"));
        assertEquals(1, json.readTree(result.toolResultJson()).path("records").size());
    }

    @Test
    void invalidPageNeverQueriesAndEmptyPageExplainsItsScope() {
        assertEquals("FAILED", listTool.execute(context(null, "练习列表", AssistantStructuredIntent.builder().practicePage(0).build())).status());
        verifyNoInteractions(service);
        when(service.pagePracticeSessions(7L, 1L, 10L)).thenReturn(PageVO.<PracticeSessionPageVO>builder()
                .current(1L).size(10L).total(0L).pages(0L).records(List.of()).build());
        assertTrue(listTool.execute(context(null, "练习列表", AssistantStructuredIntent.empty())).summaryText()
                .contains("尚未提交的练习不会出现在历史列表"));
    }

    private ToolContext context(AssistantSession session, String text, AssistantStructuredIntent intent) {
        return new ToolContext(7L, session, text, "test", intent);
    }

    private AssistantSession boundSession() {
        AssistantSession session = new AssistantSession();
        session.setCurrentPracticeSessionId(10L);
        session.setCurrentContextType("PRACTICE_SESSION");
        session.setCurrentContextId(10L);
        return session;
    }

    private PracticeDetailVO detail(Long id, String status, List<PracticeAnswerVO> answers) {
        return PracticeDetailVO.builder().sessionId(id).materialId(90L).questionSetId(80L).sessionName("测试练习")
                .sessionStatus(status).totalQuestions(answers.size()).totalScore(100).obtainedScore(0)
                .correctCount(0).accuracyRate(BigDecimal.ZERO).answers(answers).build();
    }

    private PracticeAnswerVO answer(int id, String mode, Integer correct) {
        return PracticeAnswerVO.builder().questionId((long) id).questionType("MULTIPLE").stemText("请选择正确选项")
                .optionA("选项 A").optionB("选项 B").optionC("选项 C").optionD("选项 D")
                .userAnswer("AC").correctAnswer("AB").referenceAnswer("AB").isCorrect(correct).obtainedScore(0)
                .reviewMode(mode).reviewComment("规则比较").answerAnalysis("A 和 B 正确").build();
    }
}
