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

/**
 * AssistantToolPlanner 的单元测试。
 *
 * <p>它主要验证“大模型返回的工具计划 JSON”能否被安全解析，并且只保留系统真正注册过的工具。</p>
 */
class AssistantToolPlannerTest {

    /** 模拟 AI 聊天服务，避免测试时真实调用大模型。 */
    private final AiChatService aiChatService = Mockito.mock(AiChatService.class);
    /** 模拟 AI 配置服务，为 planner 提供测试模型配置。 */
    private final AiConfigService aiConfigService = Mockito.mock(AiConfigService.class);
    /** JSON 工具，用于解析 planner 返回的 JSON 文本。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证 planner 可以解析模型返回的 JSON，并过滤掉未注册的危险工具。
     */
    @Test
    void shouldParsePlannerJsonAndFilterUnknownTools() {
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(fakeTool("material.list")));
        AssistantToolPlanner planner = new AssistantToolPlanner(aiChatService, aiConfigService, registry, objectMapper);
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

    /**
     * 构造一个测试用假工具。
     */
    private AssistantTool fakeTool(String toolName) {
        return new AssistantTool() {
            /**
             * 返回工具名称，供注册表和 planner 匹配。
             */
            @Override
            public String name() {
                return toolName;
            }

            /**
             * 测试工具始终支持执行。
             */
            @Override
            public boolean supports(ToolContext context) {
                return true;
            }

            /**
             * 返回一个固定成功结果，测试重点不在工具内部逻辑。
             */
            @Override
            public ToolExecutionResult execute(ToolContext context) {
                LocalDateTime now = LocalDateTime.now();
                return new ToolExecutionResult(toolName, "SUCCESS", "{}", "{}", "ok", null, now, now);
            }
        };
    }
}
