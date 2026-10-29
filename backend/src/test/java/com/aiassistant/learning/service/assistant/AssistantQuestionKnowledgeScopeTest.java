package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.assistant.tool.QuestionGenerateTaskAssistantTool;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantQuestionKnowledgeScopeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void structuredExtractionPreservesLabelCaseAndDistinguishesUnspecifiedFromClear() {
        AiChatService chat = mock(AiChatService.class);
        AiConfigService config = mock(AiConfigService.class);
        when(config.getResolvedConfig()).thenReturn(new AiConfigService.ResolvedAiConfig(
                true, false, null, null, null, null, null, null, "test-key", null, "test-model", null));
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble())).thenReturn(
                "{\"knowledgePoints\":[\"HashMap\",\"C++\"],\"multipleCount\":2}",
                "{}", "{\"knowledgePoints\":[]}", "{\"knowledgePoints\":\"事务\"}");
        AssistantStructuredIntentExtractor extractor = new AssistantStructuredIntentExtractor(chat, config, mapper);

        AssistantStructuredIntent intent = extractor.extract("指定知识点出题", null);
        assertEquals(List.of("HashMap", "C++"), intent.getKnowledgePoints());
        assertEquals(2, intent.getMultipleCount());
        assertNull(extractor.extract("默认", null).getKnowledgePoints());
        assertEquals(List.of(), extractor.extract("改成整份资料", null).getKnowledgePoints());
        assertEquals(List.of(""), extractor.extract("指定事务知识点", null).getKnowledgePoints());
    }

    @Test
    void plannedScopeIsPreservedButToolCannotSubmitWithoutConfirmation() throws Exception {
        AssistantToolExecutionSupport support = new AssistantToolExecutionSupport(
                new AssistantToolRegistry(List.of()), mock(AssistantModelPolicy.class));
        AssistantToolPlan plan = AssistantToolPlan.builder().planned(true).toolCalls(List.of(
                AssistantToolPlan.ToolCall.builder().toolName("task.submit_question_generate")
                        .arguments(Map.of("knowledgePoints", List.of("事务", "索引"), "multipleCount", 2))
                        .build())).build();
        AssistantStructuredIntent intent = support.mergePlanIntoStructuredIntent(AssistantStructuredIntent.empty(), plan);
        assertEquals(List.of("事务", "索引"), intent.getKnowledgePoints());
        AiTaskService tasks = mock(AiTaskService.class);
        when(tasks.submitQuestionGenerateTask(eq(7L), eq(42L), any())).thenReturn(
                AiTaskDetailVO.builder().id(9L).status("PENDING").build());
        QuestionGenerateTaskAssistantTool tool = new QuestionGenerateTaskAssistantTool(
                tasks, new AssistantTaskIntentParser(), mapper);
        AssistantSession session = new AssistantSession();
        session.setCurrentMaterialId(42L);

        AssistantTool.ToolExecutionResult result = tool.execute(new AssistantTool.ToolContext(
                7L, session, "生成练习题", null, intent));

        org.mockito.Mockito.verifyNoInteractions(tasks);
        assertEquals("FAILED", result.status());
        assertTrue(result.errorMessage().contains("确认"));
        var options = new AssistantTaskIntentParser().parseQuestionRequest("生成练习题", null, intent);
        assertEquals(List.of("事务", "索引"), options.knowledgePoints());
        assertEquals(2, options.multipleCount());
        plan.getToolCalls().get(0).setArguments(Map.of("multipleCount", 3));
        assertEquals(List.of("事务", "索引"), support.mergePlanIntoStructuredIntent(intent, plan).getKnowledgePoints());
        plan.getToolCalls().get(0).setArguments(Map.of("knowledgePoints", List.of()));
        assertEquals(List.of(), support.mergePlanIntoStructuredIntent(intent, plan).getKnowledgePoints());
        plan.getToolCalls().get(0).setArguments(Map.of("knowledgePoints", "事务"));
        assertEquals(List.of(""), support.mergePlanIntoStructuredIntent(intent, plan).getKnowledgePoints());
    }
}
