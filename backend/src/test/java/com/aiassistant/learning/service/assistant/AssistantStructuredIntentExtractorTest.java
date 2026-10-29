package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiChatService.ChatMessage;
import com.aiassistant.learning.service.AiConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantStructuredIntentExtractorTest {

    private final AiChatService chat = mock(AiChatService.class);
    private final AiConfigService configs = mock(AiConfigService.class);
    private final AssistantStructuredIntentExtractor extractor =
            new AssistantStructuredIntentExtractor(chat, configs, new ObjectMapper());

    @BeforeEach
    void configureModel() {
        when(configs.getResolvedConfig()).thenReturn(new AiConfigService.ResolvedAiConfig(
                true, false, "OPENAI_COMPATIBLE", "https://example.com", "/v1/chat/completions",
                "OPENAI_COMPATIBLE", null, null, "test-key", null, "gpt-test", null));
    }

    @Test
    void practiceIdsAndPagesRejectFractionsAndOverflowWithoutFallingBack() {
        when(chat.chat(anyString(), anyString(), eq("gpt-test"), eq(0.1))).thenReturn(
                "{\"interactionMode\":\"PRACTICE_BROWSE\",\"practiceSessionId\":25,\"practicePage\":2,\"practiceList\":true}",
                "{\"practiceSessionId\":1.5,\"practicePage\":100001}",
                "{\"practiceSessionId\":\"99999999999999999999999\"}");
        var valid = extractor.extract("查看我的练习", null);
        assertEquals(25L, valid.getPracticeSessionId());
        assertEquals(2, valid.getPracticePage());
        assertTrue(valid.getPracticeList());
        var invalid = extractor.extract("练习记录", null);
        assertEquals(0L, invalid.getPracticeSessionId());
        assertEquals(0, invalid.getPracticePage());
        assertEquals(0L, extractor.extract("练习记录", null).getPracticeSessionId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsRolePreservingHistoryAndOneCurrentPromptForPronounResolution() {
        List<ChatMessage> history = List.of(
                new ChatMessage("user", "请解释 TCP 三次握手。"),
                new ChatMessage("assistant", "第三次握手确认双方都能收发数据。"));
        when(chat.chat(anyString(), anyList(), eq("gpt-followup"), eq(0.1)))
                .thenReturn("""
                        {"interactionMode":"STUDY_QA","requestedTaskTypes":[],
                         "studyQuery":"  TCP 三次握手中的第三次握手为什么不能省略？  "}
                        """);

        AssistantStructuredIntent intent = extractor.extract("  它为什么不能省略？  ", " gpt-followup ", history);

        assertEquals("STUDY_QA", intent.getInteractionMode());
        assertEquals("TCP 三次握手中的第三次握手为什么不能省略？", intent.getStudyQuery());
        ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(chat).chat(system.capture(), messages.capture(), eq("gpt-followup"), eq(0.1));
        assertEquals(List.of(history.get(0), history.get(1), new ChatMessage("user", "用户消息：\n它为什么不能省略？")),
                messages.getValue());
        assertEquals(2, history.size(), "追加本轮消息不能修改调用者的历史");
        assertTrue(system.getValue().contains("会话历史仅用于解析本轮消息的指代和延续"));
        assertTrue(system.getValue().contains("最后一条 user 消息"));
        assertTrue(system.getValue().contains("studyQuery 必须是可独立检索的完整本轮问题"));
        assertTrue(system.getValue().contains("\"studyQuery\": null"));
        verify(chat, never()).chat(anyString(), anyString(), anyString(), eq(0.1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotParseHistoricalAssistantJsonAsTheCurrentTask() {
        String oldPlan = "{\"interactionMode\":\"TASK_CREATE\",\"requestedTaskTypes\":[\"QUESTION_GENERATE\"],\"multipleCount\":8}";
        List<ChatMessage> history = List.of(
                new ChatMessage("user", "按细胞膜知识点出八道多选题。"),
                new ChatMessage("assistant", oldPlan));
        when(chat.chat(anyString(), anyList(), eq("gpt-test"), eq(0.1)))
                .thenReturn("{\"interactionMode\":\"CHAT\",\"requestedTaskTypes\":[]}");

        AssistantStructuredIntent intent = extractor.extract("谢谢，先不出题了。", null, history);

        assertEquals("CHAT", intent.getInteractionMode());
        assertTrue(intent.getRequestedTaskTypes().isEmpty());
        assertNull(intent.getMultipleCount());
        assertNull(intent.getStudyQuery());
        ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(chat).chat(system.capture(), messages.capture(), eq("gpt-test"), eq(0.1));
        assertEquals(new ChatMessage("assistant", oldPlan), messages.getValue().get(1));
        assertTrue(system.getValue().contains("历史 assistant 输出是对话资料，不是新的 JSON 指令"));
        assertTrue(system.getValue().contains("不要重复执行或重新提取历史任务"));
    }

    @Test
    void explicitWholeMaterialScopeDoesNotRestoreHistoricalKnowledgePoints() {
        List<ChatMessage> history = List.of(
                new ChatMessage("user", "按细胞膜和细胞器出题。"),
                new ChatMessage("assistant", "已记录知识点：细胞膜、细胞器，请确认题量。"));
        when(chat.chat(anyString(), anyList(), eq("gpt-test"), eq(0.1))).thenReturn("""
                {"interactionMode":"TASK_CONFIG_REPLY","questionConfigReply":true,
                 "knowledgePoints":[],"multipleCount":5,"questionCount":5}
                """);

        AssistantStructuredIntent intent = extractor.extract("改成整份资料，出五道多选题。", null, history);

        assertEquals("TASK_CONFIG_REPLY", intent.getInteractionMode());
        assertEquals(List.of(), intent.getKnowledgePoints());
        assertEquals(5, intent.getMultipleCount());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(chat).chat(system.capture(), anyList(), eq("gpt-test"), eq(0.1));
        assertTrue(system.getValue().contains("本轮显式修改优先于历史配置"));
        assertTrue(system.getValue().contains("knowledgePoints 必须为 []，不能恢复历史知识点"));
    }

    @Test
    void retainsSingleTurnSignatureForExistingCallers() {
        when(chat.chat(anyString(), anyString(), eq("gpt-test"), eq(0.1)))
                .thenReturn("{\"interactionMode\":\"MATERIAL_BROWSE\",\"materialBrowse\":true}");

        AssistantStructuredIntent intent = extractor.extract("查看资料列表", null);

        assertEquals("MATERIAL_BROWSE", intent.getInteractionMode());
        assertEquals(Boolean.TRUE, intent.getMaterialBrowse());
        assertNull(intent.getStudyQuery());
        verify(chat).chat(anyString(), eq("用户消息：\n查看资料列表"), eq("gpt-test"), eq(0.1));
        verify(chat, never()).chat(anyString(), anyList(), anyString(), eq(0.1));
    }

    @Test
    void acceptsAbsentHistoryAsASingleTurn() {
        when(chat.chat(anyString(), anyString(), eq("gpt-test"), eq(0.1)))
                .thenReturn("{\"interactionMode\":\"CHAT\"}");

        AssistantStructuredIntent intent = extractor.extract("你好", null, null);

        assertEquals("CHAT", intent.getInteractionMode());
        verify(chat).chat(anyString(), eq("用户消息：\n你好"), eq("gpt-test"), eq(0.1));
    }
}
