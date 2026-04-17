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

    private Integer storedSegments;

    private String collectionName;

    private Boolean vectorStoreReady;

    private LocalDateTime createdAt;
}
