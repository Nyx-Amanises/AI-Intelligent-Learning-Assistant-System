package com.aiassistant.learning.vo.rag;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagEvalSampleVO {

    private Long id;

    private Long datasetId;

    private Long materialId;

    private String materialTitle;

    private String queryText;

    private List<Long> expectedSegmentIds;

    private List<Integer> expectedPageNos;

    private String expectedKeywords;

    private String tag;

    private Integer difficulty;

    private String sourceType;

    private String note;

    private LocalDateTime createdAt;
}
