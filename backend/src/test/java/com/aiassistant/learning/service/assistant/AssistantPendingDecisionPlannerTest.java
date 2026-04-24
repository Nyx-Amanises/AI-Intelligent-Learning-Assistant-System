package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
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
}
