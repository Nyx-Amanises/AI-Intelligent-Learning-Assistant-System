package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.entity.AssistantMemory;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.mapper.AssistantMemoryMapper;
import com.aiassistant.learning.service.AssistantMemoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AssistantMemoryServiceImpl implements AssistantMemoryService {

    private final AssistantMemoryMapper assistantMemoryMapper;

    public AssistantMemoryServiceImpl(AssistantMemoryMapper assistantMemoryMapper) {
        this.assistantMemoryMapper = assistantMemoryMapper;
    }

    @Override
    public List<MemorySnippet> findRelevantMemories(Long userId, String queryText, Integer limit) {
        int resolvedLimit = limit == null || limit <= 0 ? 3 : Math.min(limit, 10);
        List<AssistantMemory> candidates = assistantMemoryMapper.selectList(new LambdaQueryWrapper<AssistantMemory>()
                .eq(AssistantMemory::getUserId, userId)
                .orderByDesc(AssistantMemory::getImportanceScore)
                .orderByDesc(AssistantMemory::getLastHitAt)
                .orderByDesc(AssistantMemory::getUpdatedAt)
                .last("limit 30"));
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<RankedMemory> rankedMemories = new ArrayList<>();
        for (AssistantMemory candidate : candidates) {
            rankedMemories.add(new RankedMemory(candidate, scoreMemory(candidate, queryText)));
        }

        List<AssistantMemory> selected = rankedMemories.stream()
                .sorted(Comparator.comparingDouble(RankedMemory::score).reversed()
                        .thenComparing(item -> item.memory().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .map(RankedMemory::memory)
                .limit(resolvedLimit)
                .toList();
        touchMemories(selected);
        return selected.stream()
                .map(memory -> new MemorySnippet(
                        memory.getId(),
                        memory.getMemoryScope(),
                        memory.getMemoryType(),
                        memory.getTopicName(),
                        memory.getSummaryText(),
                        memory.getContentText()
                ))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void captureConversationMemory(Long userId, AssistantSession session, String userMessage, String assistantReply) {
        captureProfileMemory(userId, userMessage);
        captureGoalMemory(userId, userMessage);
        capturePreferenceMemory(userId, userMessage);
        captureContextMemory(userId, session, assistantReply);
    }

    private void captureProfileMemory(Long userId, String userMessage) {
        if (StringUtils.hasText(userMessage) && userMessage.contains("我是")) {
            upsertMemory(
                    userId,
                    "PROFILE",
                    "IDENTITY",
                    "用户身份",
                    trimForStorage(userMessage, 400),
                    trimForStorage(userMessage, 100),
                    "ASSISTANT_CHAT",
                    null,
                    BigDecimal.valueOf(85),
                    BigDecimal.valueOf(70)
            );
        }
    }

    private void captureGoalMemory(Long userId, String userMessage) {
        if (containsAny(userMessage, List.of("我准备", "我要", "目标", "复习", "考试", "简历", "面试"))) {
            upsertMemory(
                    userId,
                    "GOAL",
                    "LEARNING_GOAL",
                    "学习目标",
                    trimForStorage(userMessage, 400),
                    trimForStorage(userMessage, 100),
                    "ASSISTANT_CHAT",
                    null,
                    BigDecimal.valueOf(88),
                    BigDecimal.valueOf(66)
            );
        }
    }

    private void capturePreferenceMemory(Long userId, String userMessage) {
        if (containsAny(userMessage, List.of("希望你", "不要", "直接", "详细一点", "简洁一点", "一步一步"))) {
            upsertMemory(
                    userId,
                    "PREFERENCE",
                    "RESPONSE_STYLE",
                    "答疑偏好",
                    trimForStorage(userMessage, 400),
                    trimForStorage(userMessage, 100),
                    "ASSISTANT_CHAT",
                    null,
                    BigDecimal.valueOf(72),
                    BigDecimal.valueOf(58)
            );
        }
    }

    private void captureContextMemory(Long userId, AssistantSession session, String assistantReply) {
        if (session == null) {
            return;
        }
        String summary = buildContextSummary(session);
        if (!StringUtils.hasText(summary)) {
            return;
        }
        upsertMemory(
                userId,
                "CONTEXT",
                "SESSION_CONTEXT",
                "最近会话上下文",
                trimForStorage(summary + "；最近一次助手响应：" + trimForStorage(assistantReply, 150), 600),
                trimForStorage(summary, 100),
                "ASSISTANT_SESSION",
                session.getId(),
                BigDecimal.valueOf(60),
                BigDecimal.valueOf(80)
        );
    }

    private void upsertMemory(
            Long userId,
            String memoryScope,
            String memoryType,
            String topicName,
            String contentText,
            String summaryText,
            String sourceType,
            Long sourceId,
            BigDecimal importanceScore,
            BigDecimal confidenceScore
    ) {
        AssistantMemory existing = assistantMemoryMapper.selectOne(new LambdaQueryWrapper<AssistantMemory>()
                .eq(AssistantMemory::getUserId, userId)
                .eq(AssistantMemory::getMemoryScope, memoryScope)
                .eq(AssistantMemory::getMemoryType, memoryType)
                .eq(AssistantMemory::getTopicName, topicName)
                .last("limit 1"));
        if (existing == null) {
            AssistantMemory memory = new AssistantMemory();
            memory.setUserId(userId);
            memory.setMemoryScope(memoryScope);
            memory.setMemoryType(memoryType);
            memory.setTopicName(topicName);
            memory.setContentText(contentText);
            memory.setSummaryText(summaryText);
            memory.setSourceType(sourceType);
            memory.setSourceId(sourceId);
            memory.setImportanceScore(importanceScore);
            memory.setConfidenceScore(confidenceScore);
            memory.setHitCount(0);
            memory.setVectorStatus("PENDING");
            assistantMemoryMapper.insert(memory);
            return;
        }

        existing.setContentText(contentText);
        existing.setSummaryText(summaryText);
        existing.setSourceType(sourceType);
        existing.setSourceId(sourceId);
        existing.setImportanceScore(importanceScore);
        existing.setConfidenceScore(confidenceScore);
        assistantMemoryMapper.updateById(existing);
    }

    private void touchMemories(List<AssistantMemory> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (AssistantMemory memory : memories) {
            memory.setHitCount((memory.getHitCount() == null ? 0 : memory.getHitCount()) + 1);
            memory.setLastHitAt(now);
            assistantMemoryMapper.updateById(memory);
        }
    }

    private double scoreMemory(AssistantMemory memory, String queryText) {
        if (memory == null) {
            return 0D;
        }
        String normalizedQuery = normalizeText(queryText);
        String normalizedMemory = normalizeText(memory.getTopicName() + " " + memory.getSummaryText() + " " + memory.getContentText());
        double score = memory.getImportanceScore() == null ? 0D : memory.getImportanceScore().doubleValue() / 100D;
        if (StringUtils.hasText(normalizedQuery) && normalizedMemory.contains(normalizedQuery)) {
            score += 2.2D;
        }
        for (String term : buildTerms(queryText)) {
            String normalizedTerm = normalizeText(term);
            if (StringUtils.hasText(normalizedTerm) && normalizedMemory.contains(normalizedTerm)) {
                score += Math.max(0.2D, Math.min(0.8D, normalizedTerm.length() * 0.12D));
            }
        }
        return score;
    }

    private List<String> buildTerms(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : text.split("[，。；：,.;:\\s()（）]+")) {
            String item = token.trim();
            if (item.length() >= 2) {
                terms.add(item);
            }
        }
        String normalized = normalizeText(text);
        if (normalized.length() >= 4 && normalized.length() <= 12) {
            for (int i = 0; i <= normalized.length() - 2; i++) {
                String gram = normalized.substring(i, Math.min(normalized.length(), i + 3));
                if (gram.length() >= 2) {
                    terms.add(gram);
                }
            }
        }
        return terms.stream().limit(10).toList();
    }

    private String buildContextSummary(AssistantSession session) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(session.getCurrentContextType())) {
            parts.add("当前上下文类型为 " + session.getCurrentContextType());
        }
        if (session.getCurrentMaterialId() != null) {
            parts.add("关联资料ID=" + session.getCurrentMaterialId());
        }
        if (session.getCurrentQuestionSetId() != null) {
            parts.add("关联题集ID=" + session.getCurrentQuestionSetId());
        }
        if (session.getCurrentPracticeSessionId() != null) {
            parts.add("关联练习记录ID=" + session.getCurrentPracticeSessionId());
        }
        return parts.isEmpty() ? null : String.join("，", parts);
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String trimForStorage(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？；：、“”‘’（）()【】《》<>·—…-]+", "");
    }

    private record RankedMemory(AssistantMemory memory, double score) {
    }
}
