package com.aiassistant.learning.service.assistant;

/**
 * 助手一轮对话的交互模式。
 *
 * <p>由 {@link AssistantInteractionModeResolver} 综合大模型规划、结构化意图和规则判断得出，
 * 决定后续走哪条工作流（创建任务、资料问答、浏览列表、闲聊等）。</p>
 */
public enum AssistantInteractionMode {
    TASK_CREATE,
    TASK_CONFIG_REPLY,
    MATERIAL_SELECTION,
    MATERIAL_BROWSE,
    TASK_BROWSE,
    QUESTION_SET_BROWSE,
    PRACTICE_BROWSE,
    PRACTICE_QA,
    CHAPTER_BROWSE,
    CONTEXT_CHALLENGE,
    STUDY_QA,
    CHAT,
    UNSUPPORTED,
    UNKNOWN
}
