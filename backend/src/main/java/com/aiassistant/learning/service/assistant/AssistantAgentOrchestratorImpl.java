package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AssistantMemoryService;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.AssistantMemoryService.MemorySnippet;
import com.aiassistant.learning.service.assistant.tool.MaterialChapterOutlineAssistantTool;
import com.aiassistant.learning.service.assistant.tool.MaterialSearchAssistantTool;
import com.aiassistant.learning.service.assistant.tool.QuestionGenerateTaskAssistantTool;
import com.aiassistant.learning.service.assistant.tool.SummaryTaskAssistantTool;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.question.QuestionSetPageVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AssistantAgentOrchestratorImpl implements AssistantAgentOrchestrator {

    private static final List<String> MATERIAL_DETAIL_KEYWORDS = List.of(
            "资料信息", "资料详情", "资料基本信息", "文件信息", "页数", "多少页", "总页数", "标题", "难度", "字符数", "解析状态", "PDF状态"
    );
    private static final List<String> TASK_STATUS_KEYWORDS = List.of("任务", "进度", "执行到哪", "状态");
    private static final List<String> PRACTICE_KEYWORDS = List.of("练习", "错题", "判分", "这次练习", "为什么错");
    private static final List<String> QUESTION_SET_KEYWORDS = List.of("题集", "这套题", "题目分布", "题型");
    private static final List<String> CASUAL_CHAT_KEYWORDS = List.of(
            "你好", "您好", "hi", "hello", "在吗", "谢谢", "多谢", "早上好", "下午好", "晚上好", "你是谁", "介绍一下你自己"
    );
    private static final List<String> QUESTION_CONFIG_REPLY_HINT_KEYWORDS = List.of(
            "单选", "选择题", "判断", "简答", "默认", "全部", "全都", "只出", "都出", "题量", "数量", "道题"
    );
    private static final List<String> MATERIAL_SELECTION_REPLY_HINT_KEYWORDS = List.of(
            "这个", "那个", "这份", "那份", "资料", "序号", "第", "id", "#"
    );
    private static final List<String> STUDY_QA_HINT_KEYWORDS = List.of(
            "带我学习", "带我复习", "帮我学习", "帮我复习", "讲讲", "解释", "说明", "告诉我", "什么是", "为什么", "怎么", "如何",
            "区别", "联系", "原理", "概念", "简介", "概述", "知识点", "重点", "难点", "考点", "学习", "核心", "要点", "重要内容"
    );
    private static final List<String> EXPLICIT_SUMMARY_TASK_KEYWORDS = List.of(
            "生成总结", "生成AI总结", "生成ai总结", "AI总结", "ai总结", "总结任务", "创建总结", "保存到笔记", "保存笔记",
            "存笔记", "帮我总结", "总结一下", "做个总结", "生成提纲", "生成大纲", "输出总结"
    );

    private final AssistantToolRegistry toolRegistry;
    private final AssistantMemoryService assistantMemoryService;
    private final AiConfigService aiConfigService;
    private final AiChatService aiChatService;
    private final StudyMaterialService studyMaterialService;
    private final QuestionSetService questionSetService;
    private final AssistantStructuredIntentExtractor structuredIntentExtractor;
    private final AssistantToolPlanner toolPlanner;
    private final AssistantTaskIntentParser taskIntentParser;
    private final MaterialChapterOutlineAssistantTool materialChapterOutlineAssistantTool;
    private final MaterialSearchAssistantTool materialSearchAssistantTool;
    private final SummaryTaskAssistantTool summaryTaskAssistantTool;
    private final QuestionGenerateTaskAssistantTool questionGenerateTaskAssistantTool;
    private final ObjectMapper objectMapper;

    public AssistantAgentOrchestratorImpl(
            AssistantToolRegistry toolRegistry,
            AssistantMemoryService assistantMemoryService,
            AiConfigService aiConfigService,
            AiChatService aiChatService,
            StudyMaterialService studyMaterialService,
            QuestionSetService questionSetService,
            AssistantStructuredIntentExtractor structuredIntentExtractor,
            AssistantToolPlanner toolPlanner,
            AssistantTaskIntentParser taskIntentParser,
            MaterialChapterOutlineAssistantTool materialChapterOutlineAssistantTool,
            MaterialSearchAssistantTool materialSearchAssistantTool,
            SummaryTaskAssistantTool summaryTaskAssistantTool,
            QuestionGenerateTaskAssistantTool questionGenerateTaskAssistantTool,
            ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.assistantMemoryService = assistantMemoryService;
        this.aiConfigService = aiConfigService;
        this.aiChatService = aiChatService;
        this.studyMaterialService = studyMaterialService;
        this.questionSetService = questionSetService;
        this.structuredIntentExtractor = structuredIntentExtractor;
        this.toolPlanner = toolPlanner;
        this.taskIntentParser = taskIntentParser;
        this.materialChapterOutlineAssistantTool = materialChapterOutlineAssistantTool;
        this.materialSearchAssistantTool = materialSearchAssistantTool;
        this.summaryTaskAssistantTool = summaryTaskAssistantTool;
        this.questionGenerateTaskAssistantTool = questionGenerateTaskAssistantTool;
        this.objectMapper = objectMapper;
    }

    @Override
    public AssistantPreparedResult prepare(Long userId, AssistantSession session, String userMessage, String modelName) {
        List<MemorySnippet> memories = assistantMemoryService.findRelevantMemories(userId, userMessage, 3);
        AssistantToolPlan toolPlan = toolPlanner.plan(session, userMessage, modelName, null);
        AssistantStructuredIntent structuredIntent = mergePlanIntoStructuredIntent(AssistantStructuredIntent.empty(), toolPlan);
        if (toolPlan == null || !toolPlan.hasUsablePlan()) {
            structuredIntent = structuredIntentExtractor.extract(userMessage, modelName);
            toolPlan = AssistantToolPlan.empty();
        }
        InteractionMode interactionMode = resolveInteractionMode(session, userMessage, structuredIntent, toolPlan);
        List<MemoryUsage> usedMemories = memories.stream()
                .map(memory -> new MemoryUsage(
                        memory.id(),
                        memory.memoryScope(),
                        memory.memoryType(),
                        memory.topicName(),
                        memory.summaryText()
                ))
                .toList();

        WorkflowResolution workflowResolution = resolvePendingAction(
                userId,
                session,
                userMessage,
                structuredIntent,
                interactionMode
        );
        if (!workflowResolution.handled()) {
            workflowResolution = switch (interactionMode) {
                case TASK_CREATE -> handleTaskCreationWorkflow(userId, session, userMessage, modelName, structuredIntent);
                case STUDY_QA -> handleStudyQaWorkflow(userId, session, userMessage, modelName, structuredIntent);
                case CONTEXT_CHALLENGE -> handleContextChallengeWorkflow(userId, session, userMessage, structuredIntent);
                case UNSUPPORTED -> buildUnsupportedResolution(structuredIntent);
                default -> WorkflowResolution.notHandled();
            };
        }
        if (!workflowResolution.handled()) {
            workflowResolution = resolvePlannerDirectAction(toolPlan);
        }
        if (workflowResolution.handled()) {
            return buildPreparedResult(
                    session,
                    userMessage,
                    memories,
                    workflowResolution.executions(),
                    usedMemories,
                    workflowResolution.useModel(),
                    workflowResolution.replyText(),
                    buildPlanSnapshot(workflowResolution.planSnapshot(), toolPlan),
                    interactionMode.name(),
                    resolveModelName(modelName)
            );
        }

        WorkflowResolution plannerToolResolution = executePlannerToolPlan(
                userId,
                session,
                userMessage,
                modelName,
                structuredIntent,
                toolPlan,
                interactionMode
        );
        if (plannerToolResolution.handled()) {
            return buildPreparedResult(
                    session,
                    userMessage,
                    memories,
                    plannerToolResolution.executions(),
                    usedMemories,
                    plannerToolResolution.useModel(),
                    plannerToolResolution.replyText(),
                    buildPlanSnapshot(plannerToolResolution.planSnapshot(), toolPlan),
                    interactionMode.name(),
                    resolveModelName(modelName)
            );
        }

        List<PlannedTool> plan = planTools(session, userMessage, structuredIntent, interactionMode);
        List<AssistantTool.ToolExecutionResult> executions = executeTools(userId, session, userMessage, modelName, structuredIntent, plan);
        return buildPreparedResult(
                session,
                userMessage,
                memories,
                executions,
                usedMemories,
                shouldUseAiModelForMode(interactionMode),
                null,
                buildPlanSnapshot(plan, toolPlan),
                interactionMode.name(),
                resolveModelName(modelName)
        );
    }

    @Override
    public AssistantAgentResult respond(Long userId, AssistantSession session, String userMessage, String modelName) {
        AssistantPreparedResult preparedResult = prepare(userId, session, userMessage, modelName);
        String assistantReply = preparedResult.fallbackReply();
        if (preparedResult.useModel()) {
            try {
                assistantReply = aiChatService.chat(
                        preparedResult.systemPrompt(),
                        preparedResult.userPrompt(),
                        preparedResult.modelName(),
                        0.3
                );
            } catch (Exception ignored) {
                assistantReply = preparedResult.fallbackReply();
            }
        }
        captureConversationMemory(userId, session, userMessage, assistantReply);
        return new AssistantAgentResult(
                assistantReply,
                preparedResult.reasoningJson(),
                preparedResult.toolPlanJson(),
                preparedResult.modelName(),
                preparedResult.toolExecutions(),
                preparedResult.usedMemories()
        );
    }

    @Override
    public void captureConversationMemory(Long userId, AssistantSession session, String userMessage, String assistantReply) {
        assistantMemoryService.captureConversationMemory(userId, session, userMessage, assistantReply);
    }

    private AssistantPreparedResult buildPreparedResult(
            AssistantSession session,
            String userMessage,
            List<MemorySnippet> memories,
            List<AssistantTool.ToolExecutionResult> executions,
            List<MemoryUsage> usedMemories,
            boolean useModel,
            String fallbackReplyOverride,
            Object planSnapshot,
            String interactionMode,
            String resolvedModelName
    ) {
        return new AssistantPreparedResult(
                useModel,
                buildSystemPrompt(),
                buildUserPrompt(session, userMessage, memories, executions, interactionMode),
                StringUtils.hasText(fallbackReplyOverride)
                        ? fallbackReplyOverride
                        : buildFallbackReply(session, memories, executions),
                toJson(Map.of(
                        "strategy", executions.isEmpty() ? "DIRECT_REPLY" : "TOOL_AUGMENTED",
                        "interactionMode", interactionMode,
                        "memoryCount", memories.size(),
                        "toolCount", executions.size()
                )),
                toJson(planSnapshot == null ? List.of() : planSnapshot),
                resolvedModelName,
                executions,
                usedMemories
        );
    }

    private AssistantStructuredIntent mergePlanIntoStructuredIntent(
            AssistantStructuredIntent structuredIntent,
            AssistantToolPlan toolPlan
    ) {
        AssistantStructuredIntent effectiveIntent = structuredIntent == null
                ? AssistantStructuredIntent.empty()
                : structuredIntent;
        if (toolPlan == null || !toolPlan.hasUsablePlan()) {
            return effectiveIntent;
        }
        if (!StringUtils.hasText(effectiveIntent.getInteractionMode()) && StringUtils.hasText(toolPlan.getInteractionMode())) {
            effectiveIntent.setInteractionMode(toolPlan.getInteractionMode());
        }
        if (!StringUtils.hasText(effectiveIntent.getUnsupportedFeature()) && StringUtils.hasText(toolPlan.getUnsupportedFeature())) {
            effectiveIntent.setUnsupportedFeature(toolPlan.getUnsupportedFeature());
        }
        for (AssistantToolPlan.ToolCall toolCall : toolPlan.getToolCalls()) {
            mergeToolCallArguments(effectiveIntent, toolCall);
        }
        return effectiveIntent;
    }

    private void mergeToolCallArguments(AssistantStructuredIntent intent, AssistantToolPlan.ToolCall toolCall) {
        if (intent == null || toolCall == null || !StringUtils.hasText(toolCall.getToolName())) {
            return;
        }
        Map<String, Object> args = toolCall.getArguments() == null ? Map.of() : toolCall.getArguments();
        String toolName = toolCall.getToolName();
        if ("material.search".equals(toolName)) {
            setMaterialQueryIfPresent(intent, readTextArg(args, "queryText"));
            return;
        }
        if ("material.list".equals(toolName)) {
            intent.setMaterialBrowse(true);
            setMaterialQueryIfPresent(intent, readTextArg(args, "keyword"));
            Boolean embeddingReadyOnly = readBooleanArg(args, "embeddingReadyOnly");
            if (embeddingReadyOnly != null) {
                intent.setEmbeddingReadyOnly(embeddingReadyOnly);
            }
            return;
        }
        if ("material.chapter_outline".equals(toolName)) {
            intent.setChapterBrowse(true);
            setMaterialQueryIfPresent(intent, readTextArg(args, "materialQuery"));
            setIfPresent(args, "chapterKeyword", intent::setChapterKeyword);
            return;
        }
        if ("task.list".equals(toolName)) {
            intent.setTaskList(true);
            setIfPresent(args, "taskTypeFilter", intent::setTaskTypeFilter);
            setIfPresent(args, "taskStatusFilter", intent::setTaskStatusFilter);
            return;
        }
        if ("question_set.list".equals(toolName)) {
            intent.setQuestionSetList(true);
            setIfPresent(args, "keyword", intent::setQuestionSetKeyword);
            setIfPresent(args, "status", intent::setQuestionSetStatus);
            Integer difficultyLevel = readIntegerArg(args, "difficultyLevel");
            if (difficultyLevel != null) {
                intent.setQuestionSetDifficultyLevel(difficultyLevel);
            }
            return;
        }
        if ("task.submit_summary".equals(toolName)) {
            addRequestedTaskType(intent, "SUMMARY");
            setMaterialQueryIfPresent(intent, readTextArg(args, "materialQuery"));
            return;
        }
        if ("task.submit_question_generate".equals(toolName)) {
            addRequestedTaskType(intent, "QUESTION_GENERATE");
            setMaterialQueryIfPresent(intent, readTextArg(args, "materialQuery"));
            setIfPresent(args, "exclusiveQuestionType", intent::setExclusiveQuestionType);
            setIntegerIfPresent(args, "questionCount", intent::setQuestionCount);
            setIntegerIfPresent(args, "singleCount", intent::setSingleCount);
            setIntegerIfPresent(args, "judgeCount", intent::setJudgeCount);
            setIntegerIfPresent(args, "shortAnswerCount", intent::setShortAnswerCount);
            setIntegerIfPresent(args, "difficultyLevel", intent::setDifficultyLevel);
        }
    }

    private WorkflowResolution resolvePlannerDirectAction(AssistantToolPlan toolPlan) {
        if (toolPlan == null || !toolPlan.hasUsablePlan()) {
            return WorkflowResolution.notHandled();
        }
        String replyStrategy = normalizeText(toolPlan.getReplyStrategy());
        if ("UNSUPPORTED".equals(replyStrategy)) {
            String unsupportedFeature = toolPlan.getUnsupportedFeature();
            String replyText = StringUtils.hasText(toolPlan.getDirectReply())
                    ? toolPlan.getDirectReply()
                    : (StringUtils.hasText(unsupportedFeature)
                    ? "目前我还没有“%s”这个功能。你可以先让我做资料检索、知识讲解、AI 总结、AI 出题、任务查询这些系统内能力。".formatted(unsupportedFeature)
                    : "目前我还没有这个功能。你可以先让我做资料检索、知识讲解、AI 总结、AI 出题、任务查询这些系统内能力。");
            return new WorkflowResolution(true, false, List.of(), replyText, toolPlan);
        }
        if ("ASK_CLARIFICATION".equals(replyStrategy)
                || (toolPlan.getMissingSlots() != null && !toolPlan.getMissingSlots().isEmpty())) {
            String replyText = StringUtils.hasText(toolPlan.getClarificationPrompt())
                    ? toolPlan.getClarificationPrompt()
                    : "我还需要你补充这些信息：" + String.join("、", toolPlan.getMissingSlots());
            return new WorkflowResolution(true, false, List.of(), replyText, toolPlan);
        }
        if ("DIRECT_REPLY".equals(replyStrategy) && StringUtils.hasText(toolPlan.getDirectReply())) {
            return new WorkflowResolution(true, false, List.of(), toolPlan.getDirectReply(), toolPlan);
        }
        return WorkflowResolution.notHandled();
    }

    private Object buildPlanSnapshot(Object executionPlan, AssistantToolPlan toolPlan) {
        if (toolPlan == null || !toolPlan.hasUsablePlan()) {
            return executionPlan;
        }
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planner", toolPlan);
        snapshot.put("executionPlan", executionPlan == null ? List.of() : executionPlan);
        return snapshot;
    }

    private InteractionMode resolveInteractionMode(
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantToolPlan toolPlan
    ) {
        InteractionMode plannedMode = normalizeInteractionMode(toolPlan == null ? null : toolPlan.getInteractionMode());
        InteractionMode extractedMode = normalizeInteractionMode(structuredIntent == null ? null : structuredIntent.getInteractionMode());
        AssistantPendingActionPayload pendingPayload = readPendingActionPayload(session);
        String pendingActionType = session == null ? null : session.getPendingActionType();

        if (taskIntentParser.looksLikeMaterialAmbiguityChallenge(userMessage)
                || Boolean.TRUE.equals(structuredIntent == null ? null : structuredIntent.getMaterialDisambiguation())
                || Boolean.TRUE.equals(structuredIntent == null ? null : structuredIntent.getContextChallenge())) {
            return InteractionMode.CONTEXT_CHALLENGE;
        }
        if ("QUESTION_CONFIG".equalsIgnoreCase(pendingActionType)
                && looksLikeQuestionConfigReply(userMessage, structuredIntent)) {
            return InteractionMode.TASK_CONFIG_REPLY;
        }
        if ("MATERIAL_SELECTION".equalsIgnoreCase(pendingActionType)
                && looksLikeMaterialSelectionReply(userMessage, pendingPayload, structuredIntent)) {
            return InteractionMode.MATERIAL_SELECTION;
        }
        if (plannedMode == InteractionMode.TASK_CREATE && !looksLikeExplicitTaskRequest(userMessage, structuredIntent)) {
            plannedMode = InteractionMode.UNKNOWN;
        }
        if (plannedMode != InteractionMode.UNKNOWN) {
            return plannedMode;
        }
        if (extractedMode == InteractionMode.TASK_CREATE && !looksLikeExplicitTaskRequest(userMessage, structuredIntent)) {
            if (looksLikeStudyQaMessage(session, userMessage, structuredIntent)) {
                return InteractionMode.STUDY_QA;
            }
            if (looksLikeCasualChat(userMessage)) {
                return InteractionMode.CHAT;
            }
        }
        if (extractedMode != InteractionMode.UNKNOWN) {
            return extractedMode;
        }
        if (looksLikeCasualChat(userMessage)) {
            return InteractionMode.CHAT;
        }
        if (looksLikeExplicitTaskRequest(userMessage, structuredIntent)) {
            return InteractionMode.TASK_CREATE;
        }
        if (taskIntentParser.looksLikeMaterialBrowseRequest(userMessage, structuredIntent)) {
            return InteractionMode.MATERIAL_BROWSE;
        }
        if (taskIntentParser.looksLikeTaskListRequest(userMessage, structuredIntent)) {
            return InteractionMode.TASK_BROWSE;
        }
        if (taskIntentParser.looksLikeQuestionSetListRequest(userMessage, structuredIntent)) {
            return InteractionMode.QUESTION_SET_BROWSE;
        }
        if (taskIntentParser.looksLikeChapterBrowseRequest(userMessage, structuredIntent)) {
            return InteractionMode.CHAPTER_BROWSE;
        }
        if (looksLikeStudyQaMessage(session, userMessage, structuredIntent)) {
            return InteractionMode.STUDY_QA;
        }
        return InteractionMode.UNKNOWN;
    }

    private InteractionMode normalizeInteractionMode(String rawMode) {
        if (!StringUtils.hasText(rawMode)) {
            return InteractionMode.UNKNOWN;
        }
        try {
            return InteractionMode.valueOf(rawMode.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return InteractionMode.UNKNOWN;
        }
    }

    private boolean looksLikeExplicitTaskRequest(String userMessage, AssistantStructuredIntent structuredIntent) {
        if (looksLikeKnowledgeAnswerRequest(userMessage) && !looksLikeExplicitTaskCommand(userMessage)) {
            return false;
        }
        return !taskIntentParser.resolveRequestedTaskTypes(userMessage, structuredIntent).isEmpty();
    }

    private boolean looksLikeExplicitTaskCommand(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        return taskIntentParser.looksLikeQuestionRequest(userMessage)
                || AssistantToolSupport.containsAnyIgnoreCase(userMessage, EXPLICIT_SUMMARY_TASK_KEYWORDS);
    }

    private boolean looksLikeKnowledgeAnswerRequest(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        return AssistantToolSupport.containsAnyIgnoreCase(userMessage, STUDY_QA_HINT_KEYWORDS)
                && AssistantToolSupport.containsAnyIgnoreCase(
                userMessage,
                List.of("哪些", "有什么", "告诉我", "梳理", "整理", "列出", "讲讲", "解释", "说明", "分析", "全量", "核心", "重要")
        );
    }

    private boolean looksLikeStudyQaMessage(
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent
    ) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        if (taskIntentParser.looksLikeMaterialBrowseRequest(userMessage, structuredIntent)
                || taskIntentParser.looksLikeTaskListRequest(userMessage, structuredIntent)
                || taskIntentParser.looksLikeQuestionSetListRequest(userMessage, structuredIntent)
                || taskIntentParser.looksLikeChapterBrowseRequest(userMessage, structuredIntent)
                || looksLikeExplicitTaskRequest(userMessage, structuredIntent)
                || looksLikeCasualChat(userMessage)) {
            return false;
        }
        if (AssistantToolSupport.containsAnyIgnoreCase(userMessage, STUDY_QA_HINT_KEYWORDS)) {
            return true;
        }
        if (StringUtils.hasText(taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent))) {
            return true;
        }
        if (AssistantToolSupport.resolveMaterialId(session) != null && (userMessage.contains("？") || userMessage.contains("?"))) {
            return true;
        }
        return AssistantToolSupport.resolveMaterialId(session) != null;
    }

    private WorkflowResolution buildUnsupportedResolution(AssistantStructuredIntent structuredIntent) {
        String unsupportedFeature = structuredIntent == null ? null : structuredIntent.getUnsupportedFeature();
        String replyText = StringUtils.hasText(unsupportedFeature)
                ? "目前我还没有“%s”这个功能。你可以先让我做资料检索、知识讲解、AI 总结、AI 出题、任务查询这些系统内能力。".formatted(unsupportedFeature)
                : "目前我还没有这个功能。你可以先让我做资料检索、知识讲解、AI 总结、AI 出题、任务查询这些系统内能力。";
        return new WorkflowResolution(true, false, List.of(), replyText, List.of());
    }

    private WorkflowResolution handleStudyQaWorkflow(
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent
    ) {
        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        List<Map<String, Object>> planSnapshot = new ArrayList<>();

        Long materialId = AssistantToolSupport.resolveMaterialId(session);
        if (materialId == null) {
            materialId = resolveMaterialIdFromQuestionSetContext(userId, session);
        }

        String materialQuery = taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent);
        if (StringUtils.hasText(materialQuery)) {
            AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, materialQuery);
            executions.add(searchExecution);
            planSnapshot.add(Map.of("toolName", "material.search", "reason", "学习问答前先定位资料"));
            if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
                return new WorkflowResolution(true, false, executions, searchExecution.errorMessage(), planSnapshot);
            }

            AssistantMaterialSearchResult searchResult = readMaterialSearchResult(searchExecution.toolResultJson());
            if (searchResult != null && searchResult.getSelectedMaterialId() != null) {
                materialId = searchResult.getSelectedMaterialId();
                bindMaterialContext(session, materialId);
            } else {
                savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                        .promptText(searchExecution.summaryText())
                        .materialQuery(materialQuery)
                        .followUpActionType("STUDY_QA")
                        .followUpUserMessage(userMessage)
                        .materialCandidates(searchResult == null ? List.of() : searchResult.getCandidates())
                        .build());
                return new WorkflowResolution(true, false, executions, searchExecution.summaryText(), planSnapshot);
            }
        }

        if (materialId == null) {
            RecentContextResolution recentContext = resolveRecentContext(userId);
            if (recentContext != null) {
                materialId = recentContext.materialId();
                bindRecentContext(session, recentContext);
                executions.add(infoExecution(
                        "context.resolve_recent",
                        recentContext.args(),
                        recentContext.result(),
                        recentContext.summaryText()
                ));
                planSnapshot.add(Map.of("toolName", "context.resolve_recent", "reason", "学习问答未显式指定资料，自动绑定最近学习内容"));
            }
        }

        if (materialId == null) {
            String replyText = "我可以先带你学，不过还需要先知道你想基于哪份资料。你可以直接告诉我资料标题，或者从某份资料页进入后再继续问我。";
            executions.add(waitingExecution(
                    "material.search",
                    Map.of("reason", "material_context_missing"),
                    Map.of(),
                    replyText
            ));
            planSnapshot.add(Map.of("toolName", "material.search", "reason", "学习问答需要先定位资料"));
            return new WorkflowResolution(true, false, executions, replyText, planSnapshot);
        }

        String effectiveQuery = resolveStudyQaQuery(userMessage, materialQuery);
        String toolMessage = StringUtils.hasText(effectiveQuery) ? effectiveQuery : userMessage;
        String toolName = isMaterialDetailQuestion(userMessage) ? "material.detail" : "rag.retrieve";
        planSnapshot.add(Map.of("toolName", toolName, "reason", "围绕当前资料回答学习问题"));
        AssistantTool.ToolExecutionResult execution = executeToolByName(
                toolName,
                userId,
                session,
                toolMessage,
                modelName,
                structuredIntent
        );
        if (execution != null) {
            executions.add(execution);
        }
        return new WorkflowResolution(true, shouldUseAiModel(), executions, null, planSnapshot);
    }

    private WorkflowResolution handleContextChallengeWorkflow(
            Long userId,
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent
    ) {
        String materialQuery = taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent);
        if (!StringUtils.hasText(materialQuery)) {
            String replyText = "你说得对，当前还没有足够信息让我唯一定位到资料。你可以直接告诉我资料 ID、回复候选序号，或者把资料标题再说完整一点。";
            return new WorkflowResolution(true, false, List.of(), replyText, List.of());
        }

        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        List<Map<String, Object>> planSnapshot = new ArrayList<>();
        AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, materialQuery);
        executions.add(searchExecution);
        planSnapshot.add(Map.of("toolName", "material.search", "reason", "用户在质疑或澄清当前资料定位"));
        if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
            return new WorkflowResolution(true, false, executions, searchExecution.errorMessage(), planSnapshot);
        }

        AssistantMaterialSearchResult searchResult = readMaterialSearchResult(searchExecution.toolResultJson());
        if (searchResult != null && searchResult.getSelectedMaterialId() != null) {
            bindMaterialContext(session, searchResult.getSelectedMaterialId());
        } else {
            savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                    .promptText(searchExecution.summaryText())
                    .materialQuery(materialQuery)
                    .materialCandidates(searchResult == null ? List.of() : searchResult.getCandidates())
                    .build());
        }
        return new WorkflowResolution(true, false, executions, searchExecution.summaryText(), planSnapshot);
    }

    private WorkflowResolution handleTaskCreationWorkflow(
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent
    ) {
        List<String> requestedTaskTypes = taskIntentParser.resolveRequestedTaskTypes(userMessage, structuredIntent);
        if (requestedTaskTypes.isEmpty()) {
            return WorkflowResolution.notHandled();
        }

        List<AssistantPlannedTask> plannedTasks = buildPlannedTasks(userMessage, modelName, requestedTaskTypes, structuredIntent);
        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        List<Map<String, Object>> planSnapshot = new ArrayList<>();

        Long materialId = AssistantToolSupport.resolveMaterialId(session);
        if (materialId == null) {
            materialId = resolveMaterialIdFromQuestionSetContext(userId, session);
        }
        if (materialId == null) {
            String materialQuery = taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent);
            if (!StringUtils.hasText(materialQuery)) {
                RecentContextResolution recentContext = resolveRecentContext(userId);
                if (recentContext != null) {
                    materialId = recentContext.materialId();
                    bindRecentContext(session, recentContext);
                    executions.add(infoExecution(
                            "context.resolve_recent",
                            recentContext.args(),
                            recentContext.result(),
                            recentContext.summaryText()
                    ));
                    planSnapshot.add(Map.of("toolName", "context.resolve_recent", "reason", "用户未显式指定资料，自动绑定最近学习内容"));
                } else {
                    String promptText = "我先需要知道你说的是哪份资料。你可以直接告诉我资料标题，或者说一个更明显的关键词，比如“Docker 入门”或“Java 核心”。";
                    savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                            .promptText(promptText)
                            .tasks(plannedTasks)
                            .build());
                    AssistantTool.ToolExecutionResult waitingExecution = waitingExecution(
                            "material.search",
                            Map.of(),
                            Map.of("reason", "material_query_missing"),
                            promptText
                    );
                    executions.add(waitingExecution);
                    planSnapshot.add(Map.of("toolName", "material.search", "reason", "需要先定位资料"));
                    return new WorkflowResolution(true, false, executions, null, planSnapshot);
                }
            }

            if (materialId == null) {
                AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, materialQuery);
                executions.add(searchExecution);
                planSnapshot.add(Map.of("toolName", "material.search", "reason", "先根据标题关键词定位资料"));

                if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
                    return new WorkflowResolution(true, false, executions, searchExecution.errorMessage(), planSnapshot);
                }

                AssistantMaterialSearchResult searchResult = readMaterialSearchResult(searchExecution.toolResultJson());
                if (searchResult != null && searchResult.getSelectedMaterialId() != null) {
                    materialId = searchResult.getSelectedMaterialId();
                    bindMaterialContext(session, materialId);
                    clearPendingAction(session);
                } else {
                    savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                            .promptText(searchExecution.summaryText())
                            .materialQuery(materialQuery)
                            .materialCandidates(searchResult == null ? List.of() : searchResult.getCandidates())
                            .tasks(plannedTasks)
                            .build());
                    return new WorkflowResolution(true, false, executions, searchExecution.summaryText(), planSnapshot);
                }
            }
        }

        WorkflowResolution executionResolution = executePlannedTasks(userId, session, materialId, plannedTasks, executions, planSnapshot);
        return executionResolution.handled()
                ? executionResolution
                : new WorkflowResolution(true, false, executions, null, planSnapshot);
    }

    private WorkflowResolution resolvePendingAction(
            Long userId,
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            InteractionMode interactionMode
    ) {
        if (session == null || !StringUtils.hasText(session.getPendingActionType())) {
            return WorkflowResolution.notHandled();
        }
        AssistantPendingActionPayload payload = readPendingActionPayload(session);
        if (payload == null) {
            clearPendingAction(session);
            return WorkflowResolution.notHandled();
        }

        String pendingActionType = session.getPendingActionType().trim().toUpperCase();
        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        List<Map<String, Object>> planSnapshot = new ArrayList<>();

        if ("MATERIAL_SELECTION".equals(pendingActionType)) {
            Long selectedMaterialId = taskIntentParser.resolveMaterialCandidateSelection(
                    userMessage,
                    payload.getMaterialCandidates(),
                    structuredIntent
            );
            if (selectedMaterialId == null) {
                String materialQuery = taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent);
                if (StringUtils.hasText(materialQuery)) {
                    AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, materialQuery);
                    executions.add(searchExecution);
                    planSnapshot.add(Map.of("toolName", "material.search", "reason", "根据用户补充的资料标题继续定位"));
                    if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
                        return new WorkflowResolution(true, false, executions, searchExecution.errorMessage(), planSnapshot);
                    }

                    AssistantMaterialSearchResult searchResult = readMaterialSearchResult(searchExecution.toolResultJson());
                    if (searchResult != null && searchResult.getSelectedMaterialId() != null) {
                        Long searchedMaterialId = searchResult.getSelectedMaterialId();
                        bindMaterialContext(session, searchedMaterialId);
                        String searchedMaterialTitle = searchResult.getSelectedMaterialTitle();
                        String selectionSummary = StringUtils.hasText(searchedMaterialTitle)
                                ? "已确认资料《%s》，后续我会按这份资料继续。".formatted(searchedMaterialTitle)
                                : "已确认当前资料，后续我会按这份资料继续。";
                        executions.add(infoExecution(
                                "material.select",
                                Map.of("materialId", searchedMaterialId),
                                buildMaterialSelectionResult(searchedMaterialId, searchedMaterialTitle),
                                selectionSummary
                        ));
                        clearPendingAction(session);
                        WorkflowResolution followUpResolution = executePendingFollowUp(userId, session, payload, executions, planSnapshot);
                        if (followUpResolution.handled()) {
                            return followUpResolution;
                        }
                        if (payload.getTasks() == null || payload.getTasks().isEmpty()) {
                            return new WorkflowResolution(true, false, executions, selectionSummary, planSnapshot);
                        }
                        return executePlannedTasks(userId, session, searchedMaterialId, payload.getTasks(), executions, planSnapshot);
                    }

                    AssistantPendingActionPayload updatedPayload = AssistantPendingActionPayload.builder()
                            .promptText(searchExecution.summaryText())
                            .materialQuery(materialQuery)
                            .materialCandidates(searchResult == null ? List.of() : searchResult.getCandidates())
                            .followUpActionType(payload.getFollowUpActionType())
                            .followUpUserMessage(payload.getFollowUpUserMessage())
                            .chapterKeyword(payload.getChapterKeyword())
                            .tasks(payload.getTasks())
                            .build();
                    savePendingAction(session, "MATERIAL_SELECTION", updatedPayload);
                    return new WorkflowResolution(true, false, executions, searchExecution.summaryText(), planSnapshot);
                }
                if (shouldBypassPendingAction(pendingActionType, interactionMode)) {
                    clearPendingAction(session);
                    return WorkflowResolution.notHandled();
                }
                String promptText = StringUtils.hasText(payload.getPromptText())
                        ? payload.getPromptText()
                        : "我还没确认你指的是哪份资料。你可以直接回复序号、资料ID（例如 #7），或者把资料标题说完整一点。";
                executions.add(waitingExecution(
                        "material.search",
                        Map.of("pending", true),
                        payload,
                        promptText
                ));
                planSnapshot.add(Map.of("toolName", "material.search", "reason", "等待用户确认具体资料"));
                return new WorkflowResolution(true, false, executions, promptText, planSnapshot);
            }

            bindMaterialContext(session, selectedMaterialId);
            String selectedMaterialTitle = resolveMaterialTitle(payload, selectedMaterialId);
            String selectionSummary = StringUtils.hasText(selectedMaterialTitle)
                    ? "已确认资料《%s》，后续我会按这份资料继续。".formatted(selectedMaterialTitle)
                    : "已确认当前资料，后续我会按这份资料继续。";
            executions.add(infoExecution(
                    "material.select",
                    Map.of("materialId", selectedMaterialId),
                    buildMaterialSelectionResult(selectedMaterialId, selectedMaterialTitle),
                    selectionSummary
            ));
            clearPendingAction(session);
            WorkflowResolution followUpResolution = executePendingFollowUp(userId, session, payload, executions, planSnapshot);
            if (followUpResolution.handled()) {
                return followUpResolution;
            }
            if (payload.getTasks() == null || payload.getTasks().isEmpty()) {
                return new WorkflowResolution(true, false, executions, selectionSummary, planSnapshot);
            }
            return executePlannedTasks(userId, session, selectedMaterialId, payload.getTasks(), executions, planSnapshot);
        }

        if ("QUESTION_CONFIG".equals(pendingActionType)) {
            AssistantPlannedTask pendingQuestionTask = payload.getTasks().isEmpty() ? null : payload.getTasks().get(0);
            WorkflowResolution materialDisambiguationResolution = resolveMaterialDisambiguationDuringPendingQuestionConfig(
                    userId,
                    session,
                    userMessage,
                    structuredIntent,
                    payload,
                    executions,
                    planSnapshot
            );
            if (materialDisambiguationResolution.handled()) {
                return materialDisambiguationResolution;
            }
            AssistantTaskIntentParser.QuestionConfigResolution resolution =
                    taskIntentParser.resolveQuestionConfigReply(userMessage, pendingQuestionTask, structuredIntent);
            if (!resolution.resolved()) {
                if (shouldBypassPendingAction(pendingActionType, interactionMode)) {
                    clearPendingAction(session);
                    return WorkflowResolution.notHandled();
                }
                String promptText = StringUtils.hasText(resolution.promptText())
                        ? resolution.promptText()
                        : taskIntentParser.buildQuestionConfigPrompt(pendingQuestionTask);
                payload.setPromptText(promptText);
                savePendingAction(session, "QUESTION_CONFIG", payload);
                executions.add(waitingExecution(
                        "task.submit_question_generate",
                        Map.of("pending", true),
                        payload,
                        promptText
                ));
                planSnapshot.add(Map.of("toolName", "task.submit_question_generate", "reason", "等待用户补充题型配置"));
                return new WorkflowResolution(true, false, executions, promptText, planSnapshot);
            }

            clearPendingAction(session);
            Long materialId = payload.getMaterialId() != null ? payload.getMaterialId() : AssistantToolSupport.resolveMaterialId(session);
            if (materialId != null) {
                bindMaterialContext(session, materialId);
            }
            return executePlannedTasks(userId, session, materialId, List.of(resolution.task()), executions, planSnapshot);
        }

        clearPendingAction(session);
        return WorkflowResolution.notHandled();
    }

    private WorkflowResolution executePlannedTasks(
            Long userId,
            AssistantSession session,
            Long materialId,
            List<AssistantPlannedTask> tasks,
            List<AssistantTool.ToolExecutionResult> executions,
            List<Map<String, Object>> planSnapshot
    ) {
        if (materialId == null || tasks == null || tasks.isEmpty()) {
            return new WorkflowResolution(true, false, executions, null, planSnapshot);
        }

        for (AssistantPlannedTask task : tasks) {
            if (task == null || !StringUtils.hasText(task.getTaskType())) {
                continue;
            }
            String taskType = task.getTaskType().trim().toUpperCase();
            if ("SUMMARY".equals(taskType)) {
                planSnapshot.add(Map.of("toolName", "task.submit_summary", "reason", "按当前资料提交总结任务"));
                SummaryGenerateRequest request = new SummaryGenerateRequest();
                request.setModelName(task.getModelName());
                request.setSummaryType(task.getSummaryType());
                request.setSaveAsNote(task.getSaveAsNote());
                request.setTemperature(task.getTemperature());
                executions.add(summaryTaskAssistantTool.executeRequest(userId, materialId, request));
                continue;
            }
            if ("QUESTION_GENERATE".equals(taskType)) {
                if (Boolean.TRUE.equals(task.getRequiresQuestionTypeConfirmation())) {
                    String promptText = taskIntentParser.buildQuestionConfigPrompt(task);
                    AssistantPendingActionPayload payload = AssistantPendingActionPayload.builder()
                            .promptText(promptText)
                            .materialId(materialId)
                            .tasks(List.of(task))
                            .build();
                    savePendingAction(session, "QUESTION_CONFIG", payload);
                    planSnapshot.add(Map.of("toolName", "task.submit_question_generate", "reason", "等待用户确认题型数量"));
                    executions.add(waitingExecution(
                            "task.submit_question_generate",
                            Map.of("materialId", materialId, "questionCount", task.getQuestionCount()),
                            payload,
                            promptText
                    ));
                    return new WorkflowResolution(true, false, executions, null, planSnapshot);
                }

                planSnapshot.add(Map.of("toolName", "task.submit_question_generate", "reason", "按当前资料提交出题任务"));
                QuestionGenerateRequest request = new QuestionGenerateRequest();
                request.setModelName(task.getModelName());
                request.setQuestionCount(task.getQuestionCount());
                request.setSingleCount(task.getSingleCount());
                request.setJudgeCount(task.getJudgeCount());
                request.setShortAnswerCount(task.getShortAnswerCount());
                request.setDifficultyLevel(task.getDifficultyLevel());
                executions.add(questionGenerateTaskAssistantTool.executeRequest(
                        userId,
                        materialId,
                        request,
                        task.getAdjustmentNote()
                ));
            }
        }
        clearPendingAction(session);
        return new WorkflowResolution(true, false, executions, null, planSnapshot);
    }

    private WorkflowResolution executePendingFollowUp(
            Long userId,
            AssistantSession session,
            AssistantPendingActionPayload payload,
            List<AssistantTool.ToolExecutionResult> executions,
            List<Map<String, Object>> planSnapshot
    ) {
        if (payload == null || !StringUtils.hasText(payload.getFollowUpActionType())) {
            return WorkflowResolution.notHandled();
        }
        String followUpActionType = payload.getFollowUpActionType().trim().toUpperCase();
        if ("CHAPTER_BROWSE".equals(followUpActionType)) {
            planSnapshot.add(Map.of("toolName", "material.chapter_outline", "reason", "已确认资料，继续展开章节目录"));
            AssistantStructuredIntent structuredIntent = AssistantStructuredIntent.builder()
                    .chapterBrowse(true)
                    .chapterKeyword(payload.getChapterKeyword())
                    .build();
            AssistantTool.ToolExecutionResult execution = materialChapterOutlineAssistantTool.execute(
                    new AssistantTool.ToolContext(
                            userId,
                            session,
                            StringUtils.hasText(payload.getChapterKeyword()) ? payload.getChapterKeyword() : "查看章节目录",
                            null,
                            structuredIntent
                    )
            );
            executions.add(execution);
            return new WorkflowResolution(true, false, executions, execution.summaryText(), planSnapshot);
        }
        if ("STUDY_QA".equals(followUpActionType)) {
            String followUpMessage = StringUtils.hasText(payload.getFollowUpUserMessage())
                    ? payload.getFollowUpUserMessage()
                    : "继续回答当前资料问题";
            String effectiveQuery = resolveStudyQaQuery(followUpMessage, payload.getMaterialQuery());
            String toolMessage = StringUtils.hasText(effectiveQuery) ? effectiveQuery : followUpMessage;
            String toolName = isMaterialDetailQuestion(followUpMessage) ? "material.detail" : "rag.retrieve";
            String reason = "已确认资料，继续回答用户刚才的问题";
            AssistantTool.ToolExecutionResult execution = executeToolByName(
                    toolName,
                    userId,
                    session,
                    toolMessage,
                    null,
                    AssistantStructuredIntent.builder().build()
            );
            if (execution == null) {
                return new WorkflowResolution(true, false, executions, "资料已确认，但我这边暂时还没继续执行后续回答。你可以再问我一次。", planSnapshot);
            }
            planSnapshot.add(Map.of("toolName", toolName, "reason", reason));
            executions.add(execution);
            return new WorkflowResolution(true, shouldUseAiModel(), executions, null, planSnapshot);
        }
        return WorkflowResolution.notHandled();
    }

    private List<AssistantPlannedTask> buildPlannedTasks(
            String userMessage,
            String modelName,
            List<String> requestedTaskTypes,
            AssistantStructuredIntent structuredIntent
    ) {
        List<AssistantPlannedTask> tasks = new ArrayList<>();
        for (String taskType : requestedTaskTypes) {
            if ("SUMMARY".equalsIgnoreCase(taskType)) {
                AssistantTaskIntentParser.SummaryTaskOptions options =
                        taskIntentParser.parseSummaryRequest(userMessage, modelName, structuredIntent);
                tasks.add(AssistantPlannedTask.builder()
                        .taskType("SUMMARY")
                        .modelName(options.modelName())
                        .summaryType(options.summaryType())
                        .saveAsNote(options.saveAsNote())
                        .temperature(options.temperature())
                        .build());
                continue;
            }
            if ("QUESTION_GENERATE".equalsIgnoreCase(taskType)) {
                AssistantTaskIntentParser.QuestionTaskOptions options =
                        taskIntentParser.parseQuestionRequest(userMessage, modelName, structuredIntent);
                tasks.add(AssistantPlannedTask.builder()
                        .taskType("QUESTION_GENERATE")
                        .modelName(options.modelName())
                        .questionCount(options.questionCount())
                        .singleCount(options.singleCount())
                        .judgeCount(options.judgeCount())
                        .shortAnswerCount(options.shortAnswerCount())
                        .difficultyLevel(options.difficultyLevel())
                        .adjustmentNote(options.adjustmentNote())
                        .requiresQuestionTypeConfirmation(options.requiresQuestionTypeConfirmation())
                        .build());
            }
        }
        return tasks;
    }

    private List<PlannedTool> planTools(
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            InteractionMode interactionMode
    ) {
        LinkedHashSet<String> toolNames = new LinkedHashSet<>();
        List<PlannedTool> plan = new ArrayList<>();
        String normalizedMessage = userMessage == null ? "" : userMessage.trim();

        if (interactionMode == InteractionMode.MATERIAL_BROWSE) {
            addPlan(plan, toolNames, "material.list", "用户想查看当前资料列表或按关键词找资料");
        }
        if (interactionMode == InteractionMode.TASK_BROWSE) {
            if (AssistantToolSupport.resolveTaskId(session, normalizedMessage) != null
                    && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, TASK_STATUS_KEYWORDS)) {
                addPlan(plan, toolNames, "task.get_status", "用户正在询问任务进度");
            } else {
                addPlan(plan, toolNames, "task.list", "用户想查看任务列表或按状态筛选任务");
            }
        }
        if (interactionMode == InteractionMode.QUESTION_SET_BROWSE) {
            addPlan(plan, toolNames, "question_set.list", "用户想查看题集列表或题集筛选结果");
        }
        if (interactionMode == InteractionMode.CHAPTER_BROWSE) {
            addPlan(plan, toolNames, "material.chapter_outline", "用户想查看资料章节、目录或某一章的位置");
        }
        if (AssistantToolSupport.resolvePracticeSessionId(session) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, PRACTICE_KEYWORDS)) {
            addPlan(plan, toolNames, "practice.detail", "当前会话绑定了练习记录");
        }
        if (interactionMode != InteractionMode.QUESTION_SET_BROWSE
                && AssistantToolSupport.resolveQuestionSetId(session) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, QUESTION_SET_KEYWORDS)) {
            addPlan(plan, toolNames, "question_set.detail", "当前会话绑定了题集");
        }
        if (AssistantToolSupport.resolveMaterialId(session) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, MATERIAL_DETAIL_KEYWORDS)) {
            addPlan(plan, toolNames, "material.detail", "用户更像在问当前资料基本信息");
        }
        return plan.stream().limit(4).toList();
    }

    private List<AssistantTool.ToolExecutionResult> executeTools(
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent,
            List<PlannedTool> plan
    ) {
        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        AssistantTool.ToolContext toolContext = new AssistantTool.ToolContext(
                userId,
                session,
                userMessage,
                modelName,
                structuredIntent
        );
        for (PlannedTool plannedTool : plan) {
            AssistantTool tool = toolRegistry.findTool(plannedTool.toolName());
            if (tool == null || !tool.supports(toolContext)) {
                continue;
            }
            executions.add(tool.execute(toolContext));
        }
        return executions;
    }

    private WorkflowResolution executePlannerToolPlan(
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent,
            AssistantToolPlan toolPlan,
            InteractionMode interactionMode
    ) {
        if (toolPlan == null || toolPlan.getToolCalls() == null || toolPlan.getToolCalls().isEmpty()) {
            return WorkflowResolution.notHandled();
        }
        List<AssistantTool.ToolExecutionResult> executions = new ArrayList<>();
        List<Map<String, Object>> planSnapshot = new ArrayList<>();
        for (AssistantToolPlan.ToolCall toolCall : toolPlan.getToolCalls()) {
            if (toolCall == null || !StringUtils.hasText(toolCall.getToolName())) {
                continue;
            }
            if (isWorkflowManagedTool(toolCall.getToolName())) {
                continue;
            }
            AssistantStructuredIntent toolIntent = cloneIntentWithToolCall(structuredIntent, toolCall);
            String toolMessage = resolveToolMessage(userMessage, toolCall);
            AssistantTool.ToolExecutionResult execution = executeToolByName(
                    toolCall.getToolName(),
                    userId,
                    session,
                    toolMessage,
                    modelName,
                    toolIntent
            );
            if (execution == null) {
                continue;
            }
            executions.add(execution);
            planSnapshot.add(Map.of(
                    "toolName", toolCall.getToolName(),
                    "reason", StringUtils.hasText(toolCall.getReason()) ? toolCall.getReason() : "LLM 工具规划"
            ));
        }
        if (executions.isEmpty()) {
            return WorkflowResolution.notHandled();
        }
        return new WorkflowResolution(
                true,
                shouldUseAiModelForMode(interactionMode),
                executions,
                null,
                planSnapshot
        );
    }

    private boolean isWorkflowManagedTool(String toolName) {
        return "task.submit_summary".equals(toolName)
                || "task.submit_question_generate".equals(toolName)
                || "material.search".equals(toolName);
    }

    private AssistantStructuredIntent cloneIntentWithToolCall(
            AssistantStructuredIntent structuredIntent,
            AssistantToolPlan.ToolCall toolCall
    ) {
        AssistantStructuredIntent cloned = AssistantStructuredIntent.builder()
                .interactionMode(structuredIntent == null ? null : structuredIntent.getInteractionMode())
                .unsupportedFeature(structuredIntent == null ? null : structuredIntent.getUnsupportedFeature())
                .requestedTaskTypes(structuredIntent == null ? new ArrayList<>() : new ArrayList<>(structuredIntent.getRequestedTaskTypes()))
                .materialQuery(structuredIntent == null ? null : structuredIntent.getMaterialQuery())
                .materialBrowse(structuredIntent == null ? null : structuredIntent.getMaterialBrowse())
                .embeddingReadyOnly(structuredIntent == null ? null : structuredIntent.getEmbeddingReadyOnly())
                .taskList(structuredIntent == null ? null : structuredIntent.getTaskList())
                .taskTypeFilter(structuredIntent == null ? null : structuredIntent.getTaskTypeFilter())
                .taskStatusFilter(structuredIntent == null ? null : structuredIntent.getTaskStatusFilter())
                .questionSetList(structuredIntent == null ? null : structuredIntent.getQuestionSetList())
                .questionSetKeyword(structuredIntent == null ? null : structuredIntent.getQuestionSetKeyword())
                .questionSetStatus(structuredIntent == null ? null : structuredIntent.getQuestionSetStatus())
                .questionSetDifficultyLevel(structuredIntent == null ? null : structuredIntent.getQuestionSetDifficultyLevel())
                .chapterBrowse(structuredIntent == null ? null : structuredIntent.getChapterBrowse())
                .chapterKeyword(structuredIntent == null ? null : structuredIntent.getChapterKeyword())
                .questionCount(structuredIntent == null ? null : structuredIntent.getQuestionCount())
                .singleCount(structuredIntent == null ? null : structuredIntent.getSingleCount())
                .judgeCount(structuredIntent == null ? null : structuredIntent.getJudgeCount())
                .shortAnswerCount(structuredIntent == null ? null : structuredIntent.getShortAnswerCount())
                .difficultyLevel(structuredIntent == null ? null : structuredIntent.getDifficultyLevel())
                .exclusiveQuestionType(structuredIntent == null ? null : structuredIntent.getExclusiveQuestionType())
                .defaultChoice(structuredIntent == null ? null : structuredIntent.getDefaultChoice())
                .questionConfigReply(structuredIntent == null ? null : structuredIntent.getQuestionConfigReply())
                .contextChallenge(structuredIntent == null ? null : structuredIntent.getContextChallenge())
                .materialDisambiguation(structuredIntent == null ? null : structuredIntent.getMaterialDisambiguation())
                .build();
        mergeToolCallArguments(cloned, toolCall);
        return cloned;
    }

    private String resolveToolMessage(String userMessage, AssistantToolPlan.ToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments() == null) {
            return userMessage;
        }
        Map<String, Object> args = toolCall.getArguments();
        String queryText = readTextArg(args, "queryText");
        if (StringUtils.hasText(queryText)) {
            return queryText;
        }
        String keyword = readTextArg(args, "keyword");
        if (StringUtils.hasText(keyword)) {
            return keyword;
        }
        String materialQuery = readTextArg(args, "materialQuery");
        if (StringUtils.hasText(materialQuery)) {
            return materialQuery;
        }
        return userMessage;
    }

    private AssistantTool.ToolExecutionResult executeToolByName(
            String toolName,
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent
    ) {
        AssistantTool tool = toolRegistry.findTool(toolName);
        if (tool == null) {
            return null;
        }
        AssistantTool.ToolContext toolContext = new AssistantTool.ToolContext(
                userId,
                session,
                userMessage,
                modelName,
                structuredIntent
        );
        if (!tool.supports(toolContext)) {
            return null;
        }
        return tool.execute(toolContext);
    }

    private WorkflowResolution resolveMaterialDisambiguationDuringPendingQuestionConfig(
            Long userId,
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantPendingActionPayload payload,
            List<AssistantTool.ToolExecutionResult> executions,
            List<Map<String, Object>> planSnapshot
    ) {
        boolean materialDisambiguation = Boolean.TRUE.equals(structuredIntent == null ? null : structuredIntent.getMaterialDisambiguation())
                || taskIntentParser.looksLikeMaterialAmbiguityChallenge(userMessage);
        String materialQuery = taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent);
        if (payload.getMaterialId() == null && StringUtils.hasText(materialQuery)) {
            materialDisambiguation = true;
        }
        if (!materialDisambiguation || !StringUtils.hasText(materialQuery)) {
            return WorkflowResolution.notHandled();
        }

        AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, materialQuery);
        executions.add(searchExecution);
        planSnapshot.add(Map.of("toolName", "material.search", "reason", "用户在补题型阶段转而澄清资料定位"));
        if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
            return new WorkflowResolution(true, false, executions, searchExecution.errorMessage(), planSnapshot);
        }

        AssistantMaterialSearchResult searchResult = readMaterialSearchResult(searchExecution.toolResultJson());
        if (searchResult != null && searchResult.getSelectedMaterialId() != null) {
            bindMaterialContext(session, searchResult.getSelectedMaterialId());
            payload.setMaterialId(searchResult.getSelectedMaterialId());
            String promptText = taskIntentParser.buildQuestionConfigPrompt(payload.getTasks().isEmpty() ? null : payload.getTasks().get(0));
            payload.setPromptText(promptText);
            savePendingAction(session, "QUESTION_CONFIG", payload);
            return new WorkflowResolution(
                    true,
                    false,
                    executions,
                    searchExecution.summaryText() + System.lineSeparator() + promptText,
                    planSnapshot
            );
        }

        clearMaterialContext(session);
        savePendingAction(session, "MATERIAL_SELECTION", AssistantPendingActionPayload.builder()
                .promptText(searchExecution.summaryText())
                .materialQuery(materialQuery)
                .materialCandidates(searchResult == null ? List.of() : searchResult.getCandidates())
                .tasks(payload.getTasks())
                .build());
        return new WorkflowResolution(true, false, executions, searchExecution.summaryText(), planSnapshot);
    }

    private boolean shouldBypassPendingAction(String pendingActionType, InteractionMode interactionMode) {
        if (interactionMode == null || interactionMode == InteractionMode.UNKNOWN) {
            return false;
        }
        if ("QUESTION_CONFIG".equalsIgnoreCase(pendingActionType)) {
            return interactionMode != InteractionMode.TASK_CONFIG_REPLY;
        }
        if ("MATERIAL_SELECTION".equalsIgnoreCase(pendingActionType)) {
            return interactionMode != InteractionMode.MATERIAL_SELECTION;
        }
        return interactionMode == InteractionMode.CHAT;
    }

    private boolean looksLikeCasualChat(String userMessage) {
        return AssistantToolSupport.containsAnyIgnoreCase(userMessage, CASUAL_CHAT_KEYWORDS);
    }

    private boolean looksLikeQuestionConfigReply(String userMessage, AssistantStructuredIntent structuredIntent) {
        if (Boolean.TRUE.equals(structuredIntent == null ? null : structuredIntent.getQuestionConfigReply())) {
            return true;
        }
        return taskIntentParser.looksLikeQuestionConfigReply(userMessage)
                || AssistantToolSupport.containsAnyIgnoreCase(userMessage, QUESTION_CONFIG_REPLY_HINT_KEYWORDS);
    }

    private boolean looksLikeMaterialSelectionReply(
            String userMessage,
            AssistantPendingActionPayload payload,
            AssistantStructuredIntent structuredIntent
    ) {
        if (Boolean.TRUE.equals(structuredIntent == null ? null : structuredIntent.getDefaultChoice())
                || taskIntentParser.isDefaultChoice(userMessage)) {
            return true;
        }
        if (StringUtils.hasText(structuredIntent == null ? null : structuredIntent.getMaterialQuery())) {
            return true;
        }
        if (containsFlexibleNumber(userMessage)
                || AssistantToolSupport.containsAnyIgnoreCase(userMessage, MATERIAL_SELECTION_REPLY_HINT_KEYWORDS)) {
            return true;
        }
        if (payload == null || payload.getMaterialCandidates() == null || payload.getMaterialCandidates().isEmpty()) {
            return false;
        }
        String normalizedMessage = userMessage.trim().toLowerCase();
        return payload.getMaterialCandidates().stream()
                .map(AssistantMaterialCandidate::getTitle)
                .filter(StringUtils::hasText)
                .map(title -> title.trim().toLowerCase())
                .anyMatch(title -> title.contains(normalizedMessage) || normalizedMessage.contains(title));
    }

    private boolean containsFlexibleNumber(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        return userMessage.matches(".*[0-9一二两三四五六七八九十百].*");
    }

    private Long resolveMaterialIdFromQuestionSetContext(Long userId, AssistantSession session) {
        Long questionSetId = AssistantToolSupport.resolveQuestionSetId(session);
        if (questionSetId == null) {
            return null;
        }
        try {
            QuestionSet questionSet = questionSetService.getById(questionSetId);
            if (questionSet == null || !userId.equals(questionSet.getUserId())) {
                return null;
            }
            if (questionSet.getMaterialId() != null && session != null) {
                session.setCurrentMaterialId(questionSet.getMaterialId());
            }
            return questionSet.getMaterialId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private RecentContextResolution resolveRecentContext(Long userId) {
        List<MaterialPageVO> recentMaterials = studyMaterialService.searchAssistantMaterials(userId, null, 1);
        if (recentMaterials != null && !recentMaterials.isEmpty()) {
            MaterialPageVO material = recentMaterials.get(0);
            LinkedHashMap<String, Object> args = new LinkedHashMap<>();
            args.put("strategy", "recent_material");
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("materialId", material.getId());
            result.put("materialTitle", material.getTitle());
            return new RecentContextResolution(
                    material.getId(),
                    null,
                    "没有明确指定资料，我先按你最近的资料《%s》继续处理。".formatted(material.getTitle()),
                    args,
                    result
            );
        }

        PageVO<QuestionSetPageVO> recentQuestionSets = questionSetService.pageQuestionSets(userId, 1L, 1L, null, null, null);
        if (recentQuestionSets == null || recentQuestionSets.getRecords() == null || recentQuestionSets.getRecords().isEmpty()) {
            return null;
        }

        QuestionSetPageVO questionSet = recentQuestionSets.getRecords().get(0);
        if (questionSet.getMaterialId() == null) {
            return null;
        }

        LinkedHashMap<String, Object> args = new LinkedHashMap<>();
        args.put("strategy", "recent_question_set");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("questionSetId", questionSet.getId());
        result.put("questionSetTitle", questionSet.getTitle());
        result.put("materialId", questionSet.getMaterialId());
        return new RecentContextResolution(
                questionSet.getMaterialId(),
                questionSet.getId(),
                "没有明确指定资料，我先按你最近的题集《%s》关联资料继续处理。".formatted(questionSet.getTitle()),
                args,
                result
        );
    }

    private void bindRecentContext(AssistantSession session, RecentContextResolution resolution) {
        if (session == null || resolution == null || resolution.materialId() == null) {
            return;
        }
        session.setCurrentMaterialId(resolution.materialId());
        if (resolution.questionSetId() != null) {
            session.setCurrentQuestionSetId(resolution.questionSetId());
            session.setCurrentContextType("QUESTION_SET");
            session.setCurrentContextId(resolution.questionSetId());
            return;
        }
        session.setCurrentContextType("MATERIAL");
        session.setCurrentContextId(resolution.materialId());
    }

    private AssistantTool.ToolExecutionResult infoExecution(
            String toolName,
            Object args,
            Object result,
            String summaryText
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new AssistantTool.ToolExecutionResult(
                toolName,
                "SUCCESS",
                toJson(args),
                toJson(result),
                summaryText,
                null,
                now,
                now
        );
    }

    private boolean shouldUseAiModel() {
        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        return Boolean.TRUE.equals(config.enabled())
                && !Boolean.TRUE.equals(config.mockMode())
                && StringUtils.hasText(config.apiKey());
    }

    private boolean shouldUseAiModelForMode(InteractionMode interactionMode) {
        return shouldUseAiModel() && interactionMode != InteractionMode.UNSUPPORTED;
    }

    private void addRequestedTaskType(AssistantStructuredIntent intent, String taskType) {
        if (intent == null || !StringUtils.hasText(taskType)) {
            return;
        }
        if (intent.getRequestedTaskTypes() == null) {
            intent.setRequestedTaskTypes(new ArrayList<>());
        }
        if (intent.getRequestedTaskTypes().stream().noneMatch(taskType::equalsIgnoreCase)) {
            intent.getRequestedTaskTypes().add(taskType);
        }
    }

    private void setMaterialQueryIfPresent(AssistantStructuredIntent intent, String value) {
        if (intent != null && StringUtils.hasText(value) && !StringUtils.hasText(intent.getMaterialQuery())) {
            intent.setMaterialQuery(value.trim());
        }
    }

    private void setIfPresent(Map<String, Object> args, String key, Consumer<String> setter) {
        String value = readTextArg(args, key);
        if (StringUtils.hasText(value)) {
            setter.accept(value.trim());
        }
    }

    private void setIntegerIfPresent(Map<String, Object> args, String key, Consumer<Integer> setter) {
        Integer value = readIntegerArg(args, key);
        if (value != null) {
            setter.accept(value);
        }
    }

    private String readTextArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return null;
        }
        String value = String.valueOf(args.get(key)).trim();
        if (!StringUtils.hasText(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private Boolean readBooleanArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return null;
        }
        Object value = args.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private Integer readIntegerArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return null;
        }
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private String resolveMaterialTitle(AssistantPendingActionPayload payload, Long materialId) {
        if (payload == null || materialId == null || payload.getMaterialCandidates() == null) {
            return null;
        }
        return payload.getMaterialCandidates().stream()
                .filter(candidate -> materialId.equals(candidate.getId()))
                .map(AssistantMaterialCandidate::getTitle)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildMaterialSelectionResult(Long materialId, String materialTitle) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("materialId", materialId);
        result.put("materialTitle", materialTitle);
        return result;
    }

    private String resolveStudyQaQuery(String userMessage, String materialQuery) {
        if (!StringUtils.hasText(userMessage)) {
            return null;
        }
        String query = userMessage.trim();
        if (StringUtils.hasText(materialQuery)) {
            query = query.replace("《" + materialQuery + "》", "")
                    .replace(materialQuery, "")
                    .trim();
        }
        query = query.replaceAll("^(根据|围绕|结合|针对|基于|关于)\\s*", "")
                .replaceAll("(这份|这个|该)?资料", "")
                .replaceAll("^(请|请你|麻烦|麻烦你|帮我|帮忙|给我|让我)\\s*", "")
                .replaceAll("^(告诉我|讲讲|解释一下|带我学习一下|带我学习|帮我学习一下|帮我学习)\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(query)) {
            return "这份资料的核心知识点、重点内容和学习主线";
        }
        return query;
    }

    private boolean isMaterialDetailQuestion(String userMessage) {
        return AssistantToolSupport.containsAnyIgnoreCase(userMessage, MATERIAL_DETAIL_KEYWORDS)
                && !looksLikeKnowledgeAnswerRequest(userMessage);
    }

    private String buildSystemPrompt() {
        return """
                你是系统内置的 AI 学习助手。
                你必须优先依据当前会话上下文、检索片段、练习记录、题集信息、任务状态和记忆摘要回答。
                不要假装看过系统里没有提供的数据，不要编造页码、进度和知识点。
                如果工具已经创建了任务，请直接告诉用户任务号和当前状态。
                如果工具结果已经给出确定的资料、题量、任务号或等待补充项，你要忠实复述，不要擅自改动这些事实。
                如果工具状态里有 WAITING，请自然地说明还缺什么信息，同时保持可以继续闲聊的语气。
                如果给出了资料片段，请尽量结合片段内容给出清晰、简洁、可执行的回答。
                回答保持中文，风格像学习教练，不要输出 Markdown 表格。
                """;
    }

    private String buildUserPrompt(
            AssistantSession session,
            String userMessage,
            List<MemorySnippet> memories,
            List<AssistantTool.ToolExecutionResult> executions,
            String interactionMode
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前会话上下文：").append(System.lineSeparator())
                .append("contextType=").append(session == null ? null : session.getCurrentContextType()).append(System.lineSeparator())
                .append("contextId=").append(session == null ? null : session.getCurrentContextId()).append(System.lineSeparator())
                .append("materialId=").append(session == null ? null : session.getCurrentMaterialId()).append(System.lineSeparator())
                .append("questionSetId=").append(session == null ? null : session.getCurrentQuestionSetId()).append(System.lineSeparator())
                .append("practiceSessionId=").append(session == null ? null : session.getCurrentPracticeSessionId()).append(System.lineSeparator())
                .append("interactionMode=").append(interactionMode).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("用户消息：").append(System.lineSeparator()).append(userMessage).append(System.lineSeparator()).append(System.lineSeparator())
                .append("相关记忆：").append(System.lineSeparator());
        if (memories.isEmpty()) {
            builder.append("暂无").append(System.lineSeparator());
        } else {
            for (MemorySnippet memory : memories) {
                builder.append("- [")
                        .append(memory.memoryScope())
                        .append("] ")
                        .append(StringUtils.hasText(memory.summaryText()) ? memory.summaryText() : memory.contentText())
                        .append(System.lineSeparator());
            }
        }
        builder.append(System.lineSeparator()).append("工具结果：").append(System.lineSeparator());
        if (executions.isEmpty()) {
            builder.append("暂无工具结果，请在上下文不足时明确说明。");
        } else {
            for (AssistantTool.ToolExecutionResult execution : executions) {
                builder.append("- [")
                        .append(execution.toolName())
                        .append(" / ")
                        .append(execution.status())
                        .append("] ")
                        .append(StringUtils.hasText(execution.summaryText()) ? execution.summaryText() : execution.errorMessage())
                        .append(System.lineSeparator());
            }
        }
        return builder.toString().trim();
    }

    private String buildFallbackReply(
            AssistantSession session,
            List<MemorySnippet> memories,
            List<AssistantTool.ToolExecutionResult> executions
    ) {
        if (!executions.isEmpty()) {
            StringBuilder builder = new StringBuilder("我已经结合当前系统数据帮你处理这条请求：");
            int index = 1;
            for (AssistantTool.ToolExecutionResult execution : executions) {
                builder.append(System.lineSeparator())
                        .append(index++)
                        .append(". ")
                        .append(StringUtils.hasText(execution.summaryText()) ? execution.summaryText() : execution.errorMessage());
            }
            return builder.toString().trim();
        }
        if (!memories.isEmpty()) {
            MemorySnippet memory = memories.get(0);
            return "我先结合你之前的学习信息来回答："
                    + System.lineSeparator()
                    + (StringUtils.hasText(memory.summaryText()) ? memory.summaryText() : memory.contentText());
        }
        if (session != null && AssistantToolSupport.resolveMaterialId(session) != null) {
            return "当前会话已经绑定资料。你可以继续直接问知识点，或者明确说“生成总结”“出题”“看任务进度”。";
        }
        return "我已经收到你的问题。当前会话还没有固定上下文，但你也可以直接说要总结或出题，我会优先尝试用最近学习内容继续处理。";
    }

    private String resolveModelName(String modelName) {
        if (StringUtils.hasText(modelName)) {
            return modelName.trim();
        }
        return aiConfigService.getResolvedConfig().defaultModel();
    }

    private void bindMaterialContext(AssistantSession session, Long materialId) {
        if (session == null || materialId == null) {
            return;
        }
        session.setCurrentMaterialId(materialId);
        session.setCurrentContextType("MATERIAL");
        session.setCurrentContextId(materialId);
    }

    private void clearMaterialContext(AssistantSession session) {
        if (session == null) {
            return;
        }
        session.setCurrentMaterialId(null);
        if ("MATERIAL".equalsIgnoreCase(session.getCurrentContextType())) {
            session.setCurrentContextType(null);
            session.setCurrentContextId(null);
        }
    }

    private void savePendingAction(AssistantSession session, String pendingActionType, AssistantPendingActionPayload payload) {
        if (session == null) {
            return;
        }
        session.setPendingActionType(pendingActionType);
        session.setPendingActionPayloadJson(toJson(payload));
    }

    private void clearPendingAction(AssistantSession session) {
        if (session == null) {
            return;
        }
        session.setPendingActionType(null);
        session.setPendingActionPayloadJson(null);
    }

    private AssistantPendingActionPayload readPendingActionPayload(AssistantSession session) {
        if (session == null || !StringUtils.hasText(session.getPendingActionPayloadJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(session.getPendingActionPayloadJson(), AssistantPendingActionPayload.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private AssistantMaterialSearchResult readMaterialSearchResult(String jsonText) {
        if (!StringUtils.hasText(jsonText)) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonText, AssistantMaterialSearchResult.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private AssistantTool.ToolExecutionResult waitingExecution(
            String toolName,
            Object args,
            Object result,
            String summaryText
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new AssistantTool.ToolExecutionResult(
                toolName,
                "WAITING",
                toJson(args),
                toJson(result),
                summaryText,
                null,
                now,
                now
        );
    }

    private void addPlan(List<PlannedTool> plan, LinkedHashSet<String> toolNames, String toolName, String reason) {
        if (toolNames.add(toolName)) {
            plan.add(new PlannedTool(toolName, reason));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"json_serialize_failed\"}";
        }
    }

    private record RecentContextResolution(
            Long materialId,
            Long questionSetId,
            String summaryText,
            Map<String, Object> args,
            Map<String, Object> result
    ) {
    }

    private record PlannedTool(String toolName, String reason) {
    }

    private record WorkflowResolution(
            boolean handled,
            boolean useModel,
            List<AssistantTool.ToolExecutionResult> executions,
            String replyText,
            Object planSnapshot
    ) {
        private static WorkflowResolution notHandled() {
            return new WorkflowResolution(false, false, List.of(), null, List.of());
        }
    }

    private enum InteractionMode {
        TASK_CREATE,
        TASK_CONFIG_REPLY,
        MATERIAL_SELECTION,
        MATERIAL_BROWSE,
        TASK_BROWSE,
        QUESTION_SET_BROWSE,
        CHAPTER_BROWSE,
        CONTEXT_CHALLENGE,
        STUDY_QA,
        CHAT,
        UNSUPPORTED,
        UNKNOWN
    }
}
