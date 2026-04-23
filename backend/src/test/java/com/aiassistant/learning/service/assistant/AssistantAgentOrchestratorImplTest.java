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
    /** 模拟学习资料服务。 */
    private final StudyMaterialService studyMaterialService = Mockito.mock(StudyMaterialService.class);
    /** 模拟题集服务。 */
    private final QuestionSetService questionSetService = Mockito.mock(QuestionSetService.class);
    /** 模拟结构化意图抽取器。 */
    private final AssistantStructuredIntentExtractor structuredIntentExtractor = Mockito.mock(AssistantStructuredIntentExtractor.class);
    /** 模拟工具规划器。 */
    private final AssistantToolPlanner toolPlanner = Mockito.mock(AssistantToolPlanner.class);
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
        assertTrue(preparedResult.fallbackReply().contains("我先需要知道你说的是哪份资料"));
        assertEquals(1, preparedResult.toolExecutions().size());
        verifyNoInteractions(structuredIntentExtractor);
    }

    /**
     * 构造一个测试用编排器。
     */
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
