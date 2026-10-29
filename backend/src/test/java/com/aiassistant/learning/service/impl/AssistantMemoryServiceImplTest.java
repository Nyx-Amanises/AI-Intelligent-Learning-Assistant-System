package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.entity.AssistantMemory;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.mapper.AssistantMemoryMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AssistantMemoryServiceImplTest {
    private final AssistantMemoryMapper mapper = mock(AssistantMemoryMapper.class);
    private final AssistantMemoryServiceImpl service = new AssistantMemoryServiceImpl(mapper);

    @BeforeAll
    static void metadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "assistant-memory-scope-test");
        TableInfoHelper.initTableInfo(assistant, AssistantMemory.class);
    }

    @Test
    void scopedRetrievalIncludesPersonalMemoriesAndOnlyTheCurrentSessionsContext() {
        AssistantMemory profile = memory(1L, 7L, "PROFILE", null, "共享身份");
        AssistantMemory goal = memory(2L, 7L, "GOAL", null, "共享目标");
        AssistantMemory preference = memory(3L, 7L, "PREFERENCE", null, "共享偏好");
        AssistantMemory context = memory(4L, 7L, "CONTEXT", 42L, "当前会话");
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<AssistantMemory> query = invocation.getArgument(0);
            assertEquals(7L, equality(query, "user_id"));
            assertEquals(42L, equality(query, "source_id"));
            assertEquals("ASSISTANT_SESSION", equality(query, "source_type"));
            // The user restriction must enclose both scope branches, and scope filtering precedes LIMIT.
            assertTrue(normalizedSql(query).startsWith(
                    "(user_id=?AND(memory_scopeIN(?,?,?)OR(memory_scope=?ANDsource_type=?ANDsource_id=?)))"));
            assertTrue(normalizedSql(query).endsWith("ORDERBYimportance_scoreDESC,last_hit_atDESC,updated_atDESClimit30"));
            assertTrue(query.getParamNameValuePairs().values().containsAll(List.of("PROFILE", "GOAL", "PREFERENCE", "CONTEXT")));
            return List.of(profile, goal, preference, context);
        });

        var snippets = service.findRelevantMemories(7L, 42L, "当前会话", 10);

        assertEquals(Set.of(1L, 2L, 3L, 4L), snippets.stream().map(item -> item.id()).collect(Collectors.toSet()));
        assertEquals(4L, snippets.get(0).id(), "当前问题的相关性排序仍然生效");
        for (AssistantMemory selected : List.of(profile, goal, preference, context)) {
            assertEquals(1, selected.getHitCount());
            verify(mapper).updateById(selected);
        }
    }

    @Test
    void legacyRetrievalWithoutSessionCannotReadAnyConversationContext() {
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<AssistantMemory> query = invocation.getArgument(0);
            assertEquals(7L, equality(query, "user_id"));
            assertTrue(normalizedSql(query).startsWith("(user_id=?AND(memory_scopeIN(?,?,?)))"));
            assertFalse(query.getParamNameValuePairs().containsValue("CONTEXT"));
            assertNull(equality(query, "source_id"));
            return List.of(memory(1L, 7L, "PREFERENCE", null, "简洁回答"));
        });

        var snippets = service.findRelevantMemories(7L, "简洁", 3);

        assertEquals(1, snippets.size());
        assertEquals("PREFERENCE", snippets.get(0).memoryScope());
    }

    @Test
    void unrelatedSessionsDoNotReplaceEachOthersContextButPersonalMemoryRemainsShared() {
        List<AssistantMemory> stored = installMemoryStore();
        AssistantSession first = session(42L, 7L, 100L);
        AssistantSession second = session(43L, 7L, 200L);

        service.captureConversationMemory(7L, first, "我是学生", "第一份资料回答");
        service.captureConversationMemory(7L, second, "我是研究生", "第二份资料回答");
        service.captureConversationMemory(7L, first, "继续", "第一份资料后续回答");

        assertEquals(3, stored.size());
        List<AssistantMemory> contexts = stored.stream().filter(item -> "CONTEXT".equals(item.getMemoryScope())).toList();
        assertEquals(2, contexts.size());
        AssistantMemory firstContext = contexts.stream().filter(item -> Long.valueOf(42L).equals(item.getSourceId())).findFirst().orElseThrow();
        AssistantMemory secondContext = contexts.stream().filter(item -> Long.valueOf(43L).equals(item.getSourceId())).findFirst().orElseThrow();
        assertTrue(firstContext.getContentText().contains("第一份资料后续回答"));
        assertFalse(firstContext.getContentText().contains("第二份资料"));
        assertTrue(secondContext.getContentText().contains("第二份资料回答"));
        AssistantMemory profile = stored.stream().filter(item -> "PROFILE".equals(item.getMemoryScope())).findFirst().orElseThrow();
        assertEquals("我是研究生", profile.getContentText());
        assertNull(profile.getSourceId());
    }

    @Test
    void contextUpsertDoesNotReuseAnotherUsersOrAnotherSourceTypesRecord() {
        List<AssistantMemory> stored = installMemoryStore();
        stored.add(memory(1L, 99L, "CONTEXT", 42L, "其他用户"));
        AssistantMemory imported = memory(2L, 7L, "CONTEXT", 42L, "其他来源");
        imported.setSourceType("IMPORTED_DOCUMENT");
        stored.add(imported);

        service.captureConversationMemory(7L, session(42L, 7L, 100L), "继续", "本用户本会话");

        assertEquals(3, stored.size());
        assertEquals("其他用户", stored.get(0).getContentText());
        assertEquals("其他来源", stored.get(1).getContentText());
        assertEquals(7L, stored.get(2).getUserId());
        assertEquals("ASSISTANT_SESSION", stored.get(2).getSourceType());
        assertTrue(stored.get(2).getContentText().contains("本用户本会话"));
    }

    @Test
    void contextWithoutPersistedSessionIdIsNotSaved() {
        service.captureConversationMemory(7L, session(null, 7L, 100L), "继续", "没有归属的上下文");
        verifyNoInteractions(mapper);
    }

    @Test
    void noCandidatesMeansNoMemoryTouchWrites() {
        when(mapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.findRelevantMemories(7L, 42L, "继续", 3).isEmpty());
        verify(mapper, never()).updateById(any(AssistantMemory.class));
    }

    private List<AssistantMemory> installMemoryStore() {
        List<AssistantMemory> stored = new ArrayList<>();
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<AssistantMemory> query = invocation.getArgument(0);
            Object user = equality(query, "user_id");
            Object scope = equality(query, "memory_scope");
            Object type = equality(query, "memory_type");
            Object topic = equality(query, "topic_name");
            Object sourceType = equality(query, "source_type");
            Object sourceId = equality(query, "source_id");
            if ("CONTEXT".equals(scope)) {
                assertEquals("ASSISTANT_SESSION", sourceType);
                assertTrue(sourceId instanceof Long);
            } else {
                assertNull(sourceType, "个人记忆继续按用户共享");
                assertNull(sourceId);
            }
            return stored.stream().filter(item -> user.equals(item.getUserId())
                    && scope.equals(item.getMemoryScope()) && type.equals(item.getMemoryType()) && topic.equals(item.getTopicName()))
                    .filter(item -> sourceType == null || sourceType.equals(item.getSourceType()))
                    .filter(item -> sourceId == null || sourceId.equals(item.getSourceId()))
                    .findFirst().orElse(null);
        });
        when(mapper.insert(any(AssistantMemory.class))).thenAnswer(invocation -> {
            AssistantMemory item = invocation.getArgument(0);
            item.setId((long) stored.size() + 1);
            stored.add(item);
            return 1;
        });
        return stored;
    }

    private static AssistantMemory memory(long id, long userId, String scope, Long sessionId, String content) {
        AssistantMemory memory = new AssistantMemory();
        memory.setId(id);
        memory.setUserId(userId);
        memory.setMemoryScope(scope);
        memory.setMemoryType("CONTEXT".equals(scope) ? "SESSION_CONTEXT" : "PERSONAL");
        memory.setTopicName("CONTEXT".equals(scope) ? "最近会话上下文" : "个人记忆");
        memory.setSourceType(sessionId == null ? "ASSISTANT_CHAT" : "ASSISTANT_SESSION");
        memory.setSourceId(sessionId);
        memory.setSummaryText(content);
        memory.setContentText(content);
        memory.setImportanceScore(BigDecimal.valueOf(60));
        memory.setUpdatedAt(LocalDateTime.of(2026, 10, 29, 10, 0));
        memory.setHitCount(0);
        return memory;
    }

    private static AssistantSession session(Long id, Long userId, long materialId) {
        AssistantSession session = new AssistantSession();
        session.setId(id);
        session.setUserId(userId);
        session.setCurrentContextType("MATERIAL");
        session.setCurrentMaterialId(materialId);
        return session;
    }

    private static Object equality(LambdaQueryWrapper<AssistantMemory> query, String field) {
        Matcher matcher = Pattern.compile("\\b" + field + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}")
                .matcher(query.getSqlSegment());
        return matcher.find() ? query.getParamNameValuePairs().get(matcher.group(1)) : null;
    }

    private static String normalizedSql(LambdaQueryWrapper<AssistantMemory> query) {
        return query.getSqlSegment().replaceAll("#\\{ew\\.paramNameValuePairs\\.MPGENVAL\\d+\\}", "?").replaceAll("\\s+", "");
    }
}
