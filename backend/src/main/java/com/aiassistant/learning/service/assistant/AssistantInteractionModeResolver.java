package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 意图路由：综合大模型规划、结构化意图、pending 决策和关键词规则，判断本轮交互模式，
 * 以及“用户是否已经离开当前待确认动作”。
 *
 * <p>这里只做判断，不执行任何工具，也不修改会话状态。</p>
 */
@Component
public class AssistantInteractionModeResolver {

    /** 用户询问资料详情时的关键词。 */
    static final List<String> MATERIAL_DETAIL_KEYWORDS = List.of(
            "资料信息", "资料详情", "资料基本信息", "文件信息", "页数", "多少页", "总页数", "标题", "难度", "字符数", "解析状态", "PDF状态"
    );
    /** 普通寒暄关键词。 */
    private static final List<String> CASUAL_CHAT_KEYWORDS = List.of(
            "你好", "您好", "hi", "hello", "在吗", "谢谢", "多谢", "早上好", "下午好", "晚上好", "你是谁", "介绍一下你自己"
    );
    /** 判断用户是否在回复出题配置的提示词。 */
    private static final List<String> QUESTION_CONFIG_REPLY_HINT_KEYWORDS = List.of(
            "单选", "多选", "多项选择", "选择题", "判断", "简答", "默认", "全部", "全都", "只出", "都出", "题量", "数量", "道题", "知识点：", "知识点:"
    );
    /** 判断用户是否在选择候选资料的提示词。 */
    private static final List<String> MATERIAL_SELECTION_REPLY_HINT_KEYWORDS = List.of(
            "这个", "那个", "这份", "那份", "资料", "序号", "第", "id", "#"
    );
    /** 学习问答类关键词。 */
    private static final List<String> STUDY_QA_HINT_KEYWORDS = List.of(
            "带我学习", "带我复习", "帮我学习", "帮我复习", "讲讲", "解释", "说明", "告诉我", "什么是", "为什么", "怎么", "如何",
            "区别", "联系", "原理", "概念", "简介", "概述", "知识点", "重点", "难点", "考点", "学习", "核心", "要点", "重要内容"
    );
    /** 明确创建总结任务的关键词。 */
    private static final List<String> EXPLICIT_SUMMARY_TASK_KEYWORDS = List.of(
            "生成总结", "生成AI总结", "生成ai总结", "AI总结", "ai总结", "总结任务", "创建总结", "保存到笔记", "保存笔记",
            "存笔记", "帮我总结", "总结一下", "做个总结", "生成提纲", "生成大纲", "输出总结"
    );
    /** 判断用户是否在问“列出/梳理”类知识问题的补充关键词。 */
    private static final List<String> KNOWLEDGE_LISTING_KEYWORDS = List.of(
            "哪些", "有什么", "告诉我", "梳理", "整理", "列出", "讲讲", "解释", "说明", "分析", "全量", "核心", "重要"
    );
    /** 用户明确取消当前待确认动作的关键词。 */
    private static final List<String> PENDING_CANCEL_KEYWORDS = List.of(
            "算了", "取消", "不出了", "不做了", "先不", "不用了", "换一个", "重新来", "别继续"
    );

    private final AssistantTaskIntentParser taskIntentParser;
    private final AssistantSessionStateSupport sessionState;

    public AssistantInteractionModeResolver(
            AssistantTaskIntentParser taskIntentParser,
            AssistantSessionStateSupport sessionState
    ) {
        this.taskIntentParser = taskIntentParser;
        this.sessionState = sessionState;
    }

