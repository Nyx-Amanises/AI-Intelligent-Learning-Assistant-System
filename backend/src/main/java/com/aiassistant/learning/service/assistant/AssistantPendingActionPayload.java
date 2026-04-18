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
public class AssistantPendingActionPayload {

    private String promptText;

    private String materialQuery;

    private Long materialId;

    private String followUpActionType;

    private String chapterKeyword;

    @Builder.Default
    private List<AssistantMaterialCandidate> materialCandidates = new ArrayList<>();

    @Builder.Default
    private List<AssistantPlannedTask> tasks = new ArrayList<>();
}
