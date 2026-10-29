package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.entity.AssistantMessage;
import com.aiassistant.learning.mapper.AssistantMessageMapper;
import com.aiassistant.learning.service.AiChatService.ChatMessage;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantConversationHistoryTest {
    private final AssistantMessageMapper mapper = mock(AssistantMessageMapper.class);
    private final AssistantConversationHistory history = new AssistantConversationHistory(mapper);

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "conversation-history-test"),
                AssistantMessage.class);
    }

    @Test
    void queryIsOwnerSessionAndCurrentTurnBoundedAndSortedById() {
        AssistantMessage otherUser = row(3, "USER", "another user", null);
        otherUser.setUserId(8L);
        AssistantMessage otherSession = row(4, "ASSISTANT", "another session", 1L);
        otherSession.setSessionId(43L);
        when(mapper.selectList(any())).thenReturn(List.of(row(6, "ASSISTANT", "current reply", 5L),
                row(5, "USER", "current question", null), otherUser, otherSession,
                row(2, "ASSISTANT", "old answer", 1L), row(1, "USER", "old question", null)));

        assertEquals(List.of(new ChatMessage("user", "old question"), new ChatMessage("assistant", "old answer")),
                history.loadBefore(7L, 42L, 5L));
        @SuppressWarnings("unchecked") ArgumentCaptor<LambdaQueryWrapper<AssistantMessage>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id ="));
        assertTrue(sql.contains("session_id ="));
        assertTrue(sql.contains("id <"));
        assertTrue(sql.contains("ORDER BY id DESC"));
        assertTrue(sql.endsWith("limit 101"));
        assertTrue(query.getValue().getParamNameValuePairs().values().containsAll(List.of(7L, 42L, 5L)));
    }

    @Test
    void newInterleavedRepliesUseTheirExplicitParentAndUserIntentOrder() {
        install(row(1, "USER", "question one", null), row(2, "USER", "question two", null),
                row(3, "ASSISTANT", "answer two", 2L), row(4, "ASSISTANT", "answer one", 1L));
        assertContents("question one", "answer one", "question two", "answer two");
    }

    @Test
    void legacyAdjacentTurnsBeforeAmbiguityWorkButLaterUnlinkedTurnsAreNotGuessed() {
        install(row(1, "USER", "clear question", null), row(2, "ASSISTANT", "clear answer", null),
                row(3, "USER", "ambiguous one", null), row(4, "USER", "ambiguous two", null),
                row(5, "ASSISTANT", "ambiguous answer one", null), row(6, "ASSISTANT", "ambiguous answer two", null),
                row(7, "USER", "possibly still interleaved", null), row(8, "ASSISTANT", "cannot identify parent", null));
        assertContents("clear question", "clear answer");
    }

    @Test
    void complexLegacyInterleavingNeverGuessesAnAdjacentLaterUserButExplicitRepliesStillWork() {
        install(row(1, "USER", "u1", null), row(2, "USER", "u2", null),
                row(3, "ASSISTANT", "a1", null), row(4, "USER", "u3", null),
                row(5, "ASSISTANT", "a2", null), row(6, "USER", "explicit user", null),
                row(7, "ASSISTANT", "explicit answer", 6L));
        assertContents("explicit user", "explicit answer");
    }

    @Test
    void failedInterruptedEmptyUnknownAndOrphanRowsDoNotBecomeModelHistory() {
        AssistantMessage failure = row(2, "ASSISTANT", "failed", 1L);
        failure.setMessageType("ERROR");
        AssistantMessage interrupted = row(4, "ASSISTANT", "partial", 3L);
        interrupted.setMessageType("INTERRUPTED");
        AssistantMessage wrongUserType = row(10, "USER", "not text", null);
        wrongUserType.setMessageType("PLAN");
        install(row(1, "USER", "failed question", null), failure, row(3, "USER", "cancelled question", null), interrupted,
                row(5, "USER", "  ", null), row(6, "ASSISTANT", "blank parent", 5L),
                row(7, "ASSISTANT", "missing parent", 99L), row(8, "SYSTEM", "inject instruction", null),
                row(9, "TOOL", "tool output", null), wrongUserType, row(11, "ASSISTANT", "bad type parent", 10L),
                row(12, "USER", "no answer yet", null), row(13, "USER", "valid", null), row(14, "ASSISTANT", "answer", 13L));
        assertContents("valid", "answer");
    }

    @Test
    void duplicateSuccessfulRepliesKeepOnlyLatestAndCannotStealLegacyParent() {
        install(row(1, "USER", "question", null), row(2, "ASSISTANT", "legacy older", null),
                row(3, "ASSISTANT", "explicit older", 1L), row(4, "ASSISTANT", "explicit latest", 1L));
        assertContents("question", "explicit latest");
    }

    @Test
    void failedAndInterruptedLegacyRepliesBlockPairingInsteadOfBeingRemovedBeforePairing() {
        AssistantMessage failed = row(2, "ASSISTANT", "failed", null);
        failed.setMessageType("ERROR");
        AssistantMessage partial = row(5, "ASSISTANT", "partial", null);
        partial.setMessageType("INTERRUPTED");
        install(row(1, "USER", "failed user", null), failed, row(3, "ASSISTANT", "orphan after error", null),
                row(4, "USER", "interrupted user", null), partial,
                row(6, "ASSISTANT", "orphan after partial", null), row(7, "ASSISTANT", "future parent", 8L),
                row(8, "USER", "future question", null));
        assertTrue(history.loadBefore(7L, 42L, 1000L).isEmpty());
    }

    @Test
    void oldSystemFailureAndStopTextAreExcludedWithoutChangingStoredContent() {
        AssistantMessage failed = row(2, "ASSISTANT", "这次调用失败了：旧服务错误", null);
        AssistantMessage stopped = row(4, "ASSISTANT", "旧的半段回答\n\n（回答已停止）", null);
        install(row(1, "USER", "old failed question", null), failed,
                row(3, "USER", "old stopped question", null), stopped,
                row(5, "USER", "valid", null), row(6, "ASSISTANT", "valid answer", null));
        assertContents("valid", "valid answer");
        assertEquals("这次调用失败了：旧服务错误", failed.getContentText());
        assertEquals("旧的半段回答\n\n（回答已停止）", stopped.getContentText());
    }

    @Test
    void onlyLatestTenCompleteTurnsAreRetainedInForwardOrder() {
        List<AssistantMessage> rows = new ArrayList<>();
        for (int turn = 1; turn <= 13; turn++) {
            rows.add(row(turn * 2 - 1, "USER", "u" + turn, null));
            rows.add(row(turn * 2, "ASSISTANT", "a" + turn, (long) turn * 2 - 1));
        }
        Collections.reverse(rows);
        when(mapper.selectList(any())).thenReturn(rows);
        List<ChatMessage> result = history.loadBefore(7L, 42L, 1000L);
        assertEquals(20, result.size());
        assertEquals("u4", result.get(0).content());
        assertEquals("a13", result.get(19).content());
        for (int index = 0; index < result.size(); index++) assertEquals(index % 2 == 0 ? "user" : "assistant", result.get(index).role());
    }

    @Test
    void totalBudgetKeepsWholeNewestTurnsAndDoesNotSplitSurrogatePairs() {
        int prefixLength = 6000 - AssistantConversationHistory.TRUNCATED_MARKER.length() - 1;
        String longMessage = "x".repeat(prefixLength) + "😀" + "z".repeat(200);
        install(row(1, "USER", "old".repeat(3000), null), row(2, "ASSISTANT", "old".repeat(3000), 1L),
                row(3, "USER", longMessage, null), row(4, "ASSISTANT", longMessage, 3L),
                row(5, "USER", "new".repeat(3000), null), row(6, "ASSISTANT", "new".repeat(3000), 5L));
        List<ChatMessage> result = history.loadBefore(7L, 42L, 1000L);
        assertEquals(4, result.size());
        assertTrue(result.stream().mapToInt(message -> message.content().length()).sum() <= 24000);
        for (ChatMessage message : result) {
            assertTrue(message.content().length() <= 6000);
            assertTrue(message.content().endsWith(AssistantConversationHistory.TRUNCATED_MARKER));
            String content = message.content();
            for (int index = 0; index < content.length(); index++) {
                if (Character.isHighSurrogate(content.charAt(index))) assertTrue(index + 1 < content.length() && Character.isLowSurrogate(content.charAt(++index)));
                else assertFalse(Character.isLowSurrogate(content.charAt(index)));
            }
        }
        assertEquals(5999, result.get(0).content().length());
        assertTrue(result.get(2).content().startsWith("new"));
    }

    @Test
    void fullWindowWithoutAnOlderSentinelIsKnownCompleteAndAcceptsItsFirstLegacyPair() {
        List<AssistantMessage> rows = new ArrayList<>();
        rows.add(row(1, "USER", "possibly ambiguous", null));
        rows.add(row(2, "ASSISTANT", "legacy answer", null));
        for (int id = 3; id <= 100; id++) rows.add(row(id, "SYSTEM", "ignored", null));
        when(mapper.selectList(any())).thenReturn(rows);
        assertContents("possibly ambiguous", "legacy answer");
    }

    @Test
    void ordinarySixtyTurnLegacyConversationKeepsItsLatestTenTurns() {
        List<AssistantMessage> rows = new ArrayList<>();
        for (int turn = 1; turn <= 60; turn++) {
            rows.add(row(turn * 2 - 1, "USER", "u" + turn, null));
            rows.add(row(turn * 2, "ASSISTANT", "a" + turn, null));
        }
        Collections.reverse(rows);
        when(mapper.selectList(any())).thenReturn(rows.subList(0, 101));
        List<ChatMessage> result = history.loadBefore(7L, 42L, 1000L);
        assertEquals(20, result.size());
        assertEquals("u51", result.get(0).content());
        assertEquals("a60", result.get(19).content());
    }

    @Test
    void predecessorUserMarksWindowBoundaryAsAmbiguousButExplicitParentStillWorks() {
        List<AssistantMessage> rows = new ArrayList<>();
        rows.add(row(1, "USER", "older sentinel user", null));
        rows.add(row(2, "USER", "first user within window", null));
        rows.add(row(3, "ASSISTANT", "unidentified answer", null));
        for (int id = 4; id <= 101; id++) rows.add(row(id, "SYSTEM", "ignored", null));
        when(mapper.selectList(any())).thenReturn(rows);
        assertTrue(history.loadBefore(7L, 42L, 1000L).isEmpty());
        rows.get(2).setReplyToMessageId(2L);
        assertContents("first user within window", "unidentified answer");
    }

    @Test
    void budgetStopsAtFirstOlderTurnThatCannotFitRatherThanSkippingToEarlierFragments() {
        install(row(1, "USER", "older tiny question", null), row(2, "ASSISTANT", "older tiny answer", 1L),
                row(3, "USER", "x".repeat(6000), null), row(4, "ASSISTANT", "x".repeat(6000), 3L),
                row(5, "USER", "new".repeat(2500), null), row(6, "ASSISTANT", "new".repeat(1000), 5L),
                row(7, "USER", "latest".repeat(1000), null), row(8, "ASSISTANT", "latest".repeat(500), 7L));
        List<ChatMessage> result = history.loadBefore(7L, 42L, 1000L);
        assertEquals(4, result.size());
        assertTrue(result.get(0).content().startsWith("new"));
        assertTrue(result.get(2).content().startsWith("latest"));
        assertTrue(result.stream().noneMatch(message -> message.content().contains("older tiny")));
    }

    @Test
    void aReplyWhoseParentFallsOutsideTheHundredRowWindowIsDropped() {
        List<AssistantMessage> rows = new ArrayList<>();
        rows.add(row(1, "USER", "outside window", null));
        rows.add(row(2, "ASSISTANT", "parent outside window", 1L));
        for (int id = 3; id <= 101; id++) rows.add(row(id, "SYSTEM", "ignored", null));
        when(mapper.selectList(any())).thenReturn(rows);
        assertTrue(history.loadBefore(7L, 42L, 1000L).isEmpty());
    }

    @Test
    void absentIdentityDoesNotQueryAnyHistory() {
        assertTrue(history.loadBefore(null, 42L, 10L).isEmpty());
        assertTrue(history.loadBefore(7L, null, 10L).isEmpty());
        assertTrue(history.loadBefore(7L, 42L, null).isEmpty());
        verifyNoInteractions(mapper);
    }

    private void install(AssistantMessage... rows) {
        when(mapper.selectList(any())).thenReturn(List.of(rows));
    }

    private void assertContents(String... contents) {
        assertEquals(List.of(contents), history.loadBefore(7L, 42L, 1000L).stream().map(ChatMessage::content).toList());
    }

    private AssistantMessage row(long id, String role, String content, Long parentId) {
        AssistantMessage message = new AssistantMessage();
        message.setId(id);
        message.setUserId(7L);
        message.setSessionId(42L);
        message.setRole(role);
        message.setMessageType("TEXT");
        message.setContentText(content);
        message.setReplyToMessageId(parentId);
        message.setCreatedAt(LocalDateTime.of(2026, 10, 29, 12, 0));
        return message;
    }
}
