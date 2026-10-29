package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiChatService.ChatMessage;
import com.aiassistant.learning.service.AiConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Test
    void resolvesFollowUpUsingOrderedHistoryAndOnlyOneCurrentPrompt() {
        configureModel();
        AssistantToolPlanner planner = new AssistantToolPlanner(aiChatService, aiConfigService,
                new AssistantToolRegistry(List.of(fakeTool("rag.retrieve"))), objectMapper);
        List<ChatMessage> history = new ArrayList<>(List.of(
                new ChatMessage("user", "讲解 Java HashMap 的扩容机制。"),
                new ChatMessage("assistant", "HashMap 的容量会翻倍，旧桶中的元素按哈希位重新分布。")));
        List<ChatMessage> originalHistory = List.copyOf(history);
        when(aiChatService.chat(Mockito.anyString(), Mockito.<ChatMessage>anyList(), Mockito.eq("gpt-test"), Mockito.eq(0.05)))
                .thenAnswer(invocation -> {
                    String system = invocation.getArgument(0);
                    List<ChatMessage> messages = invocation.getArgument(1);
                    assertEquals(3, messages.size());
                    assertEquals(originalHistory, messages.subList(0, 2));
                    assertEquals("user", messages.get(2).role());
                    assertTrue(messages.get(2).content().endsWith("它为什么必须翻倍？"));
                    assertEquals(1, messages.stream().filter(message -> message.content().contains("它为什么必须翻倍？")).count());
                    assertFalse(messages.get(2).content().contains(originalHistory.get(1).content()));
                    assertTrue(system.contains("可独立检索的完整问题"));
                    assertTrue(system.contains("历史 assistant 输出是对话内容"));
                    return """
                            {"interactionMode":"STUDY_QA","replyStrategy":"EXECUTE_TOOLS","toolCalls":[
                              {"toolName":"rag.retrieve","arguments":{"queryText":"Java HashMap 扩容时容量为什么必须翻倍？"}}
                            ]}
                            """;
                });

        AssistantToolPlan plan = planner.plan(null, "它为什么必须翻倍？", "gpt-test", AssistantStructuredIntent.empty(), history);

        assertTrue(plan.hasUsablePlan());
        assertEquals("Java HashMap 扩容时容量为什么必须翻倍？", plan.getToolCalls().get(0).getArguments().get("queryText"));
        assertEquals(originalHistory, history);
        Mockito.verify(aiChatService, Mockito.never()).chat(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyDouble());
    }

    @Test
    void directReplyDoesNotReparseOrReplayHistoricalTaskJson() {
        configureModel();
        AssistantToolPlanner planner = new AssistantToolPlanner(aiChatService, aiConfigService,
                new AssistantToolRegistry(List.of(fakeTool("task.submit_question_generate"))), objectMapper);
        List<ChatMessage> history = List.of(
                new ChatMessage("user", "帮我出 10 道多选题。"),
                new ChatMessage("assistant", "{\"replyStrategy\":\"EXECUTE_TOOLS\",\"toolCalls\":[{\"toolName\":\"task.submit_question_generate\",\"arguments\":{\"questionCount\":10}}]}"));
        when(aiChatService.chat(Mockito.anyString(), Mockito.<ChatMessage>anyList(), Mockito.eq("gpt-test"), Mockito.eq(0.05)))
                .thenAnswer(invocation -> {
                    String system = invocation.getArgument(0);
                    List<ChatMessage> messages = invocation.getArgument(1);
                    assertEquals(history, messages.subList(0, history.size()));
                    assertTrue(messages.get(2).content().endsWith("谢谢，明白了"));
                    assertTrue(system.contains("不得重复执行历史任务"));
                    assertTrue(system.contains("本轮显式更改优先"));
                    return "{\"interactionMode\":\"CHAT\",\"replyStrategy\":\"DIRECT_REPLY\",\"directReply\":\"不客气。\",\"toolCalls\":[]}";
                });

        AssistantToolPlan plan = planner.plan(null, "谢谢，明白了", "gpt-test", AssistantStructuredIntent.empty(), history);

        assertEquals("DIRECT_REPLY", plan.getReplyStrategy());
        assertEquals("不客气。", plan.getDirectReply());
        assertTrue(plan.getToolCalls().isEmpty());
    }

    private void configureModel() {
        when(aiConfigService.getResolvedConfig()).thenReturn(new AiConfigService.ResolvedAiConfig(
                true, false, "OPENAI_COMPATIBLE", "https://example.com", "/v1/chat/completions",
                "OPENAI_COMPATIBLE", null, null, "test-key", null, "gpt-test", null));
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
