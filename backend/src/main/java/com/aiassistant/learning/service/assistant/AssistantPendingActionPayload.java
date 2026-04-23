package com.aiassistant.learning.service.assistant;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话中等待用户确认的动作参数。
 *
 * <p>例如同名资料需要用户选择、出题配置需要用户补充时，会把待办信息暂存在会话里。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantPendingActionPayload {

    /** 追问用户的提示文本。 */
    private String promptText;

    /** 资料查询关键词。 */
    private String materialQuery;

    /** 已确定的资料 ID。 */
    private Long materialId;

    /** 用户确认后要继续执行的动作类型。 */
    private String followUpActionType;

    /** 用户确认后要继续使用的消息。 */
    private String followUpUserMessage;

    /** 章节关键词。 */
    private String chapterKeyword;

    /** 候选资料列表。 */
    @Builder.Default
    private List<AssistantMaterialCandidate> materialCandidates = new ArrayList<>();

    /** 待提交的任务列表。 */
    @Builder.Default
    private List<AssistantPlannedTask> tasks = new ArrayList<>();
}
