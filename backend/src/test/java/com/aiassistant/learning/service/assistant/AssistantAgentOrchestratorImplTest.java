package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiChatService.ChatMessage;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AssistantMemoryService;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.assistant.tool.MaterialChapterOutlineAssistantTool;
import com.aiassistant.learning.service.assistant.tool.MaterialSearchAssistantTool;
import com.aiassistant.learning.service.assistant.tool.QuestionGenerateTaskAssistantTool;
import com.aiassistant.learning.service.assistant.tool.SummaryTaskAssistantTool;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * AssistantAgentOrchestratorImpl 的单元测试。
 *
 * <p>编排器是智能助手的“大脑入口”：它决定当前消息要不要查资料、要不要创建任务、是否需要追问用户。</p>
 */
class AssistantAgentOrchestratorImplTest {

    /** 模拟长期记忆服务。 */
    private final AssistantMemoryService assistantMemoryService = Mockito.mock(AssistantMemoryService.class);
    /** 模拟 AI 配置服务，提供测试用模型配置。 */
    private final AiConfigService aiConfigService = Mockito.mock(AiConfigService.class);
    /** 模拟 AI 聊天服务，避免真实调用模型。 */
    private final AiChatService aiChatService = Mockito.mock(AiChatService.class);
    /** 模拟学习资料服务（供真实的资料搜索工具使用）。 */
    private final StudyMaterialService studyMaterialService = Mockito.mock(StudyMaterialService.class);
    /** 模拟题集服务。 */
    private final QuestionSetService questionSetService = Mockito.mock(QuestionSetService.class);
    /** 模拟结构化意图抽取器。 */
    private final AssistantStructuredIntentExtractor structuredIntentExtractor = Mockito.mock(AssistantStructuredIntentExtractor.class);
    /** 模拟工具规划器。 */
    private final AssistantToolPlanner toolPlanner = Mockito.mock(AssistantToolPlanner.class);
    /** 模拟 pending 状态决策器。 */
    private final AssistantPendingDecisionPlanner pendingDecisionPlanner = Mockito.mock(AssistantPendingDecisionPlanner.class);
    /** 模拟章节目录工具。 */
    private final MaterialChapterOutlineAssistantTool materialChapterOutlineAssistantTool = Mockito.mock(MaterialChapterOutlineAssistantTool.class);
    /** 模拟总结任务工具。 */
    private final SummaryTaskAssistantTool summaryTaskAssistantTool = Mockito.mock(SummaryTaskAssistantTool.class);
    /** 模拟出题任务工具。 */
    private final QuestionGenerateTaskAssistantTool questionGenerateTaskAssistantTool = Mockito.mock(QuestionGenerateTaskAssistantTool.class);
    /** JSON 工具，用于构造和解析 pendingActionPayload。 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 真实的任务意图解析器，用来覆盖规则解析逻辑。 */
    private final AssistantTaskIntentParser taskIntentParser = new AssistantTaskIntentParser();

    /**
     * 每个测试默认不返回相关记忆，并配置一个可用的测试模型。
     */
    AssistantAgentOrchestratorImplTest() {
        when(assistantMemoryService.findRelevantMemories(Mockito.anyLong(), Mockito.any(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(List.of());
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
    }

    /**
     * 验证“带我学习资料”应被当成资料问答，而不是误创建总结任务。
     */
    @Test
    void shouldTreatLearningRequestAsStudyQaInsteadOfCreatingSummaryTask() {
        MaterialSearchAssistantTool materialSearchAssistantTool = Mockito.mock(MaterialSearchAssistantTool.class);
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        AssistantSession session = materialSession(7L);
        String userMessage = "你能带我学习一下Java核心知识全面梳理这个资料里面的知识吗";
        when(structuredIntentExtractor.extract(userMessage, "gpt-test")).thenReturn(AssistantStructuredIntent.builder()
                .interactionMode("STUDY_QA")
                .requestedTaskTypes(List.of("SUMMARY"))
                .build());

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, userMessage, "gpt-test");

        assertTrue(preparedResult.useModel());
        assertEquals(1, preparedResult.toolExecutions().size());
        assertEquals("rag.retrieve", preparedResult.toolExecutions().get(0).toolName());
        verifyNoInteractions(summaryTaskAssistantTool);
        verifyNoInteractions(questionGenerateTaskAssistantTool);
    }

    /**
     * 验证用户问“核心要点”时，即使 planner 误判为总结任务，也会回到资料问答流程。
     */
    @Test
    void shouldTreatCoreKnowledgeRequestAsStudyQaEvenWhenPlannerSuggestsSummaryTask() {
        MaterialSearchAssistantTool materialSearchAssistantTool = Mockito.mock(MaterialSearchAssistantTool.class);
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        AssistantSession session = materialSession(7L);
        String userMessage = "整理全量的核心要点";
        when(toolPlanner.plan(Mockito.any(), eq(userMessage), eq("gpt-test"), Mockito.any()))
                .thenReturn(AssistantToolPlan.builder()
                        .planned(true)
                        .interactionMode("TASK_CREATE")
                        .replyStrategy("EXECUTE_TOOLS")
                        .toolCalls(List.of(AssistantToolPlan.ToolCall.builder()
                                .toolName("task.submit_summary")
                                .arguments(Map.of())
                                .reason("模型误判为总结任务")
                                .build()))
                        .build());

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, userMessage, "gpt-test");

        assertTrue(preparedResult.useModel());
        assertEquals(1, preparedResult.toolExecutions().size());
        assertEquals("rag.retrieve", preparedResult.toolExecutions().get(0).toolName());
        verifyNoInteractions(summaryTaskAssistantTool);
        verifyNoInteractions(questionGenerateTaskAssistantTool);
    }

    /**
     * 验证普通打招呼不会触发资料检索。
     */
    @Test
    void shouldNotRetrieveMaterialWhenUserIsJustGreeting() {
        MaterialSearchAssistantTool materialSearchAssistantTool = Mockito.mock(MaterialSearchAssistantTool.class);
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        AssistantSession session = materialSession(7L);
        String userMessage = "你好";
        when(structuredIntentExtractor.extract(userMessage, "gpt-test")).thenReturn(AssistantStructuredIntent.builder()
                .interactionMode("CHAT")
                .build());

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, userMessage, "gpt-test");

        assertTrue(preparedResult.useModel());
        assertTrue(preparedResult.toolExecutions().isEmpty());
    }