    /**
     * 综合待确认动作、模型意图和规则意图，决定本轮交互模式。
     */
    public AssistantInteractionMode resolve(
            AssistantSession session,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantToolPlan toolPlan,
            AssistantPendingDecision pendingDecision
    ) {
        AssistantInteractionMode plannedMode = normalize(toolPlan == null ? null : toolPlan.getInteractionMode());
        AssistantInteractionMode extractedMode = normalize(structuredIntent == null ? null : structuredIntent.getInteractionMode());
        AssistantInteractionMode pendingMode = normalize(pendingDecision == null ? null : pendingDecision.getInteractionMode());
        AssistantPendingActionPayload pendingPayload = sessionState.readPendingActionPayload(session);
        String pendingActionType = session == null ? null : session.getPendingActionType();
        boolean pendingInterrupted = shouldInterruptPendingAction(
                pendingActionType,
                userMessage,
                structuredIntent,
                plannedMode,
                extractedMode,
                pendingDecision
        );

        if (isPendingContinueDecision(pendingDecision)) {
            if ("QUESTION_CONFIG".equalsIgnoreCase(pendingActionType)) {
                return pendingMode != AssistantInteractionMode.UNKNOWN ? pendingMode : AssistantInteractionMode.TASK_CONFIG_REPLY;
            }
            if ("MATERIAL_SELECTION".equalsIgnoreCase(pendingActionType)) {
                return pendingMode != AssistantInteractionMode.UNKNOWN ? pendingMode : AssistantInteractionMode.MATERIAL_SELECTION;
            }
        }
        if (taskIntentParser.looksLikeMaterialAmbiguityChallenge(userMessage)
                || Boolean.TRUE.equals(structuredIntent == null ? null : structuredIntent.getMaterialDisambiguation())
                || Boolean.TRUE.equals(structuredIntent == null ? null : structuredIntent.getContextChallenge())) {
            return AssistantInteractionMode.CONTEXT_CHALLENGE;
        }
        if (!pendingInterrupted
                && "QUESTION_CONFIG".equalsIgnoreCase(pendingActionType)
                && looksLikeQuestionConfigReply(userMessage, structuredIntent)) {
            return AssistantInteractionMode.TASK_CONFIG_REPLY;
        }
        if (!pendingInterrupted
                && "MATERIAL_SELECTION".equalsIgnoreCase(pendingActionType)
                && looksLikeMaterialSelectionReply(userMessage, pendingPayload, structuredIntent)) {
            return AssistantInteractionMode.MATERIAL_SELECTION;
        }
        if (plannedMode == AssistantInteractionMode.TASK_CREATE && !looksLikeExplicitTaskRequest(userMessage, structuredIntent)) {
            plannedMode = AssistantInteractionMode.UNKNOWN;
        }
        if (plannedMode != AssistantInteractionMode.UNKNOWN) {
            return plannedMode;
        }
        if (extractedMode == AssistantInteractionMode.TASK_CREATE && !looksLikeExplicitTaskRequest(userMessage, structuredIntent)) {
            if (looksLikeStudyQaMessage(session, userMessage, structuredIntent)) {
                return AssistantInteractionMode.STUDY_QA;
            }
            if (looksLikeCasualChat(userMessage)) {
                return AssistantInteractionMode.CHAT;
            }
        }
        if (extractedMode != AssistantInteractionMode.UNKNOWN) {
            return extractedMode;
        }
        if (looksLikeCasualChat(userMessage)) {
            return AssistantInteractionMode.CHAT;
        }
        if (looksLikeExplicitTaskRequest(userMessage, structuredIntent)) {
            return AssistantInteractionMode.TASK_CREATE;
        }
        if (taskIntentParser.looksLikeMaterialBrowseRequest(userMessage, structuredIntent)) {
            return AssistantInteractionMode.MATERIAL_BROWSE;
        }
        if (taskIntentParser.looksLikeTaskListRequest(userMessage, structuredIntent)) {
            return AssistantInteractionMode.TASK_BROWSE;
        }
        if (taskIntentParser.looksLikeQuestionSetListRequest(userMessage, structuredIntent)) {
            return AssistantInteractionMode.QUESTION_SET_BROWSE;
        }
        if (taskIntentParser.looksLikeChapterBrowseRequest(userMessage, structuredIntent)) {
            return AssistantInteractionMode.CHAPTER_BROWSE;
        }
        if (looksLikeStudyQaMessage(session, userMessage, structuredIntent)) {
            return AssistantInteractionMode.STUDY_QA;
        }
        return AssistantInteractionMode.UNKNOWN;
    }

