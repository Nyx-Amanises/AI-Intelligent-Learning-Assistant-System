package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AssistantToolPlannerTest {

    private final AiChatService aiChatService = Mockito.mock(AiChatService.class);
    private final AiConfigService aiConfigService = Mockito.mock(AiConfigService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParsePlannerJsonAndFilterUnknownTools() {
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(fakeTool("material.list")));
        AssistantToolPlanner planner = new AssistantToolPlanner(aiChatService, aiConfigService, registry, objectMapper);
        when(aiConfigService.getResolvedConfig()).thenReturn(new AiConfigService.ResolvedAiConfig(
                true,
                false,
                "https://example.com",
                "/v1/chat/completions",
                "OPENAI",
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
                          "interactionMode": "MATERIAL_BROWSE",
                          "intent": "LIST_EMBEDDED_MATERIALS",
                          "replyStrategy": "EXECUTE_TOOLS",
                          "missingSlots": [],
                          "toolCalls": [
                            {
                              "toolName": "material.list",
                              "arguments": {"embeddingReadyOnly": true},
                              "reason": "查看已向量化资料"
                            },
                            {
                              "toolName": "dangerous.delete",
                              "arguments": {},
                              "reason": "非法工具"
                            }
                          ]
                        }
                        """);

        AssistantToolPlan plan = planner.plan(null, "查一下已经生成 Embedding 的资料", "gpt-test", AssistantStructuredIntent.empty());

        assertTrue(plan.hasUsablePlan());
        assertEquals("MATERIAL_BROWSE", plan.getInteractionMode());
        assertEquals(1, plan.getToolCalls().size());
        assertEquals("material.list", plan.getToolCalls().get(0).getToolName());
        assertEquals(true, plan.getToolCalls().get(0).getArguments().get("embeddingReadyOnly"));
    }

    private AssistantTool fakeTool(String toolName) {
        return new AssistantTool() {
            @Override
            public String name() {
                return toolName;
            }

            @Override
            public boolean supports(ToolContext context) {
                return true;
            }

            @Override
            public ToolExecutionResult execute(ToolContext context) {
                LocalDateTime now = LocalDateTime.now();
                return new ToolExecutionResult(toolName, "SUCCESS", "{}", "{}", "ok", null, now, now);
            }
        };
    }
}
