package com.aiassistant.learning.vo.rag;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CmrcImportResultVO {

    private Long materialId;

    private String materialTitle;

    private Long datasetId;

    private String datasetName;

    private Integer segmentCount;

    private Integer sampleCount;

    private Long embeddingTaskId;
}
