package com.aiassistant.learning.vo.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SummaryResultVO {

    private Long materialId;

    private Long recordId;

    private Long noteId;

    private String modelName;

    private String summaryType;

    private String summaryText;
}