    /**
     * 将字符串交互模式转成枚举，无法识别时返回 UNKNOWN。
     */
    public AssistantInteractionMode normalize(String rawMode) {
        if (!StringUtils.hasText(rawMode)) {
            return AssistantInteractionMode.UNKNOWN;
        }
        try {
            return AssistantInteractionMode.valueOf(rawMode.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AssistantInteractionMode.UNKNOWN;
        }
    }

    /**
     * 判断用户是否已经离开当前待确认动作。
     */
    public boolean shouldInterruptPendingAction(
            String pendingActionType,
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantInteractionMode plannedMode,
            AssistantInteractionMode extractedMode,
            AssistantPendingDecision pendingDecision
    ) {
        if (!StringUtils.hasText(pendingActionType) || !StringUtils.hasText(userMessage)) {
            return false;
        }
        if (isPendingInterruptDecision(pendingDecision) || isPendingCancelDecision(pendingDecision)) {
            return true;
        }
        if (isPendingContinueDecision(pendingDecision) || isPendingClarifyDecision(pendingDecision)) {
            return false;
        }
        if (looksLikePendingCancelMessage(userMessage)) {
            return true;
        }
        String normalizedPendingType = pendingActionType.trim().toUpperCase();
        if ("QUESTION_CONFIG".equals(normalizedPendingType)) {
            return shouldInterruptQuestionConfigPending(userMessage, structuredIntent, plannedMode, extractedMode);
        }
        if ("MATERIAL_SELECTION".equals(normalizedPendingType)) {
            return shouldInterruptMaterialSelectionPending(userMessage, structuredIntent, plannedMode, extractedMode);
        }
        return false;
    }

    /**
     * 待确认动作还挂着、但用户已经换了明确的其他模式时，是否直接跳过 pending。
     */
    public boolean shouldBypassPendingAction(String pendingActionType, AssistantInteractionMode interactionMode) {
        if (interactionMode == null || interactionMode == AssistantInteractionMode.UNKNOWN) {
            return false;
        }
        if ("QUESTION_CONFIG".equalsIgnoreCase(pendingActionType)) {
            return interactionMode != AssistantInteractionMode.TASK_CONFIG_REPLY;
        }
        if ("MATERIAL_SELECTION".equalsIgnoreCase(pendingActionType)) {
            return interactionMode != AssistantInteractionMode.MATERIAL_SELECTION;
        }
        return interactionMode == AssistantInteractionMode.CHAT;
    }

    /**
     * 判断用户是否明确在要求创建任务。
     */
    public boolean looksLikeExplicitTaskRequest(String userMessage, AssistantStructuredIntent structuredIntent) {
        if (looksLikeKnowledgeAnswerRequest(userMessage) && !looksLikeExplicitTaskCommand(userMessage)) {
            return false;
        }
        return !taskIntentParser.resolveRequestedTaskTypes(userMessage, structuredIntent).isEmpty();
    }

    /**
     * 判断用户是否更像在问资料的基本信息（页数、标题等），而不是知识问题。
     */
    public boolean isMaterialDetailQuestion(String userMessage) {
        return AssistantToolSupport.containsAnyIgnoreCase(userMessage, MATERIAL_DETAIL_KEYWORDS)
                && !looksLikeKnowledgeAnswerRequest(userMessage);
    }

    /**
     * 判断是否是普通寒暄。
     */
    public boolean looksLikeCasualChat(String userMessage) {
        return AssistantToolSupport.containsAnyIgnoreCase(userMessage, CASUAL_CHAT_KEYWORDS);
    }

    /**
     * 从用户消息里去掉资料名和口语前缀，得到适合检索的学习问题。
     */
    public String resolveStudyQaQuery(String userMessage, String materialQuery) {
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

    /**
     * 判断是否出现明确的任务创建动词。
     */
    private boolean looksLikeExplicitTaskCommand(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        return taskIntentParser.looksLikeQuestionRequest(userMessage)
                || AssistantToolSupport.containsAnyIgnoreCase(userMessage, EXPLICIT_SUMMARY_TASK_KEYWORDS);
    }

    /**
     * 判断用户是否更像在问知识问题，而不是创建任务。
     */
    private boolean looksLikeKnowledgeAnswerRequest(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        return AssistantToolSupport.containsAnyIgnoreCase(userMessage, STUDY_QA_HINT_KEYWORDS)
                && AssistantToolSupport.containsAnyIgnoreCase(userMessage, KNOWLEDGE_LISTING_KEYWORDS);
    }

    /**
     * 判断是否应进入资料问答模式。
     */
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

    private boolean isPendingContinueDecision(AssistantPendingDecision pendingDecision) {
        return isPendingDecision(pendingDecision, "CONTINUE");
    }

    private boolean isPendingInterruptDecision(AssistantPendingDecision pendingDecision) {
        return isPendingDecision(pendingDecision, "INTERRUPT");
    }

    private boolean isPendingCancelDecision(AssistantPendingDecision pendingDecision) {
        return isPendingDecision(pendingDecision, "CANCEL");
    }

    private boolean isPendingClarifyDecision(AssistantPendingDecision pendingDecision) {
        return isPendingDecision(pendingDecision, "CLARIFY");
    }

    private boolean isPendingDecision(AssistantPendingDecision pendingDecision, String expectedDecision) {
        return pendingDecision != null
                && StringUtils.hasText(pendingDecision.getDecision())
                && pendingDecision.getDecision().trim().equalsIgnoreCase(expectedDecision);
    }

    /**
     * 等待题型配置时，题型、数量与明确的知识点范围回复继续原任务。
     */
    private boolean shouldInterruptQuestionConfigPending(
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantInteractionMode plannedMode,
            AssistantInteractionMode extractedMode
    ) {
        if (plannedMode == AssistantInteractionMode.MATERIAL_SELECTION || extractedMode == AssistantInteractionMode.MATERIAL_SELECTION) {
            return false;
        }
        if (StringUtils.hasText(taskIntentParser.extractMaterialQueryText(userMessage, structuredIntent))
                && looksLikeExplicitTaskRequest(userMessage, structuredIntent)) {
            return true;
        }
        if (taskIntentParser.hasExplicitKnowledgePointScope(userMessage, structuredIntent)) {
            return false;
        }
        if (plannedMode == AssistantInteractionMode.STUDY_QA || extractedMode == AssistantInteractionMode.STUDY_QA) {
            return true;
        }
        if (plannedMode == AssistantInteractionMode.CHAT || extractedMode == AssistantInteractionMode.CHAT) {
            return true;
        }
        return looksLikeNonPendingTopic(userMessage, structuredIntent);
    }

    /**
     * 等待资料选择时，列表查询、任务查询、普通问答等会打断旧选择。
     */
    private boolean shouldInterruptMaterialSelectionPending(
            String userMessage,
            AssistantStructuredIntent structuredIntent,
            AssistantInteractionMode plannedMode,
            AssistantInteractionMode extractedMode
    ) {
        if (plannedMode == AssistantInteractionMode.CHAT || extractedMode == AssistantInteractionMode.CHAT) {
            return true;
        }
        if (plannedMode == AssistantInteractionMode.STUDY_QA || extractedMode == AssistantInteractionMode.STUDY_QA) {
            return true;
        }
        return looksLikeNonPendingTopic(userMessage, structuredIntent);
    }

    /**
     * 判断用户消息是否明显是另一个话题。
     */
    private boolean looksLikeNonPendingTopic(String userMessage, AssistantStructuredIntent structuredIntent) {
        return looksLikeCasualChat(userMessage)
                || taskIntentParser.looksLikeMaterialBrowseRequest(userMessage, structuredIntent)
                || taskIntentParser.looksLikeTaskListRequest(userMessage, structuredIntent)
                || taskIntentParser.looksLikeQuestionSetListRequest(userMessage, structuredIntent)
                || taskIntentParser.looksLikeChapterBrowseRequest(userMessage, structuredIntent)
                || taskIntentParser.looksLikeMaterialAmbiguityChallenge(userMessage)
                || AssistantToolSupport.containsAnyIgnoreCase(userMessage, STUDY_QA_HINT_KEYWORDS);
    }

    private boolean looksLikePendingCancelMessage(String userMessage) {
        return AssistantToolSupport.containsAnyIgnoreCase(userMessage, PENDING_CANCEL_KEYWORDS);
    }

    /**
     * 判断用户是否在回复出题配置追问。
     */
    private boolean looksLikeQuestionConfigReply(String userMessage, AssistantStructuredIntent structuredIntent) {
        if (Boolean.TRUE.equals(structuredIntent == null ? null : structuredIntent.getQuestionConfigReply())) {
            return true;
        }
        return taskIntentParser.looksLikeQuestionConfigReply(userMessage)
                || taskIntentParser.hasExplicitKnowledgePointScope(userMessage, structuredIntent)
                || AssistantToolSupport.containsAnyIgnoreCase(userMessage, QUESTION_CONFIG_REPLY_HINT_KEYWORDS);
    }

    /**
     * 判断用户是否在从候选资料中做选择。
     */
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
}
