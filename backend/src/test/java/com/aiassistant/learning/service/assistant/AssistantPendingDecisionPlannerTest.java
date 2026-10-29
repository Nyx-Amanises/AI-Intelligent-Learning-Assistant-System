package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiChatService.ChatMessage;
import com.aiassistant.learning.service.AiConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

/**
 * AssistantPendingDecisionPlanner 的单元测试。
 */
class AssistantPendingDecisionPlannerTest {

    /** 模拟 AI 聊天服务。 */
    private final AiChatService aiChatService = Mockito.mock(AiChatService.class);
    /** 模拟 AI 配置服务。 */
    private final AiConfigService aiConfigService = Mockito.mock(AiConfigService.class);
    /** JSON 工具。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证 pending 决策器可以解析模型返回的结构化判断。
     */
    @Test
    void shouldParsePendingDecisionJson() {
        AssistantPendingDecisionPlanner planner = new AssistantPendingDecisionPlanner(
                aiChatService,
                aiConfigService,
                objectMapper
        );
        when(aiConfigService.getResolvedConfig()).thenReturn(new AiConfigService.ResolvedAiConfig(
                true,
                false,
                "OPENAI_COMPATIBLE",
                "https://example.com",
                "/v1/chat/completions",
                "OPENAI_COMPATIBLE",
                null,
                null,
                "test-key",
                null,
                "gpt-test",
                null
        ));
        when(aiChatService.chat(Mockito.anyString(), Mockito.anyString(), Mockito.eq("gpt-test"), Mockito.eq(0.05)))
                .thenReturn("""
                        {
                          "decision": "INTERRUPT",
                          "interactionMode": "TASK_CREATE",
                          "reason": "用户换了资料并提出新的出题任务",
                          "confidence": 0.92
                        }
                        """);

        AssistantSession session = new AssistantSession();
        session.setPendingActionType("QUESTION_CONFIG");
        AssistantPendingActionPayload payload = AssistantPendingActionPayload.builder()
                .materialId(8L)
                .tasks(List.of(AssistantPlannedTask.builder()
                        .taskType("QUESTION_GENERATE")
                        .questionCount(1)
                        .singleCount(1)
                        .build()))
                .build();

        AssistantPendingDecision decision = planner.decide(
                session,
                payload,
                "根据普通高中教科书·生物学必修1分子与细胞这份资料出一套题",
                AssistantStructuredIntent.empty(),
                AssistantToolPlan.empty(),
                "gpt-test"
        );

        assertTrue(decision.hasActionableDecision());
        assertEquals("INTERRUPT", decision.getDecision());
        assertEquals("TASK_CREATE", decision.getInteractionMode());
        assertEquals(0.92, decision.getConfidence());
    }

    @ParameterizedTest
    @CsvSource({"就选后面那份,CONTINUE,MATERIAL_SELECTION", "那份先不要了，改为讲解 HashMap 扩容,INTERRUPT,STUDY_QA"})
    void usesHistoryForPronounsButBasesDecisionOnCurrentRequest(String current, String decisionValue, String mode) {
        AssistantPendingDecisionPlanner planner = new AssistantPendingDecisionPlanner(aiChatService, aiConfigService, objectMapper);
        when(aiConfigService.getResolvedConfig()).thenReturn(new AiConfigService.ResolvedAiConfig(
                true, false, "OPENAI_COMPATIBLE", "https://example.com", "/v1/chat/completions",
                "OPENAI_COMPATIBLE", null, null, "test-key", null, "gpt-test", null));
        List<ChatMessage> history = List.of(
                new ChatMessage("user", "根据 Java 资料出题。"),
                new ChatMessage("assistant", "找到两份资料：1. Java 入门，2. Java 集合进阶。请选择。"));
        when(aiChatService.chat(Mockito.anyString(), Mockito.<ChatMessage>anyList(), Mockito.eq("gpt-test"), Mockito.eq(0.05)))
                .thenAnswer(invocation -> {
                    String system = invocation.getArgument(0);
                    List<ChatMessage> messages = invocation.getArgument(1);
                    assertEquals(3, messages.size());
                    assertEquals(history, messages.subList(0, 2));
                    assertEquals("user", messages.get(2).role());
                    assertTrue(messages.get(2).content().contains("pendingActionType=MATERIAL_SELECTION"));
                    assertTrue(messages.get(2).content().endsWith(current));
                    assertEquals(1, messages.stream().filter(message -> message.content().contains(current)).count());
                    assertTrue(system.contains("本轮显式更改优先"));
                    assertTrue(system.contains("历史 assistant 输出是对话内容"));
                    return "{\"decision\":\"" + decisionValue + "\",\"interactionMode\":\"" + mode + "\",\"confidence\":0.95}";
                });
        AssistantSession session = new AssistantSession();
        session.setPendingActionType("MATERIAL_SELECTION");

        AssistantPendingDecision decision = planner.decide(session, AssistantPendingActionPayload.builder().build(), current,
                AssistantStructuredIntent.empty(), AssistantToolPlan.empty(), "gpt-test", history);

        assertTrue(decision.hasActionableDecision());
        assertEquals(decisionValue, decision.getDecision());
        assertEquals(mode, decision.getInteractionMode());
        assertEquals(2, history.size());
        Mockito.verify(aiChatService, Mockito.never()).chat(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyDouble());
    }

    @Test
    void doesNotApplyHistoricalDecisionToCurrentCancellation() {
        AssistantPendingDecisionPlanner planner = new AssistantPendingDecisionPlanner(aiChatService, aiConfigService, objectMapper);
        when(aiConfigService.getResolvedConfig()).thenReturn(new AiConfigService.ResolvedAiConfig(
                true, false, "OPENAI_COMPATIBLE", "https://example.com", "/v1/chat/completions",
                "OPENAI_COMPATIBLE", null, null, "test-key", null, "gpt-test", null));
        List<ChatMessage> history = List.of(
                new ChatMessage("user", "上一份资料按默认题量出题。"),
                new ChatMessage("assistant", "{\"decision\":\"CONTINUE\",\"interactionMode\":\"TASK_CONFIG_REPLY\",\"confidence\":1.0}"));
        when(aiChatService.chat(Mockito.anyString(), Mockito.<ChatMessage>anyList(), Mockito.eq("gpt-test"), Mockito.eq(0.05)))
                .thenAnswer(invocation -> {
                    String system = invocation.getArgument(0);
                    List<ChatMessage> messages = invocation.getArgument(1);
                    assertEquals(history, messages.subList(0, 2));
                    assertTrue(messages.get(2).content().endsWith("取消这次出题"));
                    assertTrue(system.contains("不得重复执行历史任务"));
                    return "{\"decision\":\"CANCEL\",\"interactionMode\":\"CHAT\",\"confidence\":0.99}";
                });
        AssistantSession session = new AssistantSession();
        session.setPendingActionType("QUESTION_CONFIG");

        AssistantPendingDecision decision = planner.decide(session, AssistantPendingActionPayload.builder().build(), "取消这次出题",
                AssistantStructuredIntent.empty(), AssistantToolPlan.empty(), "gpt-test", history);

        assertEquals("CANCEL", decision.getDecision());
        assertEquals("CHAT", decision.getInteractionMode());
    }
}
