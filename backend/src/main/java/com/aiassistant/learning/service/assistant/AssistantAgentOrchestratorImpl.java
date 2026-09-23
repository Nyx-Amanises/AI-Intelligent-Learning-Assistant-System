package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AssistantMemoryService;
import com.aiassistant.learning.service.AssistantMemoryService.MemorySnippet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 助手 Agent 编排器实现类。
 *
 * <p>它把用户消息转换成“识别意图 -> 处理待确认动作 -> 执行工作流 / 工具 -> 组织提示词”的链路，
 * 自己只负责串联顺序，具体逻辑分别委托给：</p>
 * <ul>
 *   <li>{@link AssistantInteractionModeResolver}：意图路由；</li>
 *   <li>{@link AssistantPendingActionHandler}：待确认动作状态机；</li>
 *   <li>{@link AssistantTaskWorkflowExecutor} / {@link AssistantStudyQaWorkflowHandler}：任务创建与资料问答工作流；</li>
 *   <li>{@link AssistantToolExecutionSupport}：工具规划与执行；</li>
 *   <li>{@link AssistantPromptBuilder}：提示词与兜底回复。</li>
 * </ul>
 */
@Component
public class AssistantAgentOrchestratorImpl implements AssistantAgentOrchestrator {

    private final AssistantMemoryService assistantMemoryService;
    private final AiChatService aiChatService;
    private final AssistantStructuredIntentExtractor structuredIntentExtractor;
    private final AssistantToolPlanner toolPlanner;
    private final AssistantPendingDecisionPlanner pendingDecisionPlanner;
    private final AssistantInteractionModeResolver modeResolver;
    private final AssistantPendingActionHandler pendingActionHandler;
    private final AssistantTaskWorkflowExecutor taskWorkflowExecutor;
    private final AssistantStudyQaWorkflowHandler studyQaWorkflowHandler;
    private final AssistantToolExecutionSupport toolExecution;
    private final AssistantPromptBuilder promptBuilder;
    private final AssistantModelPolicy modelPolicy;
    private final AssistantSessionStateSupport sessionState;

    public AssistantAgentOrchestratorImpl(
            AssistantMemoryService assistantMemoryService,
            AiChatService aiChatService,
            AssistantStructuredIntentExtractor structuredIntentExtractor,
            AssistantToolPlanner toolPlanner,
            AssistantPendingDecisionPlanner pendingDecisionPlanner,
            AssistantInteractionModeResolver modeResolver,
            AssistantPendingActionHandler pendingActionHandler,
            AssistantTaskWorkflowExecutor taskWorkflowExecutor,
            AssistantStudyQaWorkflowHandler studyQaWorkflowHandler,
            AssistantToolExecutionSupport toolExecution,
            AssistantPromptBuilder promptBuilder,
            AssistantModelPolicy modelPolicy,
            AssistantSessionStateSupport sessionState
    ) {
        this.assistantMemoryService = assistantMemoryService;
        this.aiChatService = aiChatService;
        this.structuredIntentExtractor = structuredIntentExtractor;
        this.toolPlanner = toolPlanner;
        this.pendingDecisionPlanner = pendingDecisionPlanner;
        this.modeResolver = modeResolver;
        this.pendingActionHandler = pendingActionHandler;
        this.taskWorkflowExecutor = taskWorkflowExecutor;
        this.studyQaWorkflowHandler = studyQaWorkflowHandler;
        this.toolExecution = toolExecution;
        this.promptBuilder = promptBuilder;
        this.modelPolicy = modelPolicy;
        this.sessionState = sessionState;
    }

