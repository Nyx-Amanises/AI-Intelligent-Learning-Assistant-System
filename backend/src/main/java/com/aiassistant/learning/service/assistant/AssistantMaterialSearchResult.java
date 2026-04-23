package com.aiassistant.learning.service.assistant;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料搜索结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantMaterialSearchResult {

    /** 查询文本。 */
    private String queryText;

    /** 自动选中的资料 ID。 */
    private Long selectedMaterialId;

    /** 自动选中的资料标题。 */
    private String selectedMaterialTitle;

    /** 是否需要用户进一步确认。 */
    private Boolean needsClarification;

    /** 候选资料。 */
    @Builder.Default
    private List<AssistantMaterialCandidate> candidates = new ArrayList<>();
}