    /**
     * 验证资料问答遇到多份同名资料时，会让用户选择具体资料。
     */
    @Test
    void shouldRequireClarificationForDuplicateMaterialDuringStudyQa() throws Exception {
        MaterialSearchAssistantTool materialSearchAssistantTool = new MaterialSearchAssistantTool(
                studyMaterialService,
                taskIntentParser,
                objectMapper
        );
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        String userMessage = "你能带我学习一下Java核心知识全面梳理这个资料里面的知识吗";
        when(structuredIntentExtractor.extract(userMessage, "gpt-test")).thenReturn(AssistantStructuredIntent.builder()
                .interactionMode("STUDY_QA")
                .materialQuery("Java核心知识全面梳理")
                .build());
        when(studyMaterialService.searchAssistantMaterials(1L, "Java核心知识全面梳理", 5))
                .thenReturn(List.of(
                        material(7L, "Java核心知识全面梳理"),
                        material(6L, "Java核心知识全面梳理")
                ));

        AssistantSession session = new AssistantSession();
        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, userMessage, "gpt-test");

        assertFalse(preparedResult.useModel());
        assertEquals(1, preparedResult.toolExecutions().size());
        assertEquals("material.search", preparedResult.toolExecutions().get(0).toolName());
        assertEquals("MATERIAL_SELECTION", session.getPendingActionType());
        AssistantPendingActionPayload payload = objectMapper.readValue(
                session.getPendingActionPayloadJson(),
                AssistantPendingActionPayload.class
        );
        assertEquals("STUDY_QA", payload.getFollowUpActionType());
        assertEquals(userMessage, payload.getFollowUpUserMessage());
    }

    /**
     * 验证用户选择资料候选后，助手能继续之前的资料问答。
     */
    @Test
    void shouldContinueStudyQaAfterUserSelectsMaterialCandidate() throws Exception {
        MaterialSearchAssistantTool materialSearchAssistantTool = Mockito.mock(MaterialSearchAssistantTool.class);
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        AssistantSession session = new AssistantSession();
        session.setPendingActionType("MATERIAL_SELECTION");
        session.setPendingActionPayloadJson(objectMapper.writeValueAsString(AssistantPendingActionPayload.builder()
                .promptText("请选择资料")
                .materialQuery("Java核心知识全面梳理")
                .followUpActionType("STUDY_QA")
                .followUpUserMessage("你能带我学习一下Java核心知识全面梳理这个资料里面的知识吗")
                .materialCandidates(List.of(
                        AssistantMaterialCandidate.builder().id(7L).title("Java核心知识全面梳理").build(),
                        AssistantMaterialCandidate.builder().id(6L).title("Java核心知识全面梳理").build()
                ))
                .build()));
        when(structuredIntentExtractor.extract("7", "gpt-test")).thenReturn(AssistantStructuredIntent.builder()
                .interactionMode("MATERIAL_SELECTION")
                .build());

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, "7", "gpt-test");

        assertTrue(preparedResult.useModel());
        assertEquals(2, preparedResult.toolExecutions().size());
        assertEquals("material.select", preparedResult.toolExecutions().get(0).toolName());
        assertEquals("rag.retrieve", preparedResult.toolExecutions().get(1).toolName());
        assertEquals(7L, session.getCurrentMaterialId());
        assertNull(session.getPendingActionType());
    }

    /**
     * 验证用户用自然语言选择资料时，结构化意图也能帮助完成候选选择。
     */
    @Test
    void shouldContinueStudyQaAfterUserSelectsMaterialCandidateViaStructuredIntent() throws Exception {
        MaterialSearchAssistantTool materialSearchAssistantTool = Mockito.mock(MaterialSearchAssistantTool.class);
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        AssistantSession session = new AssistantSession();
        session.setPendingActionType("MATERIAL_SELECTION");
        session.setPendingActionPayloadJson(objectMapper.writeValueAsString(AssistantPendingActionPayload.builder()
                .promptText("请选择资料")
                .materialQuery("Java")
                .followUpActionType("STUDY_QA")
                .followUpUserMessage("带我学习 Java 核心知识全面梳理里的知识")
                .materialCandidates(List.of(
                        AssistantMaterialCandidate.builder().id(7L).title("Java核心知识全面梳理").build(),
                        AssistantMaterialCandidate.builder().id(8L).title("Docker入门").build()
                ))
                .build()));
        String userMessage = "就学 Java 那份吧";
        when(structuredIntentExtractor.extract(userMessage, "gpt-test")).thenReturn(AssistantStructuredIntent.builder()
                .interactionMode("MATERIAL_SELECTION")
                .materialQuery("Java核心知识全面梳理")
                .build());

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, userMessage, "gpt-test");

        assertTrue(preparedResult.useModel());
        assertEquals(2, preparedResult.toolExecutions().size());
        assertEquals("material.select", preparedResult.toolExecutions().get(0).toolName());
        assertEquals("rag.retrieve", preparedResult.toolExecutions().get(1).toolName());
        assertEquals(7L, session.getCurrentMaterialId());
        assertNull(session.getPendingActionType());
    }

    /**
     * 验证完成资料选择后，会继续回答原始知识问题，而不是只停在选择动作。
     */
    @Test
    void shouldContinueOriginalKnowledgeQuestionAfterMaterialSelection() throws Exception {
        MaterialSearchAssistantTool materialSearchAssistantTool = Mockito.mock(MaterialSearchAssistantTool.class);
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool(), fakeSuccessTool("material.detail", "资料详情"))),
                materialSearchAssistantTool
        );

        AssistantSession session = new AssistantSession();
        session.setPendingActionType("MATERIAL_SELECTION");
        session.setPendingActionPayloadJson(objectMapper.writeValueAsString(AssistantPendingActionPayload.builder()
                .promptText("请选择资料")
                .materialQuery("Java核心知识全面梳理")
                .followUpActionType("STUDY_QA")
                .followUpUserMessage("在Java核心知识全面梳理这份资料中有哪些重要的，核心的知识点？")
                .materialCandidates(List.of(
                        AssistantMaterialCandidate.builder().id(7L).title("Java核心知识全面梳理").build(),
                        AssistantMaterialCandidate.builder().id(6L).title("Java核心知识全面梳理").build()
                ))
                .build()));
        when(structuredIntentExtractor.extract("7", "gpt-test")).thenReturn(AssistantStructuredIntent.builder()
                .interactionMode("MATERIAL_SELECTION")
                .build());

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, "7", "gpt-test");

        assertTrue(preparedResult.useModel());
        assertEquals(2, preparedResult.toolExecutions().size());
        assertEquals("material.select", preparedResult.toolExecutions().get(0).toolName());
        assertEquals("rag.retrieve", preparedResult.toolExecutions().get(1).toolName());
        assertEquals(7L, session.getCurrentMaterialId());
        assertNull(session.getPendingActionType());
    }

    /**
     * 验证等待题型配置时，结构化意图可以直接补齐出题数量并提交任务。
     */
    @Test
    void shouldUseStructuredIntentForPendingQuestionConfigReply() throws Exception {
        MaterialSearchAssistantTool materialSearchAssistantTool = Mockito.mock(MaterialSearchAssistantTool.class);
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        AssistantSession session = materialSession(7L);
        session.setPendingActionType("QUESTION_CONFIG");
        session.setPendingActionPayloadJson(objectMapper.writeValueAsString(AssistantPendingActionPayload.builder()
                .materialId(7L)
                .promptText("题型数量我还需要你确认一下。")
                .tasks(List.of(AssistantPlannedTask.builder()
                        .taskType("QUESTION_GENERATE")
                        .questionCount(5)
                        .singleCount(3)
                        .judgeCount(1)
                        .shortAnswerCount(1)
                        .difficultyLevel(3)
                        .knowledgePoints(List.of("事务", "索引"))
                        .requiresQuestionTypeConfirmation(true)
                        .build()))
                .build()));
        AssistantAgentOrchestrator.AssistantPreparedResult scopeReply =
                orchestrator.prepare(1L, session, "知识点：HashMap", "gpt-test");
        assertFalse(scopeReply.useModel());
        assertEquals("QUESTION_CONFIG", session.getPendingActionType());
        AssistantPendingActionPayload updatedPayload = objectMapper.readValue(
                session.getPendingActionPayloadJson(), AssistantPendingActionPayload.class);
        assertEquals(List.of("HashMap"), updatedPayload.getTasks().get(0).getKnowledgePoints());

        String userMessage = "十个，全部做成选择";
        when(structuredIntentExtractor.extract(userMessage, "gpt-test")).thenReturn(AssistantStructuredIntent.builder()
                .interactionMode("TASK_CONFIG_REPLY")
                .questionConfigReply(true)
                .questionCount(10)
                .exclusiveQuestionType("SINGLE")
                .build());
        when(questionGenerateTaskAssistantTool.executeRequest(eq(1L), eq(7L), Mockito.any(QuestionGenerateRequest.class), isNull()))
                .thenAnswer(invocation -> {
                    QuestionGenerateRequest request = invocation.getArgument(2);
                    LocalDateTime now = LocalDateTime.now();
                    return new AssistantTool.ToolExecutionResult(
                            "task.submit_question_generate",
                            "SUCCESS",
                            "{\"materialId\":7}",
                            "{\"questionCount\":" + request.getQuestionCount() + "}",
                            "已创建出题任务",
                            null,
                            now,
                            now
                    );
                });

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, userMessage, "gpt-test");

        ArgumentCaptor<QuestionGenerateRequest> requestCaptor = ArgumentCaptor.forClass(QuestionGenerateRequest.class);
        verify(questionGenerateTaskAssistantTool).executeRequest(eq(1L), eq(7L), requestCaptor.capture(), isNull());
        assertEquals(10, requestCaptor.getValue().getQuestionCount());
        assertEquals(10, requestCaptor.getValue().getSingleCount());
        assertEquals(0, requestCaptor.getValue().getJudgeCount());
        assertEquals(0, requestCaptor.getValue().getShortAnswerCount());
        assertEquals(List.of("HashMap"), requestCaptor.getValue().getKnowledgePoints());
        assertEquals(1, preparedResult.toolExecutions().size());
        assertNull(session.getPendingActionType());
    }

    /**
     * 验证任务请求缺少明确资料时，资料消歧优先于 planner 的普通追问。
     */
    @Test
    void shouldPreferTaskWorkflowOverPlannerClarificationWhenMaterialMustBeDisambiguated() throws Exception {
        MaterialSearchAssistantTool materialSearchAssistantTool = new MaterialSearchAssistantTool(
                studyMaterialService,
                taskIntentParser,
                objectMapper
        );
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        String userMessage = "帮我用Java核心知识全面梳理出一套题";
        when(toolPlanner.plan(Mockito.any(), eq(userMessage), eq("gpt-test"), Mockito.any()))
                .thenReturn(AssistantToolPlan.builder()
                        .planned(true)
                        .interactionMode("TASK_CREATE")
                        .replyStrategy("ASK_CLARIFICATION")
                        .missingSlots(List.of("questionCount", "questionDistribution"))
                        .clarificationPrompt("请问你需要生成多少道题目？")
                        .toolCalls(List.of(AssistantToolPlan.ToolCall.builder()
                                .toolName("task.submit_question_generate")
                                .arguments(Map.of("materialQuery", "Java核心知识全面梳理"))
                                .reason("用户要基于 Java 资料出题")
                                .build()))
                        .build());
        when(studyMaterialService.searchAssistantMaterials(1L, "Java核心知识全面梳理", 5))
                .thenReturn(List.of(
                        material(7L, "Java核心知识全面梳理"),
                        material(6L, "Java核心知识全面梳理")
                ));

        AssistantSession session = new AssistantSession();
        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, userMessage, "gpt-test");

        assertFalse(preparedResult.useModel());
        assertEquals(1, preparedResult.toolExecutions().size());
        assertEquals("material.search", preparedResult.toolExecutions().get(0).toolName());
        assertEquals("MATERIAL_SELECTION", session.getPendingActionType());
        assertTrue(preparedResult.fallbackReply().contains("#7"));
        assertTrue(preparedResult.fallbackReply().contains("#6"));
        AssistantPendingActionPayload payload = objectMapper.readValue(
                session.getPendingActionPayloadJson(),
                AssistantPendingActionPayload.class
        );
        assertEquals(1, payload.getTasks().size());
        assertEquals("QUESTION_GENERATE", payload.getTasks().get(0).getTaskType());
        assertTrue(Boolean.TRUE.equals(payload.getTasks().get(0).getRequiresQuestionTypeConfirmation()));
    }

    /**
     * 验证等待题型配置时，如果用户补充资料名但仍有重名资料，不会擅自猜测资料 ID。
     */
    @Test
    void shouldResolveMaterialTitleWhileWaitingForQuestionConfigWithoutGuessingMaterialId() throws Exception {
        MaterialSearchAssistantTool materialSearchAssistantTool = new MaterialSearchAssistantTool(
                studyMaterialService,
                taskIntentParser,
                objectMapper
        );
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        AssistantSession session = new AssistantSession();
        session.setPendingActionType("QUESTION_CONFIG");
        session.setPendingActionPayloadJson(objectMapper.writeValueAsString(AssistantPendingActionPayload.builder()
                .promptText("题型数量我还需要你确认一下。")
                .tasks(List.of(AssistantPlannedTask.builder()
                        .taskType("QUESTION_GENERATE")
                        .questionCount(10)
                        .singleCount(5)
                        .judgeCount(3)
                        .shortAnswerCount(2)
                        .difficultyLevel(3)
                        .requiresQuestionTypeConfirmation(false)
                        .build()))
                .build()));
        String userMessage = "《Java核心知识全面梳理》呀";
        when(structuredIntentExtractor.extract(userMessage, "gpt-test")).thenReturn(AssistantStructuredIntent.builder()
                .interactionMode("MATERIAL_SELECTION")
                .materialQuery("Java核心知识全面梳理")
                .build());
        when(studyMaterialService.searchAssistantMaterials(1L, "Java核心知识全面梳理", 5))
                .thenReturn(List.of(
                        material(7L, "Java核心知识全面梳理"),
                        material(6L, "Java核心知识全面梳理")
                ));

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, userMessage, "gpt-test");

        assertFalse(preparedResult.useModel());
        assertEquals(1, preparedResult.toolExecutions().size());
        assertEquals("material.search", preparedResult.toolExecutions().get(0).toolName());
        assertEquals("MATERIAL_SELECTION", session.getPendingActionType());
        AssistantPendingActionPayload payload = objectMapper.readValue(
                session.getPendingActionPayloadJson(),
                AssistantPendingActionPayload.class
        );
        assertEquals(2, payload.getMaterialCandidates().size());
        assertEquals(1, payload.getTasks().size());
        assertNull(session.getCurrentMaterialId());
    }

    /**
     * 验证等待出题配置时，用户切换到另一份资料出题会打断旧 pending 并重新进入新任务流程。
     */
    @Test
    void shouldInterruptPendingQuestionConfigWhenUserStartsQuestionTaskForAnotherMaterial() throws Exception {
        MaterialSearchAssistantTool materialSearchAssistantTool = new MaterialSearchAssistantTool(
                studyMaterialService,
                taskIntentParser,
                objectMapper
        );
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        AssistantSession session = materialSession(8L);
        session.setPendingActionType("QUESTION_CONFIG");
        session.setPendingActionPayloadJson(objectMapper.writeValueAsString(AssistantPendingActionPayload.builder()
                .promptText("题型数量我还需要你确认一下。")
                .materialId(8L)
                .tasks(List.of(AssistantPlannedTask.builder()
                        .taskType("QUESTION_GENERATE")
                        .questionCount(1)
                        .singleCount(1)
                        .judgeCount(0)
                        .shortAnswerCount(0)
                        .difficultyLevel(3)
                        .requiresQuestionTypeConfirmation(true)
                        .build()))
                .build()));
        String userMessage = "根据普通高中教科书·生物学必修1分子与细胞这份资料出一套题";
        when(structuredIntentExtractor.extract(userMessage, "gpt-test")).thenReturn(AssistantStructuredIntent.builder()
                .interactionMode("TASK_CREATE")
                .requestedTaskTypes(List.of("QUESTION_GENERATE"))
                .materialQuery("普通高中教科书·生物学必修1分子与细胞")
                .build());
        when(studyMaterialService.searchAssistantMaterials(1L, "普通高中教科书·生物学必修1分子与细胞", 5))
                .thenReturn(List.of(material(9L, "普通高中教科书·生物学必修1 分子与细胞")));

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, session, userMessage, "gpt-test");

        assertFalse(preparedResult.useModel());
        assertEquals(2, preparedResult.toolExecutions().size());
        assertEquals("material.search", preparedResult.toolExecutions().get(0).toolName());
        assertEquals("task.submit_question_generate", preparedResult.toolExecutions().get(1).toolName());
        assertEquals(9L, session.getCurrentMaterialId());
        assertEquals("QUESTION_CONFIG", session.getPendingActionType());
        AssistantPendingActionPayload payload = objectMapper.readValue(
                session.getPendingActionPayloadJson(),
                AssistantPendingActionPayload.class
        );
        assertEquals(9L, payload.getMaterialId());
        assertEquals(5, payload.getTasks().get(0).getQuestionCount());
        assertEquals(3, payload.getTasks().get(0).getSingleCount());
        assertEquals(1, payload.getTasks().get(0).getJudgeCount());
        assertEquals(1, payload.getTasks().get(0).getShortAnswerCount());
        verifyNoInteractions(questionGenerateTaskAssistantTool);
    }

    /**
     * 验证 planner 已经给出可执行工具计划时，编排器会优先执行 planner 的结果。
     */
    @Test
    void shouldExecutePlannerProvidedToolPlanBeforeRuleFallback() {
        AssistantTool materialListTool = fakeSuccessTool("material.list", "我先帮你列出当前资料。");
        MaterialSearchAssistantTool materialSearchAssistantTool = Mockito.mock(MaterialSearchAssistantTool.class);
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(materialListTool)),
                materialSearchAssistantTool
        );

        String userMessage = "查一下已经向量化的资料";
        when(toolPlanner.plan(Mockito.any(), eq(userMessage), eq("gpt-test"), Mockito.any()))
                .thenReturn(AssistantToolPlan.builder()
                        .planned(true)
                        .interactionMode("MATERIAL_BROWSE")
                        .replyStrategy("EXECUTE_TOOLS")
                        .toolCalls(List.of(AssistantToolPlan.ToolCall.builder()
                                .toolName("material.list")
                                .reason("用户要查看已向量化资料")
                                .arguments(Map.of("embeddingReadyOnly", true))
                                .build()))
                        .build());

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, new AssistantSession(), userMessage, "gpt-test");

        assertTrue(preparedResult.useModel());
        assertEquals(1, preparedResult.toolExecutions().size());
        assertEquals("material.list", preparedResult.toolExecutions().get(0).toolName());
        assertTrue(preparedResult.toolPlanJson().contains("planner"));
        verifyNoInteractions(structuredIntentExtractor);
    }

    /**
     * 验证用户只说“帮我出一套题”时，会保存待选择资料状态并提醒用户补充资料。
     */
    @Test
    void shouldSavePendingMaterialSelectionWhenTaskRequestLacksMaterial() throws Exception {
        MaterialSearchAssistantTool materialSearchAssistantTool = Mockito.mock(MaterialSearchAssistantTool.class);
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(fakeRagTool())),
                materialSearchAssistantTool
        );

        String userMessage = "帮我出一套题";
        when(toolPlanner.plan(Mockito.any(), eq(userMessage), eq("gpt-test"), Mockito.any()))
                .thenReturn(AssistantToolPlan.builder()
                        .planned(true)
                        .interactionMode("TASK_CREATE")
                        .replyStrategy("ASK_CLARIFICATION")
                        .missingSlots(List.of("materialQuery"))
                        .clarificationPrompt("我需要先知道你想基于哪份资料出题。")
                        .build());

        AssistantAgentOrchestrator.AssistantPreparedResult preparedResult =
                orchestrator.prepare(1L, new AssistantSession(), userMessage, "gpt-test");

        assertFalse(preparedResult.useModel());
        assertTrue(preparedResult.fallbackReply().contains("请先告诉我资料标题"));
        assertEquals(1, preparedResult.toolExecutions().size());
        verifyNoInteractions(structuredIntentExtractor);
    }

    @Test
    void prepareSharesOneHistorySnapshotAcrossPlannerFallbackAndPendingDecision() {
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of()), Mockito.mock(MaterialSearchAssistantTool.class));
        AssistantSession session = materialSession(7L);
        session.setId(42L);
        String current = "刚才第二点能再具体说明吗";
        List<ChatMessage> history = new ArrayList<>(twoCompletedTurns());
        List<ChatMessage> originalHistory = List.copyOf(history);
        AssistantStructuredIntent intent = AssistantStructuredIntent.builder().interactionMode("CHAT").build();
        when(toolPlanner.plan(eq(session), eq(current), eq("gpt-test"), isNull(), Mockito.anyList()))
                .thenReturn(AssistantToolPlan.empty());
        when(structuredIntentExtractor.extract(eq(current), eq("gpt-test"), Mockito.anyList())).thenReturn(intent);
        when(pendingDecisionPlanner.decide(eq(session), isNull(), eq(current), eq(intent), Mockito.any(),
                eq("gpt-test"), Mockito.anyList())).thenReturn(AssistantPendingDecision.unknown());
        when(assistantMemoryService.findRelevantMemories(1L, 42L, current, 3)).thenReturn(List.of(
                new AssistantMemoryService.MemorySnippet(8L, "PREFERENCE", "RESPONSE_STYLE", "偏好", "喜欢例子", "喜欢例子")));

        var prepared = orchestrator.prepare(1L, session, current, "gpt-test", history);

        ArgumentCaptor<List<ChatMessage>> plannerHistory = chatMessagesCaptor();
        verify(toolPlanner).plan(eq(session), eq(current), eq("gpt-test"), isNull(), plannerHistory.capture());
        verify(structuredIntentExtractor).extract(eq(current), eq("gpt-test"), Mockito.same(plannerHistory.getValue()));
        verify(pendingDecisionPlanner).decide(eq(session), isNull(), eq(current), eq(intent), Mockito.any(),
                eq("gpt-test"), Mockito.same(plannerHistory.getValue()));
        verify(assistantMemoryService).findRelevantMemories(1L, 42L, current, 3);
        assertEquals(originalHistory, plannerHistory.getValue());
        assertTrue(prepared.useModel());
        assertEquals(5, prepared.messages().size());
        assertEquals(originalHistory, prepared.messages().subList(0, 4));
        assertEquals(List.of("user", "assistant", "user", "assistant", "user"),
                prepared.messages().stream().map(ChatMessage::role).toList());
        assertEquals(prepared.userPrompt(), prepared.messages().get(4).content());
        assertTrue(prepared.userPrompt().contains("materialId=7"));
        assertTrue(prepared.userPrompt().contains("喜欢例子"));
        assertCurrentAppearsOnce(prepared.messages(), current);
        history.clear();
        assertEquals(originalHistory, plannerHistory.getValue(), "调用方后续修改列表不能改变已准备的会话历史");
        assertEquals(originalHistory, prepared.messages().subList(0, 4));
        assertThrows(UnsupportedOperationException.class, () -> prepared.messages().add(new ChatMessage("user", "修改")));
        verifyNoInteractions(aiChatService);
    }

    @Test
    void directPlannerReplyUsesHistoryWithoutCallingTheFinalModelAgain() {
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of()), Mockito.mock(MaterialSearchAssistantTool.class));
        AssistantSession session = materialSession(7L);
        session.setId(43L);
        String current = "把刚才的答案缩成一句话";
        List<ChatMessage> history = twoCompletedTurns();
        AssistantToolPlan plan = AssistantToolPlan.builder().planned(true).interactionMode("CHAT")
                .replyStrategy("DIRECT_REPLY").directReply("HashMap 先定位桶，再比较键。").build();
        when(toolPlanner.plan(eq(session), eq(current), eq("gpt-test"), isNull(), Mockito.anyList())).thenReturn(plan);

        var reply = orchestrator.respond(1L, session, current, "gpt-test", history);

        ArgumentCaptor<List<ChatMessage>> plannerHistory = chatMessagesCaptor();
        verify(toolPlanner).plan(eq(session), eq(current), eq("gpt-test"), isNull(), plannerHistory.capture());
        assertEquals(history, plannerHistory.getValue());
        verify(pendingDecisionPlanner).decide(eq(session), isNull(), eq(current), Mockito.any(), Mockito.same(plan),
                eq("gpt-test"), Mockito.same(plannerHistory.getValue()));
        assertEquals(plan.getDirectReply(), reply.assistantReply());
        verify(assistantMemoryService).findRelevantMemories(1L, 43L, current, 3);
        verify(assistantMemoryService).captureConversationMemory(1L, session, current, plan.getDirectReply());
        verifyNoInteractions(structuredIntentExtractor, aiChatService);
    }

    @Test
    void respondSendsHistoricalRolesAndOneEnhancedCurrentMessageToTheListChatApi() {
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of()), Mockito.mock(MaterialSearchAssistantTool.class));
        AssistantSession session = materialSession(7L);
        session.setId(44L);
        String current = "上一个例子再展开一点";
        List<ChatMessage> history = twoCompletedTurns();
        AssistantToolPlan plan = AssistantToolPlan.builder().planned(true).interactionMode("CHAT")
                .replyStrategy("USE_MODEL").build();
        when(toolPlanner.plan(eq(session), eq(current), eq("gpt-test"), isNull(), Mockito.anyList())).thenReturn(plan);
        when(aiChatService.chat(Mockito.anyString(), Mockito.<ChatMessage>anyList(), eq("gpt-test"), eq(0.3)))
                .thenReturn("沿用刚才的碰撞示例，接下来比较键是否相等。");

        var reply = orchestrator.respond(1L, session, current, "gpt-test", history);

        ArgumentCaptor<List<ChatMessage>> finalMessages = chatMessagesCaptor();
        verify(aiChatService).chat(Mockito.anyString(), finalMessages.capture(), eq("gpt-test"), eq(0.3));
        assertEquals(history, finalMessages.getValue().subList(0, 4));
        assertEquals(5, finalMessages.getValue().size());
        assertEquals("assistant", finalMessages.getValue().get(3).role());
        assertSame(history.get(3), finalMessages.getValue().get(3));
        assertEquals("user", finalMessages.getValue().get(4).role());
        assertTrue(finalMessages.getValue().get(4).content().contains("用户消息："));
        assertCurrentAppearsOnce(finalMessages.getValue(), current);
        assertEquals("沿用刚才的碰撞示例，接下来比较键是否相等。", reply.assistantReply());
        verify(assistantMemoryService).findRelevantMemories(1L, 44L, current, 3);
        verify(assistantMemoryService).captureConversationMemory(1L, session, current, reply.assistantReply());
        verify(aiChatService, Mockito.never()).chat(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyDouble());
        verifyNoInteractions(structuredIntentExtractor);
    }

    @Test
    void legacyPrepareWithoutHistoryKeepsExistingPlannerCallsAndAllowsAnUnsetSessionId() {
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of()), Mockito.mock(MaterialSearchAssistantTool.class));
        AssistantSession session = materialSession(7L);
        when(structuredIntentExtractor.extract("你好", "gpt-test"))
                .thenReturn(AssistantStructuredIntent.builder().interactionMode("CHAT").build());

        var prepared = orchestrator.prepare(1L, session, "你好", "gpt-test");

        verify(assistantMemoryService).findRelevantMemories(1L, null, "你好", 3);
        verify(toolPlanner).plan(session, "你好", "gpt-test", null);
        verify(structuredIntentExtractor).extract("你好", "gpt-test");
        verify(toolPlanner, Mockito.never()).plan(Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.anyList());
        assertTrue(prepared.useModel());
        assertEquals(List.of(new ChatMessage("user", prepared.userPrompt())), prepared.messages());
        assertCurrentAppearsOnce(prepared.messages(), "你好");
    }

    @Test
    void followUpUsesResolvedHistoryQueryForRetrievalWhileKeepingTheCurrentWordsInFinalPrompt() {
        AssistantTool rag = Mockito.mock(AssistantTool.class);
        when(rag.name()).thenReturn("rag.retrieve");
        when(rag.supports(Mockito.any())).thenReturn(true);
        LocalDateTime now = LocalDateTime.now();
        when(rag.execute(Mockito.any())).thenReturn(new AssistantTool.ToolExecutionResult(
                "rag.retrieve", "SUCCESS", "{}", "{}", "找到散列碰撞的说明", null, now, now));
        AssistantAgentOrchestratorImpl orchestrator = newOrchestrator(
                new AssistantToolRegistry(List.of(rag)), Mockito.mock(MaterialSearchAssistantTool.class));
        AssistantSession session = materialSession(7L);
        session.setId(45L);
        String current = "它为什么还要比较键？";
        String resolved = "HashMap 发生散列碰撞后为什么还要通过 equals 比较键？";
        List<ChatMessage> history = twoCompletedTurns();
        AssistantToolPlan plan = AssistantToolPlan.builder().planned(true).interactionMode("STUDY_QA")
                .replyStrategy("EXECUTE_TOOLS")
                .toolCalls(List.of(AssistantToolPlan.ToolCall.builder().toolName("rag.retrieve")
                        .arguments(Map.of("queryText", resolved)).build())).build();
        when(toolPlanner.plan(eq(session), eq(current), eq("gpt-test"), isNull(), Mockito.anyList())).thenReturn(plan);

        var prepared = orchestrator.prepare(1L, session, current, "gpt-test", history);

        ArgumentCaptor<AssistantTool.ToolContext> toolContext = ArgumentCaptor.forClass(AssistantTool.ToolContext.class);
        verify(rag).execute(toolContext.capture());
        assertEquals(resolved, toolContext.getValue().userMessage());
        assertEquals(resolved, toolContext.getValue().structuredIntent().getStudyQuery());
        assertEquals(7L, toolContext.getValue().session().getCurrentMaterialId());
        assertEquals(history, prepared.messages().subList(0, history.size()));
        assertCurrentAppearsOnce(prepared.messages(), current);
        assertTrue(prepared.userPrompt().contains("找到散列碰撞的说明"));
        assertTrue(prepared.useModel());
    }

    private List<ChatMessage> twoCompletedTurns() {
        return List.of(new ChatMessage("user", "HashMap 怎样查找键？"),
                new ChatMessage("assistant", "第一步定位桶，第二步比较键。"),
                new ChatMessage("user", "请举个散列碰撞的例子。"),
                new ChatMessage("assistant", "键甲和键乙进入同一个桶，再通过 equals 区分。"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<ChatMessage>> chatMessagesCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private void assertCurrentAppearsOnce(List<ChatMessage> messages, String current) {
        assertEquals(1L, messages.stream().filter(item -> item.content().contains(current)).count());
        String prompt = messages.get(messages.size() - 1).content();
        assertEquals(prompt.indexOf(current), prompt.lastIndexOf(current));
    }

    /**
     * 构造一个测试用编排器。
     */
    @Test
    void practiceReviewWinsOverGenericQaAndClearsObsoletePending() {
        var orchestrator = newOrchestrator(new AssistantToolRegistry(List.of(
                fakeRagTool(), fakeSuccessTool("practice.detail", "用户答案 B，正确答案 A"))),
                Mockito.mock(MaterialSearchAssistantTool.class));
        AssistantSession session = materialSession(7L);
        session.setCurrentPracticeSessionId(25L);
        session.setPendingActionType("QUESTION_CONFIG");
        session.setPendingActionPayloadJson("{}");
        String message = "这次练习第1题为什么错";
        when(structuredIntentExtractor.extract(message, "gpt-test"))
                .thenReturn(AssistantStructuredIntent.builder().interactionMode("STUDY_QA").build());
        var result = orchestrator.prepare(1L, session, message, "gpt-test");
        assertEquals(List.of("practice.detail"), result.toolExecutions().stream().map(AssistantTool.ToolExecutionResult::toolName).toList());
        assertNull(session.getPendingActionType());
        verifyNoInteractions(summaryTaskAssistantTool, questionGenerateTaskAssistantTool);
    }

    @Test
    void unboundPracticeReviewOffersOwnedHistory() {
        var orchestrator = newOrchestrator(new AssistantToolRegistry(List.of(
                fakeSuccessTool("practice.list", "练习 #25"))), Mockito.mock(MaterialSearchAssistantTool.class));
        var result = orchestrator.prepare(1L, materialSession(7L), "请复盘这次练习", "gpt-test");
        assertEquals(List.of("practice.list"), result.toolExecutions().stream().map(AssistantTool.ToolExecutionResult::toolName).toList());
    }

    @Test
    void explicitMaterialQuestionDoesNotReadOldPracticeEvenWhenPlannerSuggestsIt() {
        var orchestrator = newOrchestrator(new AssistantToolRegistry(List.of(
                fakeRagTool(), fakeSuccessTool("practice.detail", "旧练习"))), Mockito.mock(MaterialSearchAssistantTool.class));
        AssistantSession session = materialSession(7L);
        session.setCurrentPracticeSessionId(25L);
        String message = "请解释这份资料里的 TCP 握手原理";
        when(structuredIntentExtractor.extract(message, "gpt-test"))
                .thenReturn(AssistantStructuredIntent.builder().interactionMode("PRACTICE_QA").practiceSessionId(25L).build());
        var result = orchestrator.prepare(1L, session, message, "gpt-test");
        assertEquals(List.of("rag.retrieve"), result.toolExecutions().stream().map(AssistantTool.ToolExecutionResult::toolName).toList());
    }

    private AssistantAgentOrchestratorImpl newOrchestrator(
            AssistantToolRegistry toolRegistry,
            MaterialSearchAssistantTool materialSearchAssistantTool
    ) {
        AssistantSessionStateSupport sessionState = new AssistantSessionStateSupport(objectMapper, questionSetService);
        AssistantModelPolicy modelPolicy = new AssistantModelPolicy(aiConfigService);
        AssistantInteractionModeResolver modeResolver = new AssistantInteractionModeResolver(taskIntentParser, sessionState);
        AssistantToolExecutionSupport toolExecution = new AssistantToolExecutionSupport(toolRegistry, modelPolicy);
        AssistantTaskWorkflowExecutor taskWorkflowExecutor = new AssistantTaskWorkflowExecutor(
                taskIntentParser,
                sessionState,
                materialSearchAssistantTool,
                summaryTaskAssistantTool,
                questionGenerateTaskAssistantTool
        );
        AssistantPendingActionHandler pendingActionHandler = new AssistantPendingActionHandler(
                taskIntentParser,
                sessionState,
                modeResolver,
                toolExecution,
                taskWorkflowExecutor,
                modelPolicy,
                materialSearchAssistantTool,
                materialChapterOutlineAssistantTool
        );
        AssistantStudyQaWorkflowHandler studyQaWorkflowHandler = new AssistantStudyQaWorkflowHandler(
                taskIntentParser,
                sessionState,
                modeResolver,
                toolExecution,
                modelPolicy,
                materialSearchAssistantTool
        );
        return new AssistantAgentOrchestratorImpl(
                assistantMemoryService,
                aiChatService,
                structuredIntentExtractor,
                toolPlanner,
                pendingDecisionPlanner,
                modeResolver,
                pendingActionHandler,
                taskWorkflowExecutor,
                studyQaWorkflowHandler,
                toolExecution,
                new AssistantPromptBuilder(),
                modelPolicy,
                sessionState
        );
    }

    /**
     * 构造一个已经绑定当前资料的助手会话。
     */
    private AssistantSession materialSession(Long materialId) {
        AssistantSession session = new AssistantSession();
        session.setCurrentMaterialId(materialId);
        session.setCurrentContextType("MATERIAL");
        session.setCurrentContextId(materialId);
        return session;
    }

    /**
     * 构造一个假的 RAG 检索工具。
     */
    private AssistantTool fakeRagTool() {
        return new AssistantTool() {
            /**
             * 返回工具名。
             */
            @Override
            public String name() {
                return "rag.retrieve";
            }

            /**
             * 只有会话已经绑定资料时，才允许执行 RAG 检索。
             */
            @Override
            public boolean supports(ToolContext context) {
                return context.session() != null && context.session().getCurrentMaterialId() != null;
            }

            /**
             * 返回固定的检索摘要，避免单元测试依赖真实向量库。
             */
            @Override
            public ToolExecutionResult execute(ToolContext context) {
                LocalDateTime now = LocalDateTime.now();
                return new ToolExecutionResult(
                        "rag.retrieve",
                        "SUCCESS",
                        "{\"materialId\":" + context.session().getCurrentMaterialId() + "}",
                        "{\"segments\":[]}",
                        "检索到以下资料依据：1. 第 1 页 · 段落#1 · Java 是一门面向对象语言。",
                        null,
                        now,
                        now
                );
            }
        };
    }

    /**
     * 构造一个始终执行成功的假工具。
     */
    private AssistantTool fakeSuccessTool(String toolName, String summaryText) {
        return new AssistantTool() {
            /**
             * 返回工具名。
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
             * 返回固定成功结果。
             */
            @Override
            public ToolExecutionResult execute(ToolContext context) {
                LocalDateTime now = LocalDateTime.now();
                return new ToolExecutionResult(
                        toolName,
                        "SUCCESS",
                        "{}",
                        "{}",
                        summaryText,
                        null,
                        now,
                        now
                );
            }
        };
    }

    /**
     * 构造测试用资料列表项。
     */
    private MaterialPageVO material(Long id, String title) {
        return MaterialPageVO.builder()
                .id(id)
                .title(title)
                .materialType("PDF")
                .parseStatus("SUCCESS")
                .difficultyLevel(3)
                .build();
    }
}
