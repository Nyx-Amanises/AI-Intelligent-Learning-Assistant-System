package com.aiassistant.learning.vo.rag;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagEvalRunItemVO {

    private Long id;

    private Long sampleId;

    private Long materialId;

    private String queryText;

    private List<Long> expectedSegmentIds;

    private List<Integer> expectedPageNos;

    private List<Long> retrievedSegmentIds;

    private List<Integer> retrievedPageNos;

    private Integer hitRank;

    private Double reciprocalRank;

    private Double recallAt1;

    private Double recallAt3;

    private Double recallAt5;

    private Long latencyMs;

    private String errorMessage;
}
