package com.aiassistant.learning.vo.rag;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagEvalDatasetVO {

    private Long id;

    private Long materialId;

    private String materialTitle;

    private String name;

    private String description;

    private String status;

    private Integer sampleCount;

    private Long lastRunId;

    private LocalDateTime lastRunAt;

    private LocalDateTime createdAt;
}
