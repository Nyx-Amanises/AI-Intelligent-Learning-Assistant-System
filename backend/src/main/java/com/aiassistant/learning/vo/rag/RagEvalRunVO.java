package com.aiassistant.learning.vo.rag;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagEvalRunVO {

    private Long id;

    private Long datasetId;

    private Long materialId;

    private String status;

    private Integer retrievalLimit;

    private Integer totalSamples;

    private Integer evaluatedSamples;

    private Integer failedSamples;

    private Double hitAt1;

    private Double hitAt3;

    private Double hitAt5;

    private Double recallAt1;

    private Double recallAt3;

    private Double recallAt5;

    private Double mrr;

    private Double avgLatencyMs;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private List<RagEvalRunItemVO> items;
}
