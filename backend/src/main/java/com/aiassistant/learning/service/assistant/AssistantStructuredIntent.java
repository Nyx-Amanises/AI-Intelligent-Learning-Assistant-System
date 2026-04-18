package com.aiassistant.learning.service.assistant;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantStructuredIntent {

    @Builder.Default
    private List<String> requestedTaskTypes = new ArrayList<>();

    private String materialQuery;

    private Boolean materialBrowse;

    private Boolean embeddingReadyOnly;

    private Boolean taskList;

    private String taskTypeFilter;

    private String taskStatusFilter;

    private Boolean questionSetList;

    private String questionSetKeyword;

    private String questionSetStatus;

    private Integer questionSetDifficultyLevel;

    private Boolean chapterBrowse;

    private String chapterKeyword;

    private Integer questionCount;

    private Integer singleCount;

    private Integer judgeCount;

    private Integer shortAnswerCount;

    private Integer difficultyLevel;

    private String exclusiveQuestionType;

    private Boolean defaultChoice;

    public static AssistantStructuredIntent empty() {
        return AssistantStructuredIntent.builder().build();
    }
}
