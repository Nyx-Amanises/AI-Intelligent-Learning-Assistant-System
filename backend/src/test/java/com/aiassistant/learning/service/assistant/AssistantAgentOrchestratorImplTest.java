package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiChatService;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AssistantAgentOrchestratorImplTest {

    private final AssistantMemoryService assistantMemoryService = Mockito.mock(AssistantMemoryService.class);
    private final AiConfigService aiConfigService = Mockito.mock(AiConfigService.class);
    private final AiChatService aiChatService = Mockito.mock(AiChatService.class);
    private final StudyMaterialService studyMaterialService = Mockito.mock(StudyMaterialService.class);
    private final QuestionSetService questionSetService = Mockito.mock(QuestionSetService.class);
    private final AssistantStructuredIntentExtractor structuredIntentExtractor = Mockito.mock(AssistantStructuredIntentExtractor.class);
    private final AssistantToolPlanner toolPlanner = Mockito.mock(AssistantToolPlanner.class);
    private final MaterialChapterOutlineAssistantTool materialChapterOutlineAssistantTool = Mockito.mock(MaterialChapterOutlineAssistantTool.class);
    private final SummaryTaskAssistantTool summaryTaskAssistantTool = Mockito.mock(SummaryTaskAssistantTool.class);
    private final QuestionGenerateTaskAssistantTool questionGenerateTaskAssistantTool = Mockito.mock(QuestionGenerateTaskAssistantTool.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AssistantTaskIntentParser taskIntentParser = new AssistantTaskIntentParser();

    AssistantAgentOrchestratorImplTest() {
        when(assistantMemoryService.findRelevantMemories(Mockito.anyLong(), Mockito.anyString(), Mockito.anyInt()))
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
                        .requiresQuestionTypeConfirmation(true)
                        .build()))
                .build()));
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
        assertEquals(1, preparedResult.toolExecutions().size());
        assertNull(session.getPendingActionType());
    }

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
        assertTrue(preparedResult.fallbackReply().contains("我先需要知道你说的是哪份资料"));
        assertEquals(1, preparedResult.toolExecutions().size());
        verifyNoInteractions(structuredIntentExtractor);
    }

    private AssistantAgentOrchestratorImpl newOrchestrator(
            AssistantToolRegistry toolRegistry,
            MaterialSearchAssistantTool materialSearchAssistantTool
    ) {
        return new AssistantAgentOrchestratorImpl(
                toolRegistry,
                assistantMemoryService,
                aiConfigService,
                aiChatService,
                studyMaterialService,
                questionSetService,
                structuredIntentExtractor,
                toolPlanner,
                taskIntentParser,
                materialChapterOutlineAssistantTool,
                materialSearchAssistantTool,
                summaryTaskAssistantTool,
                questionGenerateTaskAssistantTool,
                objectMapper
        );
    }

    private AssistantSession materialSession(Long materialId) {
        AssistantSession session = new AssistantSession();
        session.setCurrentMaterialId(materialId);
        session.setCurrentContextType("MATERIAL");
        session.setCurrentContextId(materialId);
        return session;
    }

    private AssistantTool fakeRagTool() {
        return new AssistantTool() {
            @Override
            public String name() {
                return "rag.retrieve";
            }

            @Override
            public boolean supports(ToolContext context) {
                return context.session() != null && context.session().getCurrentMaterialId() != null;
            }

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

    private AssistantTool fakeSuccessTool(String toolName, String summaryText) {
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
