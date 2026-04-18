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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AssistantAgentOrchestratorImpl implements AssistantAgentOrchestrator {

    private static final List<String> MATERIAL_DETAIL_KEYWORDS = List.of("资料信息", "资料详情", "这份资料", "页数", "标题", "难度", "字符数");
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

    private final AssistantToolRegistry toolRegistry;
    private final AssistantMemoryService assistantMemoryService;
    private final AiConfigService aiConfigService;
    private final AiChatService aiChatService;
    private final StudyMaterialService studyMaterialService;
    private final QuestionSetService questionSetService;
    private final AssistantStructuredIntentExtractor structuredIntentExtractor;
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
        AssistantStructuredIntent structuredIntent = structuredIntentExtractor.extract(userMessage, modelName);
        List<MemoryUsage> usedMemories = memories.stream()
                .map(memory -> new MemoryUsage(
                        memory.id(),
                        memory.memoryScope(),
                        memory.memoryType(),
                        memory.topicName(),
                        memory.summaryText()
                ))
                .toList();

        WorkflowResolution workflowResolution = handleTaskWorkflow(userId, session, userMessage, modelName, structuredIntent);
        if (workflowResolution.handled()) {
            return buildPreparedResult(
                    session,
                    userMessage,
                    memories,
                    workflowResolution.executions(),
                    usedMemories,
                    false,
                    workflowResolution.replyText(),
                    workflowResolution.planSnapshot(),
                    resolveModelName(modelName)
            );
        }

        List<PlannedTool> plan = planTools(session, userMessage, structuredIntent);
        List<AssistantTool.ToolExecutionResult> executions = executeTools(userId, session, userMessage, modelName, structuredIntent, plan);
        return buildPreparedResult(
                session,
                userMessage,
                memories,
                executions,
                usedMemories,
                shouldUseAiModel(),
                null,
                plan,
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
            String resolvedModelName
    ) {
        return new AssistantPreparedResult(
                useModel,
                buildSystemPrompt(),
                buildUserPrompt(session, userMessage, memories, executions),
                StringUtils.hasText(fallbackReplyOverride)
                        ? fallbackReplyOverride
                        : buildFallbackReply(session, memories, executions),
                toJson(Map.of(
                        "strategy", executions.isEmpty() ? "DIRECT_REPLY" : "TOOL_AUGMENTED",
                        "memoryCount", memories.size(),
                        "toolCount", executions.size()
                )),
                toJson(planSnapshot == null ? List.of() : planSnapshot),
                resolvedModelName,
                executions,
                usedMemories
        );
    }

    private WorkflowResolution handleTaskWorkflow(
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent
    ) {
        WorkflowResolution pendingResolution = resolvePendingAction(userId, session, userMessage);
        if (pendingResolution.handled()) {
            return pendingResolution;
        }

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
                    AssistantTool.ToolExecutionResult waitingExecution = waitingExecution(
                            "material.search",
                            Map.of(),
                            Map.of("reason", "material_query_missing"),
                            "我先需要知道你说的是哪份资料。你可以直接告诉我资料标题，或者说一个更明显的关键词，比如“Docker 入门”或“Java 核心”。"
                    );
                    executions.add(waitingExecution);
                    planSnapshot.add(Map.of("toolName", "material.search", "reason", "需要先定位资料"));
                    return new WorkflowResolution(true, executions, null, planSnapshot);
                }
            }

            if (materialId == null) {
                AssistantTool.ToolExecutionResult searchExecution = materialSearchAssistantTool.search(userId, materialQuery);
                executions.add(searchExecution);
                planSnapshot.add(Map.of("toolName", "material.search", "reason", "先根据标题关键词定位资料"));

                if ("FAILED".equalsIgnoreCase(searchExecution.status())) {
                    return new WorkflowResolution(true, executions, searchExecution.errorMessage(), planSnapshot);
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
                    return new WorkflowResolution(true, executions, searchExecution.summaryText(), planSnapshot);
                }
            }
        }

        WorkflowResolution executionResolution = executePlannedTasks(userId, session, materialId, plannedTasks, executions, planSnapshot);
        return executionResolution.handled()
                ? executionResolution
                : new WorkflowResolution(true, executions, null, planSnapshot);
    }

    private WorkflowResolution resolvePendingAction(Long userId, AssistantSession session, String userMessage) {
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
            Long selectedMaterialId = taskIntentParser.resolveMaterialCandidateSelection(userMessage, payload.getMaterialCandidates());
            if (selectedMaterialId == null) {
                if (shouldBypassPendingAction(userMessage, pendingActionType, payload)) {
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
                return new WorkflowResolution(true, executions, promptText, planSnapshot);
            }

            bindMaterialContext(session, selectedMaterialId);
            clearPendingAction(session);
            WorkflowResolution followUpResolution = executePendingFollowUp(userId, session, payload, executions, planSnapshot);
            if (followUpResolution.handled()) {
                return followUpResolution;
            }
            return executePlannedTasks(userId, session, selectedMaterialId, payload.getTasks(), executions, planSnapshot);
        }

        if ("QUESTION_CONFIG".equals(pendingActionType)) {
            AssistantPlannedTask pendingQuestionTask = payload.getTasks().isEmpty() ? null : payload.getTasks().get(0);
            AssistantTaskIntentParser.QuestionConfigResolution resolution =
                    taskIntentParser.resolveQuestionConfigReply(userMessage, pendingQuestionTask);
            if (!resolution.resolved()) {
                if (shouldBypassPendingAction(userMessage, pendingActionType, payload)) {
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
                return new WorkflowResolution(true, executions, promptText, planSnapshot);
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
            return new WorkflowResolution(true, executions, null, planSnapshot);
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
                    return new WorkflowResolution(true, executions, null, planSnapshot);
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
        return new WorkflowResolution(true, executions, null, planSnapshot);
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
            return new WorkflowResolution(true, executions, execution.summaryText(), planSnapshot);
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
            AssistantStructuredIntent structuredIntent
    ) {
        LinkedHashSet<String> toolNames = new LinkedHashSet<>();
        List<PlannedTool> plan = new ArrayList<>();
        String normalizedMessage = userMessage == null ? "" : userMessage.trim();
        boolean materialBrowseIntent = taskIntentParser.looksLikeMaterialBrowseRequest(normalizedMessage, structuredIntent);
        boolean taskListIntent = taskIntentParser.looksLikeTaskListRequest(normalizedMessage, structuredIntent);
        boolean questionSetListIntent = taskIntentParser.looksLikeQuestionSetListRequest(normalizedMessage, structuredIntent);
        boolean chapterBrowseIntent = taskIntentParser.looksLikeChapterBrowseRequest(normalizedMessage, structuredIntent);

        if (materialBrowseIntent) {
            addPlan(plan, toolNames, "material.list", "用户想查看当前资料列表或按关键词找资料");
        }
        if (taskListIntent) {
            addPlan(plan, toolNames, "task.list", "用户想查看任务列表或按状态筛选任务");
        }
        if (questionSetListIntent) {
            addPlan(plan, toolNames, "question_set.list", "用户想查看题集列表或题集筛选结果");
        }
        if (chapterBrowseIntent) {
            addPlan(plan, toolNames, "material.chapter_outline", "用户想查看资料章节、目录或某一章的位置");
        }
        if (!taskListIntent
                && AssistantToolSupport.resolveTaskId(session, normalizedMessage) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, TASK_STATUS_KEYWORDS)) {
            addPlan(plan, toolNames, "task.get_status", "用户正在询问任务进度");
        }
        if (AssistantToolSupport.resolvePracticeSessionId(session) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, PRACTICE_KEYWORDS)) {
            addPlan(plan, toolNames, "practice.detail", "当前会话绑定了练习记录");
        }
        if (!questionSetListIntent
                && AssistantToolSupport.resolveQuestionSetId(session) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, QUESTION_SET_KEYWORDS)) {
            addPlan(plan, toolNames, "question_set.detail", "当前会话绑定了题集");
        }
        if (!materialBrowseIntent && !chapterBrowseIntent && AssistantToolSupport.resolveMaterialId(session) != null) {
            if (AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, MATERIAL_DETAIL_KEYWORDS)) {
                addPlan(plan, toolNames, "material.detail", "用户更像在问当前资料基本信息");
            } else {
                addPlan(plan, toolNames, "rag.retrieve", "默认走资料检索问答");
            }
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

    private boolean shouldBypassPendingAction(
            String userMessage,
            String pendingActionType,
            AssistantPendingActionPayload payload
    ) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        if (looksLikeCasualChat(userMessage)) {
            return true;
        }
        if ("QUESTION_CONFIG".equalsIgnoreCase(pendingActionType)) {
            return !looksLikeQuestionConfigReply(userMessage);
        }
        if ("MATERIAL_SELECTION".equalsIgnoreCase(pendingActionType)) {
            return !looksLikeMaterialSelectionReply(userMessage, payload);
        }
        return false;
    }

    private boolean looksLikeCasualChat(String userMessage) {
        return AssistantToolSupport.containsAnyIgnoreCase(userMessage, CASUAL_CHAT_KEYWORDS);
    }

    private boolean looksLikeQuestionConfigReply(String userMessage) {
        if (taskIntentParser.isDefaultChoice(userMessage)) {
            return true;
        }
        return containsFlexibleNumber(userMessage)
                || AssistantToolSupport.containsAnyIgnoreCase(userMessage, QUESTION_CONFIG_REPLY_HINT_KEYWORDS);
    }

    private boolean looksLikeMaterialSelectionReply(String userMessage, AssistantPendingActionPayload payload) {
        if (taskIntentParser.isDefaultChoice(userMessage)) {
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
        if (!recentMaterials.isEmpty()) {
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
            List<AssistantTool.ToolExecutionResult> executions
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前会话上下文：").append(System.lineSeparator())
                .append("contextType=").append(session == null ? null : session.getCurrentContextType()).append(System.lineSeparator())
                .append("contextId=").append(session == null ? null : session.getCurrentContextId()).append(System.lineSeparator())
                .append("materialId=").append(session == null ? null : session.getCurrentMaterialId()).append(System.lineSeparator())
                .append("questionSetId=").append(session == null ? null : session.getCurrentQuestionSetId()).append(System.lineSeparator())
                .append("practiceSessionId=").append(session == null ? null : session.getCurrentPracticeSessionId()).append(System.lineSeparator())
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
            List<AssistantTool.ToolExecutionResult> executions,
            String replyText,
            Object planSnapshot
    ) {
        private static WorkflowResolution notHandled() {
            return new WorkflowResolution(false, List.of(), null, List.of());
        }
    }
}
