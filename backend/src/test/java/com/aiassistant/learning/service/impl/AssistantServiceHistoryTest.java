package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.AiStreamAbortedException;
import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.assistant.AssistantMessageSendRequest;
import com.aiassistant.learning.entity.AssistantMessage;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.entity.AssistantToolCall;
import com.aiassistant.learning.mapper.AssistantMessageMapper;
import com.aiassistant.learning.mapper.AssistantSessionMapper;
import com.aiassistant.learning.mapper.AssistantToolCallMapper;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiChatService.ChatMessage;
import com.aiassistant.learning.service.assistant.AssistantAgentOrchestrator;
import com.aiassistant.learning.service.assistant.AssistantAgentOrchestrator.AssistantPreparedResult;
import com.aiassistant.learning.service.assistant.AssistantConversationHistory;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;

class AssistantServiceHistoryTest {
    private final AssistantSessionMapper sessions = mock(AssistantSessionMapper.class);
    private final AssistantMessageMapper messages = mock(AssistantMessageMapper.class);
    private final AssistantToolCallMapper tools = mock(AssistantToolCallMapper.class);
    private final AiChatService chat = mock(AiChatService.class);
    private final AssistantAgentOrchestrator orchestrator = mock(AssistantAgentOrchestrator.class);
    private final AssistantConversationHistory history = mock(AssistantConversationHistory.class);
    private final AssistantSession session = new AssistantSession();
    private final List<AssistantMessage> inserted = new ArrayList<>();
    private final List<ChatMessage> previous = List.of(new ChatMessage("user", "请记住变量n是7"),
            new ChatMessage("assistant", "好的，n是7"));
    private final List<ChatMessage> withCurrent = List.of(previous.get(0), previous.get(1),
            new ChatMessage("user", "结合资料回答：那n乘2是多少？"));
    private long nextId = 100;

    @BeforeAll
    static void metadata() {
        MapperBuilderAssistant builder = new MapperBuilderAssistant(new MybatisConfiguration(), "assistant-service-history-test");
        TableInfoHelper.initTableInfo(builder, AssistantMessage.class);
        TableInfoHelper.initTableInfo(builder, AssistantSession.class);
        TableInfoHelper.initTableInfo(builder, AssistantToolCall.class);
    }

    @BeforeEach
    void setup() {
        session.setId(42L);
        session.setUserId(7L);
        session.setTitle("上下文会话");
        when(sessions.selectOne(any())).thenReturn(session);
        when(history.loadBefore(7L, 42L, 100L)).thenReturn(previous);
        doAnswer(invocation -> {
            AssistantMessage message = invocation.getArgument(0);
            message.setId(nextId++);
            message.setCreatedAt(LocalDateTime.of(2026, 10, 29, 12, 0));
            inserted.add(message);
            return 1;
        }).when(messages).insert(any(AssistantMessage.class));
        when(tools.selectList(any())).thenReturn(List.of());
    }

    @Test
    void synchronousReplyLoadsHistoryAfterUserInsertAndPassesSnapshotToRespond() {
        when(orchestrator.respond(eq(7L), same(session), eq("那n乘2是多少？"), eq("test-model"), same(previous)))
                .thenReturn(new AssistantAgentOrchestrator.AssistantAgentResult("14", "{}", "{}", "test-model", List.of(), List.of()));
        var result = service(Runnable::run).sendMessage(7L, 42L, request());

        var order = inOrder(messages, history, orchestrator);
        order.verify(messages).insert(any(AssistantMessage.class));
        order.verify(history).loadBefore(7L, 42L, 100L);
        order.verify(orchestrator).respond(7L, session, "那n乘2是多少？", "test-model", previous);
        assertEquals(2, inserted.size());
        assertEquals("USER", inserted.get(0).getRole());
        assertNull(inserted.get(0).getReplyToMessageId());
        assertEquals(100L, inserted.get(1).getReplyToMessageId());
        assertEquals("TEXT", inserted.get(1).getMessageType());
        assertEquals("14", result.getAssistantMessage().getContentText());
        verify(chat, never()).chat(anyString(), anyList(), anyString(), anyDouble());
    }

    @Test
    void streamUsesPreparedRoleMessagesWithCurrentPromptOnlyOnce() {
        when(orchestrator.prepare(7L, session, "那n乘2是多少？", "test-model", previous)).thenReturn(prepared(true));
        doAnswer(invocation -> {
            Consumer<String> delta = invocation.getArgument(4);
            delta.accept("14");
            return null;
        }).when(chat).streamChat(eq("system"), eq(withCurrent), eq("test-model"), eq(0.3), any());

        service(Runnable::run).streamMessage(7L, 42L, request());

        verify(history).loadBefore(7L, 42L, 100L);
        verify(orchestrator).prepare(7L, session, "那n乘2是多少？", "test-model", previous);
        verify(chat).streamChat(eq("system"), eq(withCurrent), eq("test-model"), eq(0.3), any());
        verify(chat, never()).streamChat(anyString(), anyString(), anyString(), anyDouble(), any());
        assertEquals(2, inserted.size());
        assertEquals(100L, inserted.get(1).getReplyToMessageId());
        assertEquals("TEXT", inserted.get(1).getMessageType());
        verify(orchestrator).captureConversationMemory(7L, session, "那n乘2是多少？", "14");
    }

