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
public class AssistantMaterialSearchResult {

    private String queryText;

    private Long selectedMaterialId;

    private String selectedMaterialTitle;

    private Boolean needsClarification;

    @Builder.Default
    private List<AssistantMaterialCandidate> candidates = new ArrayList<>();
}
