package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 工具执行支撑：把大模型规划 / 规则规划转换成具体的工具调用并执行。
 *
 * <p>包含三部分能力：</p>
 * <ul>
 *   <li>把规划器给出的工具参数合并进结构化意图；</li>
 *   <li>根据交互模式生成规则工具计划并执行；</li>
 *   <li>执行规划器返回的工具调用列表（跳过由工作流自己管理的任务型工具）。</li>
 * </ul>
 */
@Component
public class AssistantToolExecutionSupport {

    /** 用户询问任务状态时的关键词。 */
    private static final List<String> TASK_STATUS_KEYWORDS = List.of("任务", "进度", "执行到哪", "状态");
    /** 用户询问练习详情时的关键词。 */
    private static final List<String> PRACTICE_KEYWORDS = List.of("练习", "错题", "判分", "这次练习", "为什么错");
    /** 用户询问题集详情时的关键词。 */
    private static final List<String> QUESTION_SET_KEYWORDS = List.of("题集", "这套题", "题目分布", "题型");

    private final AssistantToolRegistry toolRegistry;
    private final AssistantModelPolicy modelPolicy;

    public AssistantToolExecutionSupport(AssistantToolRegistry toolRegistry, AssistantModelPolicy modelPolicy) {
        this.toolRegistry = toolRegistry;
        this.modelPolicy = modelPolicy;
    }

    /**
     * 将工具规划器给出的参数合并到结构化意图中。
     */
    public AssistantStructuredIntent mergePlanIntoStructuredIntent(
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

    /**
     * 根据交互模式生成规则工具计划。
     */
    public List<PlannedTool> planTools(
            AssistantSession session,
            String userMessage,
            AssistantInteractionMode interactionMode
    ) {
        LinkedHashSet<String> toolNames = new LinkedHashSet<>();
        List<PlannedTool> plan = new ArrayList<>();
        String normalizedMessage = userMessage == null ? "" : userMessage.trim();

        if (interactionMode == AssistantInteractionMode.MATERIAL_BROWSE) {
            addPlan(plan, toolNames, "material.list", "用户想查看当前资料列表或按关键词找资料");
        }
        if (interactionMode == AssistantInteractionMode.TASK_BROWSE) {
            if (AssistantToolSupport.resolveTaskId(session, normalizedMessage) != null
                    && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, TASK_STATUS_KEYWORDS)) {
                addPlan(plan, toolNames, "task.get_status", "用户正在询问任务进度");
            } else {
                addPlan(plan, toolNames, "task.list", "用户想查看任务列表或按状态筛选任务");
            }
        }
        if (interactionMode == AssistantInteractionMode.QUESTION_SET_BROWSE) {
            addPlan(plan, toolNames, "question_set.list", "用户想查看题集列表或题集筛选结果");
        }
        if (interactionMode == AssistantInteractionMode.CHAPTER_BROWSE) {
            addPlan(plan, toolNames, "material.chapter_outline", "用户想查看资料章节、目录或某一章的位置");
        }
        if (AssistantToolSupport.resolvePracticeSessionId(session) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, PRACTICE_KEYWORDS)) {
            addPlan(plan, toolNames, "practice.detail", "当前会话绑定了练习记录");
        }
        if (interactionMode != AssistantInteractionMode.QUESTION_SET_BROWSE
                && AssistantToolSupport.resolveQuestionSetId(session) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, QUESTION_SET_KEYWORDS)) {
            addPlan(plan, toolNames, "question_set.detail", "当前会话绑定了题集");
        }
        if (AssistantToolSupport.resolveMaterialId(session) != null
                && AssistantToolSupport.containsAnyIgnoreCase(normalizedMessage, AssistantInteractionModeResolver.MATERIAL_DETAIL_KEYWORDS)) {
            addPlan(plan, toolNames, "material.detail", "用户更像在问当前资料基本信息");
        }
        return plan.stream().limit(4).toList();
    }

    /**
     * 执行规则生成的工具计划。
     */
    public List<AssistantTool.ToolExecutionResult> executeTools(
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

    /**
     * 执行大模型规划器返回的工具计划。
     */
    public AssistantWorkflowResolution executePlannerToolPlan(
            Long userId,
            AssistantSession session,
            String userMessage,
            String modelName,
            AssistantStructuredIntent structuredIntent,
            AssistantToolPlan toolPlan,
            AssistantInteractionMode interactionMode
    ) {
        if (toolPlan == null || toolPlan.getToolCalls() == null || toolPlan.getToolCalls().isEmpty()) {
            return AssistantWorkflowResolution.notHandled();
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
            return AssistantWorkflowResolution.notHandled();
        }
        return new AssistantWorkflowResolution(
                true,
                modelPolicy.shouldUseAiModelForMode(interactionMode),
                executions,
                null,
                planSnapshot
        );
    }

    /**
     * 按名称执行单个工具；工具不存在或不支持当前上下文时返回 null。
     */
    public AssistantTool.ToolExecutionResult executeToolByName(
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

    /**
     * 任务提交与资料搜索由工作流自己编排，不能被规划器直接触发。
     */
    private boolean isWorkflowManagedTool(String toolName) {
        return "task.submit_summary".equals(toolName)
                || "task.submit_question_generate".equals(toolName)
                || "material.search".equals(toolName);
    }

    /**
     * 把单个工具调用参数写回意图对象。
     */
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

    /**
     * 复制一份结构化意图并合并该工具调用的参数，避免多个工具互相污染。
     */
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

    /**
     * 规划器给了更精确的查询词时，用它替代原始用户消息传给工具。
     */
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

    private void addPlan(List<PlannedTool> plan, LinkedHashSet<String> toolNames, String toolName, String reason) {
        if (toolNames.add(toolName)) {
            plan.add(new PlannedTool(toolName, reason));
        }
    }

    /**
     * 规则规划出的单个工具及其原因。
     */
    public record PlannedTool(String toolName, String reason) {
    }
}
