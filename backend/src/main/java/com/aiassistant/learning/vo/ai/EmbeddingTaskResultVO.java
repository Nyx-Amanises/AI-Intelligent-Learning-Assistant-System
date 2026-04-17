package com.aiassistant.learning.vo.ai;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmbeddingTaskResultVO {

    private Long materialId;

    private String materialTitle;

    private String modelName;

    private Integer totalSegments;

    private Integer queuedSegments;

    private Integer skippedSegments;

    private Boolean vectorStoreReady;

    private LocalDateTime createdAt;
}