    /**
     * 准备一轮回复。
     *
     * <p>先执行可确定的工具调用，再决定是否需要继续把上下文交给大模型生成最终回答。</p>
     */
    @Override
    public AssistantPreparedResult prepare(Long userId, AssistantSession session, String userMessage, String modelName) {
        List<MemorySnippet> memories = assistantMemoryService.findRelevantMemories(userId, userMessage, 3);
        AssistantToolPlan toolPlan = toolPlanner.plan(session, userMessage, modelName, null);
        AssistantStructuredIntent structuredIntent = toolExecution.mergePlanIntoStructuredIntent(AssistantStructuredIntent.empty(), toolPlan);
        if (toolPlan == null || !toolPlan.hasUsablePlan()) {
            structuredIntent = structuredIntentExtractor.extract(userMessage, modelName);
            toolPlan = AssistantToolPlan.empty();
        }
        AssistantPendingActionPayload pendingPayload = sessionState.readPendingActionPayload(session);
        AssistantPendingDecision pendingDecision = pendingDecisionPlanner == null
                ? AssistantPendingDecision.unknown()
                : pendingDecisionPlanner.decide(session, pendingPayload, userMessage, structuredIntent, toolPlan, modelName);
        AssistantInteractionMode interactionMode = modeResolver.resolve(session, userMessage, structuredIntent, toolPlan, pendingDecision);
        List<MemoryUsage> usedMemories = memories.stream()
                .map(memory -> new MemoryUsage(
                        memory.id(),
                        memory.memoryScope(),
                        memory.memoryType(),
                        memory.topicName(),
                        memory.summaryText()
                ))
                .toList();

        // 1. 先看会话上是否挂着待确认动作。
        AssistantWorkflowResolution workflowResolution = pendingActionHandler.resolve(
                userId, session, userMessage, structuredIntent, interactionMode, pendingDecision);
        // 2. 按交互模式进入对应工作流。
        if (!workflowResolution.handled()) {
            workflowResolution = switch (interactionMode) {
                case TASK_CREATE -> taskWorkflowExecutor.handleTaskCreation(userId, session, userMessage, modelName, structuredIntent);
                case STUDY_QA -> studyQaWorkflowHandler.handleStudyQa(userId, session, userMessage, modelName, structuredIntent);
                case CONTEXT_CHALLENGE -> studyQaWorkflowHandler.handleContextChallenge(userId, session, userMessage, structuredIntent);
                case UNSUPPORTED -> studyQaWorkflowHandler.buildUnsupportedResolution(structuredIntent);
                default -> AssistantWorkflowResolution.notHandled();
            };
        }
        // 3. 规划器直接给出的回复 / 追问。
        if (!workflowResolution.handled()) {
            workflowResolution = resolvePlannerDirectAction(toolPlan);
        }
        if (workflowResolution.handled()) {
            return buildPreparedResult(session, userMessage, memories, usedMemories, workflowResolution, toolPlan,
                    workflowResolution.planSnapshot(), interactionMode, modelName);
        }

        // 4. 规划器给出的工具调用列表。
        AssistantWorkflowResolution plannerToolResolution = toolExecution.executePlannerToolPlan(
                userId, session, userMessage, modelName, structuredIntent, toolPlan, interactionMode);
        if (plannerToolResolution.handled()) {
            return buildPreparedResult(session, userMessage, memories, usedMemories, plannerToolResolution, toolPlan,
                    plannerToolResolution.planSnapshot(), interactionMode, modelName);
        }

        // 5. 规则兜底：按交互模式挑选工具执行。
        List<AssistantToolExecutionSupport.PlannedTool> plan = toolExecution.planTools(session, userMessage, interactionMode);
        List<AssistantTool.ToolExecutionResult> executions = toolExecution.executeTools(
                userId, session, userMessage, modelName, structuredIntent, plan);
        AssistantWorkflowResolution ruleResolution = new AssistantWorkflowResolution(
                true, modelPolicy.shouldUseAiModelForMode(interactionMode), executions, null, plan);
        return buildPreparedResult(session, userMessage, memories, usedMemories, ruleResolution, toolPlan,
                plan, interactionMode, modelName);
    }

    /**
     * 非流式生成完整助手回复。
     */
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

