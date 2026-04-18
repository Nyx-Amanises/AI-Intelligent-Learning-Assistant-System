package com.aiassistant.learning.service.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantMaterialCandidate {

    private Long id;

    private String title;

    private String materialType;

    private String parseStatus;

    private Integer difficultyLevel;

    private Integer totalCharacters;

    private String tags;

    private Integer matchScore;
}
