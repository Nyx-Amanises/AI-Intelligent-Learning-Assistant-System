package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantMessage;
import com.aiassistant.learning.mapper.AssistantMessageMapper;
import com.aiassistant.learning.service.AiChatService.ChatMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 从已持久化消息构建有限、归属明确的完整对话轮，避免把失败回复和异步错配传入模型。 */
@Component
public class AssistantConversationHistory {
    static final int ROW_LIMIT = 100;
    /** 多取一条前驱哨兵，仅用于判断 100 行窗口是否切在连续 USER 组内部。 */
    static final int SCAN_LIMIT = ROW_LIMIT + 1;
    static final int TURN_LIMIT = 10;
    static final int CHARACTER_BUDGET = 24000;
    static final int MESSAGE_CHARACTER_LIMIT = 6000;
    static final String TRUNCATED_MARKER = "\n[历史消息已截断]";

    private final AssistantMessageMapper messages;

    public AssistantConversationHistory(AssistantMessageMapper messages) {
        this.messages = messages;
    }

    public List<ChatMessage> loadBefore(Long userId, Long sessionId, Long currentUserMessageId) {
        if (userId == null || sessionId == null || currentUserMessageId == null) return List.of();
        List<AssistantMessage> selected = messages.selectList(new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getUserId, userId)
                .eq(AssistantMessage::getSessionId, sessionId)
                .lt(AssistantMessage::getId, currentUserMessageId)
                .orderByDesc(AssistantMessage::getId)
                .last("limit " + SCAN_LIMIT));
        if (selected == null || selected.isEmpty()) return List.of();
        List<AssistantMessage> scanned = selected.stream()
                .filter(Objects::nonNull)
                .filter(message -> Objects.equals(message.getUserId(), userId)
                        && Objects.equals(message.getSessionId(), sessionId)
                        && message.getId() != null && message.getId() < currentUserMessageId)
                .sorted(Comparator.comparing(AssistantMessage::getId).reversed())
                .limit(SCAN_LIMIT)
                .toList();
        AssistantMessage predecessor = scanned.size() > ROW_LIMIT ? scanned.get(ROW_LIMIT) : null;
        List<AssistantMessage> rows = scanned.stream().limit(ROW_LIMIT)
                .sorted(Comparator.comparing(AssistantMessage::getId)).toList();
        Map<Long, AssistantMessage> users = rows.stream()
                .filter(message -> isSuccessful(message, "USER"))
                .collect(Collectors.toMap(AssistantMessage::getId, Function.identity(), (first, ignored) -> first));
        Set<Long> pairedUsers = new HashSet<>();
        List<Turn> turns = new ArrayList<>();

        // 新消息通过显式父消息 ID 配对，允许不同轮的回复交错完成。重复写回仅保留最新成功回复。
        for (int index = rows.size() - 1; index >= 0; index--) {
            AssistantMessage reply = rows.get(index);
            if (!isSuccessful(reply, "ASSISTANT") || reply.getReplyToMessageId() == null) continue;
            AssistantMessage user = users.get(reply.getReplyToMessageId());
            if (user != null && user.getId() < reply.getId() && pairedUsers.add(user.getId())) {
                turns.add(turn(user, reply));
            }
        }

        // 旧数据只接纳无歧义的相邻 U/A。出现连续 U 后，后续无引用的回复可能仍属较早轮，不能靠邻接恢复。
        // 首条 USER 的前驱同样是 USER 时说明切在歧义组内；正常 A/U 边界仍可恢复长会话最近的完整轮。
        boolean legacyAmbiguous = predecessor != null && "USER".equalsIgnoreCase(predecessor.getRole()) && !rows.isEmpty()
                && "USER".equalsIgnoreCase(rows.get(0).getRole());
        for (int index = 0; index + 1 < rows.size(); index++) {
            AssistantMessage user = rows.get(index);
            AssistantMessage reply = rows.get(index + 1);
            if ("USER".equalsIgnoreCase(user.getRole()) && "USER".equalsIgnoreCase(reply.getRole())) {
                legacyAmbiguous = true;
            }
            if (legacyAmbiguous) continue;
            if (!isSuccessful(user, "USER") || !isSuccessful(reply, "ASSISTANT")
                    || reply.getReplyToMessageId() != null || pairedUsers.contains(user.getId())) continue;
            pairedUsers.add(user.getId());
            turns.add(turn(user, reply));
        }

        turns.sort(Comparator.comparingLong(Turn::completedId).reversed());
        List<Turn> retained = new ArrayList<>();
        int remaining = CHARACTER_BUDGET;
        for (Turn turn : turns) {
            if (retained.size() == TURN_LIMIT) break;
            int size = turn.user().content().length() + turn.assistant().content().length();
            if (size > remaining) break;
            retained.add(turn);
            remaining -= size;
        }
        retained.sort(Comparator.comparingLong(Turn::userId));
        return retained.stream().flatMap(turn -> java.util.stream.Stream.of(turn.user(), turn.assistant())).toList();
    }

    private boolean isSuccessful(AssistantMessage message, String role) {
        return role.equalsIgnoreCase(message.getRole())
                && "TEXT".equalsIgnoreCase(message.getMessageType())
                && StringUtils.hasText(message.getContentText())
                && !("ASSISTANT".equals(role) && isLegacyFailure(message));
    }

    /** 升级前的失败/停止说明也是 TEXT；仅识别原系统固定标记，保留原文用于界面回看。 */
    private boolean isLegacyFailure(AssistantMessage message) {
        if (message.getReplyToMessageId() != null) return false;
        String content = message.getContentText().trim();
        return content.startsWith("这次调用失败了：") || content.endsWith("\n\n（回答已停止）");
    }

    private Turn turn(AssistantMessage user, AssistantMessage reply) {
        return new Turn(user.getId(), reply.getId(), new ChatMessage("user", bounded(user.getContentText())),
                new ChatMessage("assistant", bounded(reply.getContentText())));
    }

    private String bounded(String text) {
        String trimmed = text.trim();
        if (trimmed.length() <= MESSAGE_CHARACTER_LIMIT) return trimmed;
        int end = MESSAGE_CHARACTER_LIMIT - TRUNCATED_MARKER.length();
        if (Character.isHighSurrogate(trimmed.charAt(end - 1)) && Character.isLowSurrogate(trimmed.charAt(end))) end--;
        return trimmed.substring(0, end) + TRUNCATED_MARKER;
    }

    private record Turn(long userId, long completedId, ChatMessage user, ChatMessage assistant) { }
}
