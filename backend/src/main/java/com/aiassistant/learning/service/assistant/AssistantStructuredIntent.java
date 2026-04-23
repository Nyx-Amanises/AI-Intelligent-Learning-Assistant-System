package com.aiassistant.learning.service.assistant;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户消息的结构化意图。
 *
 * <p>系统会先把自然语言解析成这个对象，再决定是否查资料、创建任务或继续普通聊天。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantStructuredIntent {

    /** 交互模式。 */
    private String interactionMode;

    /** 不支持能力的描述。 */
    private String unsupportedFeature;

    /** 用户请求创建的任务类型。 */
    @Builder.Default
    private List<String> requestedTaskTypes = new ArrayList<>();

    /** 用户提到的资料关键词或标题。 */
    private String materialQuery;

    /** 是否要浏览资料列表。 */
    private Boolean materialBrowse;

    /** 是否只看已经完成向量化的资料。 */
    private Boolean embeddingReadyOnly;

    /** 是否要查看任务列表。 */
    private Boolean taskList;

    /** 任务类型过滤条件。 */
    private String taskTypeFilter;

    /** 任务状态过滤条件。 */
    private String taskStatusFilter;

    /** 是否要查看题集列表。 */
    private Boolean questionSetList;

    /** 题集关键词。 */
    private String questionSetKeyword;

    /** 题集状态过滤条件。 */
    private String questionSetStatus;

    /** 题集难度过滤条件。 */
    private Integer questionSetDifficultyLevel;

    /** 是否要查看章节目录。 */
    private Boolean chapterBrowse;

    /** 章节关键词。 */
    private String chapterKeyword;

    /** 题目总数。 */
    private Integer questionCount;

    /** 单选题数量。 */
    private Integer singleCount;

    /** 判断题数量。 */
    private Integer judgeCount;

    /** 简答题数量。 */
    private Integer shortAnswerCount;

    /** 难度等级。 */
    private Integer difficultyLevel;

    /** 是否只生成某一种题型。 */
    private String exclusiveQuestionType;

    /** 用户是否表示使用默认配置。 */
    private Boolean defaultChoice;

    /** 是否是在回复出题配置追问。 */
    private Boolean questionConfigReply;

    /** 是否是在质疑/澄清当前上下文。 */
    private Boolean contextChallenge;

    /** 是否是在处理资料歧义。 */
    private Boolean materialDisambiguation;

    /**
     * 返回空意图，表示无法识别或无需结构化处理。
     */
    public static AssistantStructuredIntent empty() {
        return AssistantStructuredIntent.builder().build();
    }
}