    /**
     * 将对话内容交给记忆服务做长期记忆沉淀。
     */
    @Override
    public void captureConversationMemory(Long userId, AssistantSession session, String userMessage, String assistantReply) {
        assistantMemoryService.captureConversationMemory(userId, session, userMessage, assistantReply);
    }

    /**
     * 组装 Agent 预处理结果。
     */
    private AssistantPreparedResult buildPreparedResult(
            AssistantSession session,
            String userMessage,
            List<MemorySnippet> memories,
            List<MemoryUsage> usedMemories,
            AssistantWorkflowResolution resolution,
            AssistantToolPlan toolPlan,
            Object executionPlan,
            AssistantInteractionMode interactionMode,
            String modelName
    ) {
        List<AssistantTool.ToolExecutionResult> executions = resolution.executions();
        String interactionModeName = interactionMode.name();
        Object planSnapshot = buildPlanSnapshot(executionPlan, toolPlan);
        return new AssistantPreparedResult(
                resolution.useModel(),
                promptBuilder.buildSystemPrompt(),
                promptBuilder.buildUserPrompt(session, userMessage, memories, executions, interactionModeName),
                StringUtils.hasText(resolution.replyText())
                        ? resolution.replyText()
                        : promptBuilder.buildFallbackReply(session, memories, executions),
                sessionState.toJson(Map.of(
                        "strategy", executions.isEmpty() ? "DIRECT_REPLY" : "TOOL_AUGMENTED",
                        "interactionMode", interactionModeName,
                        "memoryCount", memories.size(),
                        "toolCount", executions.size()
                )),
                sessionState.toJson(planSnapshot == null ? List.of() : planSnapshot),
                modelPolicy.resolveModelName(modelName),
                executions,
                usedMemories
        );
    }

    /**
     * 处理规划器直接给出的回复、追问或不支持能力。
     */
    private AssistantWorkflowResolution resolvePlannerDirectAction(AssistantToolPlan toolPlan) {
        if (toolPlan == null || !toolPlan.hasUsablePlan()) {
            return AssistantWorkflowResolution.notHandled();
        }
        String replyStrategy = StringUtils.hasText(toolPlan.getReplyStrategy())
                ? toolPlan.getReplyStrategy().trim().toUpperCase()
                : null;
        if ("UNSUPPORTED".equals(replyStrategy)) {
            String replyText = StringUtils.hasText(toolPlan.getDirectReply())
                    ? toolPlan.getDirectReply()
                    : AssistantStudyQaWorkflowHandler.buildUnsupportedReply(toolPlan.getUnsupportedFeature());
            return new AssistantWorkflowResolution(true, false, List.of(), replyText, toolPlan);
        }
        if ("ASK_CLARIFICATION".equals(replyStrategy)
                || (toolPlan.getMissingSlots() != null && !toolPlan.getMissingSlots().isEmpty())) {
            String replyText = StringUtils.hasText(toolPlan.getClarificationPrompt())
                    ? toolPlan.getClarificationPrompt()
                    : "我还需要你补充这些信息：" + String.join("、", toolPlan.getMissingSlots());
            return new AssistantWorkflowResolution(true, false, List.of(), replyText, toolPlan);
        }
        if ("DIRECT_REPLY".equals(replyStrategy) && StringUtils.hasText(toolPlan.getDirectReply())) {
            return new AssistantWorkflowResolution(true, false, List.of(), toolPlan.getDirectReply(), toolPlan);
        }
        return AssistantWorkflowResolution.notHandled();
    }

    /**
     * 构造调试用的计划快照。
     */
    private Object buildPlanSnapshot(Object executionPlan, AssistantToolPlan toolPlan) {
        if (toolPlan == null || !toolPlan.hasUsablePlan()) {
            return executionPlan;
        }
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planner", toolPlan);
        snapshot.put("executionPlan", executionPlan == null ? List.of() : executionPlan);
        return snapshot;
    }
}