    @Test
    void streamFailureIsStoredAsErrorForDisplayWithoutCapturingMemory() {
        when(orchestrator.prepare(7L, session, "那n乘2是多少？", "test-model", previous)).thenReturn(prepared(true));
        doThrow(new BusinessException("测试模型失败")).when(chat).streamChat(anyString(), anyList(), anyString(), anyDouble(), any());
        service(Runnable::run).streamMessage(7L, 42L, request());

        assertEquals(2, inserted.size());
        assertEquals("ERROR", inserted.get(1).getMessageType());
        assertEquals(100L, inserted.get(1).getReplyToMessageId());
        assertEquals("这次调用失败了：测试模型失败", inserted.get(1).getContentText());
        verify(orchestrator, never()).captureConversationMemory(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void partialStopIsStoredAsInterruptedWithExplicitParentAndNoMemory() {
        when(orchestrator.prepare(7L, session, "那n乘2是多少？", "test-model", previous)).thenReturn(prepared(true));
        doAnswer(invocation -> {
            Consumer<String> delta = invocation.getArgument(4);
            delta.accept("算到一半");
            throw new AiStreamAbortedException("user stopped");
        }).when(chat).streamChat(anyString(), anyList(), anyString(), anyDouble(), any());
        service(Runnable::run).streamMessage(7L, 42L, request());

        assertEquals(2, inserted.size());
        assertEquals("INTERRUPTED", inserted.get(1).getMessageType());
        assertEquals(100L, inserted.get(1).getReplyToMessageId());
        assertEquals("算到一半\n\n（回答已停止）", inserted.get(1).getContentText());
        verify(orchestrator, never()).captureConversationMemory(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void stopBeforeAnyTextPreservesOriginalNoAssistantRowBehavior() {
        when(orchestrator.prepare(7L, session, "那n乘2是多少？", "test-model", previous)).thenReturn(prepared(true));
        doThrow(new AiStreamAbortedException("stopped")).when(chat).streamChat(anyString(), anyList(), anyString(), anyDouble(), any());
        service(Runnable::run).streamMessage(7L, 42L, request());
        assertEquals(1, inserted.size());
        verify(orchestrator, never()).captureConversationMemory(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void workflowFallbackRemainsSuccessfulTextAndUsesNoModel() {
        when(orchestrator.prepare(7L, session, "那n乘2是多少？", "test-model", previous)).thenReturn(prepared(false));
        service(Runnable::run).streamMessage(7L, 42L, request());
        assertEquals("TEXT", inserted.get(1).getMessageType());
        assertEquals("请确认使用哪份资料", inserted.get(1).getContentText());
        assertEquals(100L, inserted.get(1).getReplyToMessageId());
        verifyNoInteractions(chat);
        verify(orchestrator).captureConversationMemory(7L, session, "那n乘2是多少？", "请确认使用哪份资料");
    }

    @Test
    void synchronousAndPrepareExceptionsRetainTheirOriginalThrownContract() {
        when(orchestrator.respond(7L, session, "那n乘2是多少？", "test-model", previous)).thenThrow(new BusinessException("sync failed"));
        assertThrows(BusinessException.class, () -> service(Runnable::run).sendMessage(7L, 42L, request()));
        assertEquals(1, inserted.size());
        when(history.loadBefore(7L, 42L, 101L)).thenReturn(previous);
        when(orchestrator.prepare(7L, session, "那n乘2是多少？", "test-model", previous)).thenThrow(new BusinessException("prepare failed"));
        assertThrows(BusinessException.class, () -> service(Runnable::run).streamMessage(7L, 42L, request()));
        assertEquals(2, inserted.size());
        assertTrue(inserted.stream().allMatch(message -> "USER".equals(message.getRole())));
    }

    @Test
    void rejectedExecutorKeepsErrorEventPathWithoutAnAssistantSuccessRow() {
        when(orchestrator.prepare(7L, session, "那n乘2是多少？", "test-model", previous)).thenReturn(prepared(true));
        assertNotNull(service(command -> { throw new TaskRejectedException("busy"); }).streamMessage(7L, 42L, request()));
        assertEquals(1, inserted.size());
        verifyNoInteractions(chat);
        verify(orchestrator, never()).captureConversationMemory(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void visibleHistoryWithSameTimestampKeepsStableIdOrder() {
        AssistantMessage a = saved(3L), b = saved(1L), c = saved(2L);
        when(messages.selectList(any())).thenReturn(List.of(a, b, c));
        var detail = service(Runnable::run).getSessionDetail(7L, 42L, 30);
        assertEquals(List.of(1L, 2L, 3L), detail.getMessages().stream().map(message -> message.getId()).toList());
        @SuppressWarnings("unchecked") ArgumentCaptor<LambdaQueryWrapper<AssistantMessage>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messages).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("ORDER BY id DESC"));
    }

    private AssistantServiceImpl service(Executor executor) {
        return new AssistantServiceImpl(sessions, messages, tools, chat, orchestrator, history, new ObjectMapper(), executor);
    }

    private AssistantPreparedResult prepared(boolean useModel) {
        return new AssistantPreparedResult(useModel, "system", "结合资料回答：那n乘2是多少？", "请确认使用哪份资料",
                "{}", "{}", "test-model", List.of(), List.of(), withCurrent);
    }

    private AssistantMessageSendRequest request() {
        AssistantMessageSendRequest request = new AssistantMessageSendRequest();
        request.setContentText("  那n乘2是多少？  ");
        request.setModelName("test-model");
        return request;
    }

    private AssistantMessage saved(Long id) {
        AssistantMessage message = new AssistantMessage();
        message.setId(id);
        message.setSessionId(42L);
        message.setUserId(7L);
        message.setRole("ASSISTANT");
        message.setMessageType("TEXT");
        message.setContentText("test");
        message.setCreatedAt(LocalDateTime.of(2026, 10, 29, 12, 0));
        return message;
    }
}
