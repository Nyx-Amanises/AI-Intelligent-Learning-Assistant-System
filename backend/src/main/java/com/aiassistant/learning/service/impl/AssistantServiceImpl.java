package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.assistant.AssistantMessageSendRequest;
import com.aiassistant.learning.dto.assistant.AssistantSessionCreateRequest;
import com.aiassistant.learning.entity.AssistantMessage;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.entity.AssistantToolCall;
import com.aiassistant.learning.mapper.AssistantMessageMapper;
import com.aiassistant.learning.mapper.AssistantSessionMapper;
import com.aiassistant.learning.mapper.AssistantToolCallMapper;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AssistantService;
import com.aiassistant.learning.service.assistant.AssistantAgentOrchestrator;
import com.aiassistant.learning.service.assistant.AssistantTool;
import com.aiassistant.learning.vo.assistant.AssistantChatReplyVO;
import com.aiassistant.learning.vo.assistant.AssistantMessageVO;
import com.aiassistant.learning.vo.assistant.AssistantRelevantMemoryVO;
import com.aiassistant.learning.vo.assistant.AssistantSessionDetailVO;
import com.aiassistant.learning.vo.assistant.AssistantSessionPageVO;
import com.aiassistant.learning.vo.assistant.AssistantToolCallVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AssistantServiceImpl implements AssistantService {

    private static final String DEFAULT_SESSION_TITLE = "新对话";

    private final AssistantSessionMapper assistantSessionMapper;
    private final AssistantMessageMapper assistantMessageMapper;
    private final AssistantToolCallMapper assistantToolCallMapper;
    private final AiChatService aiChatService;
    private final AssistantAgentOrchestrator assistantAgentOrchestrator;
    private final ObjectMapper objectMapper;
    private final ExecutorService assistantStreamExecutor = Executors.newCachedThreadPool(new AssistantStreamThreadFactory());

    public AssistantServiceImpl(
            AssistantSessionMapper assistantSessionMapper,
            AssistantMessageMapper assistantMessageMapper,
            AssistantToolCallMapper assistantToolCallMapper,
            AiChatService aiChatService,
            AssistantAgentOrchestrator assistantAgentOrchestrator,
            ObjectMapper objectMapper
    ) {
        this.assistantSessionMapper = assistantSessionMapper;
        this.assistantMessageMapper = assistantMessageMapper;
        this.assistantToolCallMapper = assistantToolCallMapper;
        this.aiChatService = aiChatService;
        this.assistantAgentOrchestrator = assistantAgentOrchestrator;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdownAssistantStreamExecutor() {
        assistantStreamExecutor.shutdownNow();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssistantSessionDetailVO createSession(Long userId, AssistantSessionCreateRequest request) {
        AssistantSession session = new AssistantSession();
        session.setUserId(userId);
        session.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : DEFAULT_SESSION_TITLE);
        session.setStatus("ACTIVE");
        session.setPinned(0);
        applyContext(session, request.getContextType(), request.getContextId(), request.getMaterialId(),
                request.getQuestionSetId(), request.getPracticeSessionId());
        assistantSessionMapper.insert(session);
        return buildSessionDetail(session, List.of(), List.of());
    }

    @Override
    public PageVO<AssistantSessionPageVO> pageSessions(Long userId, Long current, Long size) {
        Page<AssistantSession> page = assistantSessionMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<AssistantSession>()
                        .eq(AssistantSession::getUserId, userId)
                        .orderByDesc(AssistantSession::getPinned)
                        .orderByDesc(AssistantSession::getLastMessageAt)
                        .orderByDesc(AssistantSession::getCreatedAt)
        );
        Map<Long, AssistantMessage> latestMessageMap = loadLatestMessages(page.getRecords().stream()
                .map(AssistantSession::getId)
                .toList());
        List<AssistantSessionPageVO> records = page.getRecords().stream()
                .map(session -> {
                    AssistantMessage latestMessage = latestMessageMap.get(session.getId());
                    return AssistantSessionPageVO.builder()
                            .id(session.getId())
                            .title(session.getTitle())
                            .status(session.getStatus())
                            .pinned(session.getPinned())
                            .currentContextType(session.getCurrentContextType())
                            .currentContextId(session.getCurrentContextId())
                            .lastMessagePreview(latestMessage == null ? null : abbreviate(latestMessage.getContentText(), 80))
                            .lastMessageAt(session.getLastMessageAt())
                            .createdAt(session.getCreatedAt())
                            .build();
                })
                .toList();
        return PageVO.<AssistantSessionPageVO>builder()
                .current(page.getCurrent())
                .size(page.getSize())
                .total(page.getTotal())
                .pages(page.getPages())
                .records(records)
                .build();
    }

    @Override
    public AssistantSessionDetailVO getSessionDetail(Long userId, Long sessionId, Integer messageLimit) {
        AssistantSession session = getOwnedSession(userId, sessionId);
        List<AssistantMessage> messages = loadMessages(session.getId(), messageLimit);
        List<AssistantToolCall> toolCalls = loadRecentToolCalls(session.getId(), 20);
        return buildSessionDetail(session, messages, toolCalls);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssistantChatReplyVO sendMessage(Long userId, Long sessionId, AssistantMessageSendRequest request) {
        AssistantSession session = getOwnedSession(userId, sessionId);
        AssistantMessage userMessage = persistUserMessage(userId, session, request);

        AssistantAgentOrchestrator.AssistantAgentResult agentResult = assistantAgentOrchestrator.respond(
                userId,
                session,
                request.getContentText().trim(),
                request.getModelName()
        );

        AssistantMessage assistantMessage = new AssistantMessage();
        assistantMessage.setSessionId(session.getId());
        assistantMessage.setUserId(userId);
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setMessageType("TEXT");
        assistantMessage.setContentText(agentResult.assistantReply());
        assistantMessage.setReasoningJson(agentResult.reasoningJson());
        assistantMessage.setToolPlanJson(agentResult.toolPlanJson());
        assistantMessage.setModelName(agentResult.modelName());
        assistantMessage.setTokenInput(estimateTokens(request.getContentText()));
        assistantMessage.setTokenOutput(estimateTokens(agentResult.assistantReply()));
        assistantMessageMapper.insert(assistantMessage);

        List<AssistantToolCall> toolCalls = saveToolCalls(session.getId(), userMessage.getId(), agentResult.toolExecutions());
        syncSessionContextFromToolCalls(session, agentResult.toolExecutions());
        session.setLastMessageAt(LocalDateTime.now());
        assistantSessionMapper.updateById(session);

        return buildChatReplyVO(session, userMessage, assistantMessage, toolCalls, agentResult.usedMemories());
    }

    @Override
    public SseEmitter streamMessage(Long userId, Long sessionId, AssistantMessageSendRequest request) {
        AssistantSession session = getOwnedSession(userId, sessionId);
        AssistantMessage userMessage = persistUserMessage(userId, session, request);
        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult = assistantAgentOrchestrator.prepare(
                userId,
                session,
                request.getContentText().trim(),
                request.getModelName()
        );

        SseEmitter emitter = new SseEmitter(300000L);
        assistantStreamExecutor.execute(() -> streamAssistantReply(
                emitter,
                userId,
                session,
                userMessage,
                request,
                preparedResult
        ));
        return emitter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, Long sessionId) {
        AssistantSession session = getOwnedSession(userId, sessionId);
        assistantToolCallMapper.delete(new LambdaQueryWrapper<AssistantToolCall>()
                .eq(AssistantToolCall::getSessionId, session.getId()));
        assistantMessageMapper.delete(new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getSessionId, session.getId()));
        assistantSessionMapper.deleteById(session.getId());
    }

    private AssistantMessage persistUserMessage(Long userId, AssistantSession session, AssistantMessageSendRequest request) {
        applyContext(session, request.getContextType(), request.getContextId(), request.getMaterialId(),
                request.getQuestionSetId(), request.getPracticeSessionId());

        String contentText = request.getContentText().trim();
        AssistantMessage userMessage = new AssistantMessage();
        userMessage.setSessionId(session.getId());
        userMessage.setUserId(userId);
        userMessage.setRole("USER");
        userMessage.setMessageType("TEXT");
        userMessage.setContentText(contentText);
        userMessage.setTokenInput(estimateTokens(contentText));
        assistantMessageMapper.insert(userMessage);

        if (!StringUtils.hasText(session.getTitle()) || DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
            session.setTitle(buildSessionTitle(contentText));
        }
        session.setLastMessageAt(LocalDateTime.now());
        assistantSessionMapper.updateById(session);
        return userMessage;
    }

    private void streamAssistantReply(
            SseEmitter emitter,
            Long userId,
            AssistantSession session,
            AssistantMessage userMessage,
            AssistantMessageSendRequest request,
            AssistantAgentOrchestrator.AssistantPreparedResult preparedResult
    ) {
        try {
            sendStreamEvent(emitter, buildSessionEventPayload(session, userMessage));
            sendStreamEvent(emitter, buildTraceEventPayload(preparedResult));

            StringBuilder assistantReplyBuilder = new StringBuilder();
            if (preparedResult.useModel()) {
                aiChatService.streamChat(
                        preparedResult.systemPrompt(),
                        preparedResult.userPrompt(),
                        preparedResult.modelName(),
                        0.3,
                        delta -> appendAssistantDelta(emitter, assistantReplyBuilder, delta)
                );
            } else {
                emitFallbackReply(emitter, assistantReplyBuilder, preparedResult.fallbackReply());
            }

            String assistantReply = assistantReplyBuilder.toString().trim();
            if (!StringUtils.hasText(assistantReply)) {
                assistantReply = preparedResult.fallbackReply();
            }
            if (!StringUtils.hasText(assistantReply)) {
                throw new BusinessException("AI 返回内容为空");
            }

            AssistantChatReplyVO replyVO = persistStreamReply(
                    userId,
                    session,
                    userMessage,
                    request,
                    preparedResult,
                    assistantReply
            );
            sendStreamEvent(emitter, Map.of("type", "done", "reply", replyVO));
            emitter.complete();
        } catch (Exception exception) {
            sendStreamError(emitter, resolveStreamErrorMessage(exception));
        }
    }

    private void appendAssistantDelta(SseEmitter emitter, StringBuilder assistantReplyBuilder, String delta) {
        if (!StringUtils.hasText(delta)) {
            return;
        }
        assistantReplyBuilder.append(delta);
        sendStreamEvent(emitter, Map.of("type", "delta", "delta", delta));
    }

    private void emitFallbackReply(SseEmitter emitter, StringBuilder assistantReplyBuilder, String fallbackReply) {
        if (!StringUtils.hasText(fallbackReply)) {
            return;
        }
        int start = 0;
        while (start < fallbackReply.length()) {
            int end = Math.min(fallbackReply.length(), start + 24);
            String delta = fallbackReply.substring(start, end);
            assistantReplyBuilder.append(delta);
            sendStreamEvent(emitter, Map.of("type", "delta", "delta", delta));
            start = end;
        }
    }

    private AssistantChatReplyVO persistStreamReply(
            Long userId,
            AssistantSession session,
            AssistantMessage userMessage,
            AssistantMessageSendRequest request,
            AssistantAgentOrchestrator.AssistantPreparedResult preparedResult,
            String assistantReply
    ) {
        AssistantMessage assistantMessage = new AssistantMessage();
        assistantMessage.setSessionId(session.getId());
        assistantMessage.setUserId(userId);
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setMessageType("TEXT");
        assistantMessage.setContentText(assistantReply);
        assistantMessage.setReasoningJson(preparedResult.reasoningJson());
        assistantMessage.setToolPlanJson(preparedResult.toolPlanJson());
        assistantMessage.setModelName(preparedResult.modelName());
        assistantMessage.setTokenInput(estimateTokens(request.getContentText()));
        assistantMessage.setTokenOutput(estimateTokens(assistantReply));
        assistantMessageMapper.insert(assistantMessage);

        List<AssistantToolCall> toolCalls = saveToolCalls(session.getId(), userMessage.getId(), preparedResult.toolExecutions());
        syncSessionContextFromToolCalls(session, preparedResult.toolExecutions());
        session.setLastMessageAt(LocalDateTime.now());
        assistantSessionMapper.updateById(session);
        assistantAgentOrchestrator.captureConversationMemory(userId, session, request.getContentText().trim(), assistantReply);

        return buildChatReplyVO(session, userMessage, assistantMessage, toolCalls, preparedResult.usedMemories());
    }

    private Map<String, Object> buildSessionEventPayload(AssistantSession session, AssistantMessage userMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "session");
        payload.put("sessionId", session.getId());
        payload.put("sessionTitle", session.getTitle());
        payload.put("userMessage", toMessageVO(userMessage));
        return payload;
    }

    private Map<String, Object> buildTraceEventPayload(AssistantAgentOrchestrator.AssistantPreparedResult preparedResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "trace");
        payload.put("modelName", preparedResult.modelName());
        payload.put("reasoningJson", preparedResult.reasoningJson());
        payload.put("toolPlanJson", preparedResult.toolPlanJson());
        payload.put("toolCalls", preparedResult.toolExecutions().stream().map(this::toPreviewToolCallVO).toList());
        payload.put("usedMemories", toRelevantMemoryVOList(preparedResult.usedMemories()));
        return payload;
    }

    private void sendStreamEvent(SseEmitter emitter, Object payload) {
        try {
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
        } catch (Exception exception) {
            throw new BusinessException(500, "SSE 推送失败: " + resolveStreamErrorMessage(exception));
        }
    }

    private void sendStreamError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "error", "message", message), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception ignored) {
            emitter.completeWithError(new RuntimeException(message));
        }
    }

    private String resolveStreamErrorMessage(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        if (throwable != null && StringUtils.hasText(throwable.getMessage())) {
            return throwable.getMessage().trim();
        }
        return "助手流式生成失败";
    }

    private AssistantSession getOwnedSession(Long userId, Long sessionId) {
        AssistantSession session = assistantSessionMapper.selectOne(new LambdaQueryWrapper<AssistantSession>()
                .eq(AssistantSession::getId, sessionId)
                .eq(AssistantSession::getUserId, userId)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        return session;
    }

    private void applyContext(
            AssistantSession session,
            String contextType,
            Long contextId,
            Long materialId,
            Long questionSetId,
            Long practiceSessionId
    ) {
        if (materialId != null) {
            session.setCurrentMaterialId(materialId);
            session.setCurrentContextType("MATERIAL");
            session.setCurrentContextId(materialId);
        }
        if (questionSetId != null) {
            session.setCurrentQuestionSetId(questionSetId);
            session.setCurrentContextType("QUESTION_SET");
            session.setCurrentContextId(questionSetId);
        }
        if (practiceSessionId != null) {
            session.setCurrentPracticeSessionId(practiceSessionId);
            session.setCurrentContextType("PRACTICE_SESSION");
            session.setCurrentContextId(practiceSessionId);
        }
        if (StringUtils.hasText(contextType)) {
            session.setCurrentContextType(contextType.trim().toUpperCase());
        }
        if (contextId != null) {
            session.setCurrentContextId(contextId);
        }
    }

    private List<AssistantMessage> loadMessages(Long sessionId, Integer messageLimit) {
        int limit = messageLimit == null || messageLimit <= 0 ? 30 : Math.min(messageLimit, 100);
        List<AssistantMessage> messages = assistantMessageMapper.selectList(new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getSessionId, sessionId)
                .orderByDesc(AssistantMessage::getCreatedAt)
                .last("limit " + limit));
        return messages.stream()
                .sorted(Comparator.comparing(AssistantMessage::getCreatedAt))
                .toList();
    }

    private List<AssistantToolCall> loadRecentToolCalls(Long sessionId, int limit) {
        return assistantToolCallMapper.selectList(new LambdaQueryWrapper<AssistantToolCall>()
                .eq(AssistantToolCall::getSessionId, sessionId)
                .orderByDesc(AssistantToolCall::getCreatedAt)
                .last("limit " + Math.max(1, limit)))
                .stream()
                .sorted(Comparator.comparing(AssistantToolCall::getCreatedAt))
                .toList();
    }

    private Map<Long, AssistantMessage> loadLatestMessages(List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        List<AssistantMessage> messages = assistantMessageMapper.selectList(new LambdaQueryWrapper<AssistantMessage>()
                .in(AssistantMessage::getSessionId, sessionIds)
                .orderByDesc(AssistantMessage::getCreatedAt));
        Map<Long, AssistantMessage> latestMessageMap = new LinkedHashMap<>();
        for (AssistantMessage message : messages) {
            latestMessageMap.putIfAbsent(message.getSessionId(), message);
        }
        return latestMessageMap;
    }

    private AssistantSessionDetailVO buildSessionDetail(
            AssistantSession session,
            List<AssistantMessage> messages,
            List<AssistantToolCall> toolCalls
    ) {
        return AssistantSessionDetailVO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .status(session.getStatus())
                .pinned(session.getPinned())
                .currentContextType(session.getCurrentContextType())
                .currentContextId(session.getCurrentContextId())
                .currentMaterialId(session.getCurrentMaterialId())
                .currentQuestionSetId(session.getCurrentQuestionSetId())
                .currentPracticeSessionId(session.getCurrentPracticeSessionId())
                .lastMessageAt(session.getLastMessageAt())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .messages(messages.stream().map(this::toMessageVO).toList())
                .recentToolCalls(toolCalls.stream().map(this::toToolCallVO).toList())
                .build();
    }

    private AssistantMessageVO toMessageVO(AssistantMessage message) {
        return AssistantMessageVO.builder()
                .id(message.getId())
                .role(message.getRole())
                .messageType(message.getMessageType())
                .contentText(message.getContentText())
                .reasoningJson(message.getReasoningJson())
                .toolPlanJson(message.getToolPlanJson())
                .modelName(message.getModelName())
                .tokenInput(message.getTokenInput())
                .tokenOutput(message.getTokenOutput())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private AssistantToolCallVO toToolCallVO(AssistantToolCall toolCall) {
        return AssistantToolCallVO.builder()
                .id(toolCall.getId())
                .messageId(toolCall.getMessageId())
                .toolName(toolCall.getToolName())
                .toolArgsJson(toolCall.getToolArgsJson())
                .toolResultJson(toolCall.getToolResultJson())
                .status(toolCall.getStatus())
                .errorMessage(toolCall.getErrorMessage())
                .startedAt(toolCall.getStartedAt())
                .finishedAt(toolCall.getFinishedAt())
                .createdAt(toolCall.getCreatedAt())
                .build();
    }

    private AssistantToolCallVO toPreviewToolCallVO(AssistantTool.ToolExecutionResult execution) {
        return AssistantToolCallVO.builder()
                .toolName(execution.toolName())
                .toolArgsJson(execution.toolArgsJson())
                .toolResultJson(execution.toolResultJson())
                .status(execution.status())
                .errorMessage(execution.errorMessage())
                .startedAt(execution.startedAt())
                .finishedAt(execution.finishedAt())
                .build();
    }

    private List<AssistantRelevantMemoryVO> toRelevantMemoryVOList(List<AssistantAgentOrchestrator.MemoryUsage> memoryUsages) {
        if (memoryUsages == null || memoryUsages.isEmpty()) {
            return List.of();
        }
        return memoryUsages.stream()
                .map(memory -> AssistantRelevantMemoryVO.builder()
                        .id(memory.id())
                        .memoryScope(memory.memoryScope())
                        .memoryType(memory.memoryType())
                        .topicName(memory.topicName())
                        .summaryText(memory.summaryText())
                        .build())
                .toList();
    }

    private AssistantChatReplyVO buildChatReplyVO(
            AssistantSession session,
            AssistantMessage userMessage,
            AssistantMessage assistantMessage,
            List<AssistantToolCall> toolCalls,
            List<AssistantAgentOrchestrator.MemoryUsage> usedMemories
    ) {
        return AssistantChatReplyVO.builder()
                .sessionId(session.getId())
                .sessionTitle(session.getTitle())
                .userMessage(toMessageVO(userMessage))
                .assistantMessage(toMessageVO(assistantMessage))
                .toolCalls(toolCalls.stream().map(this::toToolCallVO).toList())
                .usedMemories(toRelevantMemoryVOList(usedMemories))
                .build();
    }

    private List<AssistantToolCall> saveToolCalls(
            Long sessionId,
            Long triggerMessageId,
            List<AssistantTool.ToolExecutionResult> executions
    ) {
        if (executions == null || executions.isEmpty()) {
            return List.of();
        }
        List<AssistantToolCall> toolCalls = new ArrayList<>();
        for (AssistantTool.ToolExecutionResult execution : executions) {
            AssistantToolCall toolCall = new AssistantToolCall();
            toolCall.setSessionId(sessionId);
            toolCall.setMessageId(triggerMessageId);
            toolCall.setToolName(execution.toolName());
            toolCall.setToolArgsJson(execution.toolArgsJson());
            toolCall.setToolResultJson(execution.toolResultJson());
            toolCall.setStatus(execution.status());
            toolCall.setErrorMessage(execution.errorMessage());
            toolCall.setStartedAt(execution.startedAt());
            toolCall.setFinishedAt(execution.finishedAt());
            assistantToolCallMapper.insert(toolCall);
            toolCalls.add(toolCall);
        }
        return toolCalls;
    }

    private void syncSessionContextFromToolCalls(
            AssistantSession session,
            List<AssistantTool.ToolExecutionResult> executions
    ) {
        if (session == null || executions == null || executions.isEmpty()) {
            return;
        }
        for (AssistantTool.ToolExecutionResult execution : executions) {
            if (!"SUCCESS".equalsIgnoreCase(execution.status())
                    || !StringUtils.hasText(execution.toolName())
                    || !execution.toolName().startsWith("task.submit_")
                    || !StringUtils.hasText(execution.toolResultJson())) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(execution.toolResultJson());
                if (root.has("id") && root.get("id").canConvertToLong()) {
                    session.setCurrentContextType("AI_TASK");
                    session.setCurrentContextId(root.get("id").asLong());
                }
            } catch (Exception ignored) {
                return;
            }
        }
    }

    private int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, text.trim().length() / 4);
    }

    private String buildSessionTitle(String firstMessage) {
        if (!StringUtils.hasText(firstMessage)) {
            return DEFAULT_SESSION_TITLE;
        }
        String normalized = firstMessage.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 20 ? normalized : normalized.substring(0, 20);
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private static final class AssistantStreamThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "assistant-stream-" + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
