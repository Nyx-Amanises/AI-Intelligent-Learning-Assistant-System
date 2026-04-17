package com.aiassistant.learning.dto.ai;

import lombok.Data;

@Data
public class SummaryTaskPayload {

    private Long materialId;

    private String modelName;

    private String summaryType;

    private Boolean saveAsNote;

    private Double temperature;
}
